# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Spring Boot 3.3.6 backend (Java 21, Gradle) for **Pointage Cleanic** — an employee time-tracking / attendance SaaS. Persistence is MongoDB. The app is multi-module (dashboard, planning, pointages, absences, agences/sites, employés, stock, collecte besoins, utilisateurs/auth, RH) and is consumed by a single client, the Angular frontend `pointage-agents` (kiosk check-in pages included). There is **no mobile app**.

Base package is `com.example.Pointage_Cleanic` (the hyphenated `Pointage-Cleanic` is not a legal Java package — see `HELP.md`).

## Build, run, test

Gradle wrapper is committed. Use it directly; do not rely on a locally installed `gradle`.

```bash
./gradlew clean build -x test        # compile + package, skip tests (CI first stage)
./gradlew test                        # run all tests (CI second stage)
./gradlew bootRun                     # run locally (needs a MongoDB REPLICA SET on localhost:27017, see below)
./gradlew test --tests "com.example.Pointage_Cleanic.controllers.EmployeCompletControllerTest"
./gradlew test --tests "*EmployeCompletControllerTest.get_all_ok"   # single test method
```

Tests are forced single-threaded (`maxParallelForks = 1`, JUnit parallel disabled) in `build.gradle` — do not re-enable parallel execution without checking why it was disabled (Testcontainers / shared Mongo state).

### Running with Docker

```bash
docker compose -f docker-compose.dev.yml up -d   # dev : Mongo 7.0 en replica set sur 127.0.0.1:27017, sans authentification
docker-compose up --build             # prod : mongo:7.0 replica set rs0 + auth (port NON publié), mongo-init, Spring Boot 8080
```

**Replica set obligatoire depuis le lot 1 / 1a** (transactions, décision D1) : un `mongod` standalone fait échouer le premier commit, ce que Mongock affiche sous la forme trompeuse « Transaction is already completed ». En dev : `docker-compose.dev.yml`, ou passer le service MongoDB local en replica set (`replication.replSetName: rs0` + `rs.initiate()`, voir `docs/refonte/1a-socle-technique.md` §2.4). En prod : `mongo:7.0` `--replSet rs0 --keyFile`, authentification (`MONGO_ROOT_*`), service `mongo-init` idempotent (`ops/mongo/initialiser-replica-set.js` : `rs.initiate` + compte applicatif `MONGO_APP_*` en `readWrite` sur `pointage_cleanic`). Passage depuis l'ancien `mongo:6` : procédure pour l'administrateur du serveur, à jouer au basculement final de la V3, dans `docs/exploitation/basculement-v3.md` (voir « Exploitation : pas d'accès au serveur »).

`docker-compose.yml` lit ses secrets dans un fichier `.env` non suivi (`env_file`, modèle `.env.example`). Les anciennes valeurs, longtemps commitées, sont **compromises** : procédure de rotation et de purge de l'historique dans `docs/securite/rotation-secrets.md`.

### Exploitation : pas d'accès au serveur

**Contrainte permanente** (conception du lot 1, Q16, corrigée le 24/11/2026) : l'équipe de développement **n'a pas accès au VPS** de production.
- Toute opération serveur (sauvegarde, détection des doublons, copie anonymisée, mise en service, fermeture du port Mongo) est livrée sous forme de **procédure écrite dans `docs/exploitation/`**, destinée à l'administrateur du serveur, avec la **liste des résultats attendus à renvoyer** (format : `docs/exploitation/README.md`).
- **Aucun critère d'acceptation ni aucune étape de développement ne dépend d'un accès au serveur.** Les migrations se testent sur des **jeux de données construits dans les tests** (Testcontainers) ; une copie anonymisée de la production n'est qu'un complément facultatif.
- Une sous-branche qui ajoute une migration, un index ou une opération serveur complète l'annexe de `docs/exploitation/basculement-v3.md` (procédure jouée une seule fois, au basculement final de la V3).
- Ne jamais écrire « jouer sur le serveur » comme une étape à faire soi-même : écrire la procédure et ce qu'il faut renvoyer.

### Profiles & configuration

- `application.yml` — default, points at `mongodb://localhost:27017/cleanic?directConnection=true` (replica set de dev), logs `com.example=DEBUG` (Spring Security et Spring Web en INFO). Secrets lus depuis l'environnement (`${JWT_SECRET}`, `${GOOGLE_MAPS_API_KEY}`, `${MAIL_USERNAME}`/`${MAIL_PASSWORD}`) ; en dev, un `.env` local est importé (`spring.config.import: optional:file:.env[.properties]`).
- `application-dev.yml` — SMTP via `MAIL_USERNAME` / `MAIL_PASSWORD` env vars (plus aucun secret en dur).
- `application-prod.yml` — Mongo `mongodb://${MONGO_APP_USERNAME}:${MONGO_APP_PASSWORD}@mongo:27017/pointage_cleanic?replicaSet=rs0&authSource=admin` (mots de passe en `[A-Za-z0-9]`, insérés tels quels dans l'URI), secrets sans valeur par défaut (`SecretsRequisVerificateur` refuse le démarrage s'il en manque un), Swagger désactivé, `server.forward-headers-strategy: native`.
- Profil **`demo`** : seul à créer les données de démonstration (sites fictifs Terrain, grille et formations RH d'exemple : ChangeUnits `9001`/`9002`). Jamais en prod.
- Profil **`e2e`** (`application-e2e.yml`) : essais de bout en bout Playwright du front, port 8089, base `cleanic_e2e`, comptes et stock d'essai (ChangeUnits `9101`/`9102`), refus de toute base dont le nom ne contient pas `e2e`. Jamais en prod. Voir « Règles de la refonte V3 » (section CI).
- `application-test.yml` — dummy JWT secret, disabled mail. ⚠ Ce profil n'est **pas** activé automatiquement : les valeurs factices des tests viennent de `src/test/resources/config/application.yml` (et des `properties` des `@SpringBootTest`).

Jackson is configured globally for `dd/MM/yyyy` dates in `Africa/Dakar`. Preserve that when adding DTOs — don't hardcode other formats.

## Architecture

Standard layered Spring MVC: `controllers → services → repositories (Spring Data Mongo) → entities (@Document)`. DTOs live in `Dto/`, MapStruct mappers in `Mapper/` (the `-Amapstruct.defaultComponentModel=spring` compiler arg wires them as Spring beans — don't add `@Mapper(componentModel="spring")` manually).

Things that only make sense after reading several files:

- **Auth is stateless JWT.** `security/SecurityConfig.java` disables CSRF, sets `SessionCreationPolicy.STATELESS`, and installs `JwtRequestFilter` before `UsernamePasswordAuthenticationFilter`. The filter skips `OPTIONS` (preflight), rejects expired/invalid tokens with 401, et répond **403 `COMPTE_DESACTIVE`** si le compte a été désactivé après l'émission du jeton (401 s'il a été supprimé). Sans jeton, l'`authenticationEntryPoint` global renvoie 401. **Depuis le lot 0 sécurité (2026-11), un 403 signifie « authentifié mais sans le droit »** (`@PreAuthorize` + `Droits`, voir la section Sécurité). Routes publiques : voir la section **Sécurité** ci-dessous — toute autre route exige un JWT. When adding a controller, decide explicitly whether to permit it here **et** posez son `@PreAuthorize`.

- **CORS allowlist is in `security/OriginesAutorisees.MOTIFS`**, liste unique partagée par le CORS HTTP (`SecurityConfig`) et le point d'entrée WebSocket `/ws`. Ce sont des motifs (`setAllowedOriginPatterns`) ; n'y ajoutez pas de joker large (`*`, `*.ngrok-free.dev`).

- **Two parallel user models coexist.** `User` (collection used by `LoginRepository` + `migrations/AmorcageSuperadmin` bootstrap superadmin `diarra.niang@cleanicsenegal.com`) and `Utilisateur` (richer admin entity with `RoleAdmin`, `ModulesAutorises`, activation flags). `MyUserDetailsService` bridges them for Spring Security. Don't collapse them without understanding which flows use which.

- **Module-based authorization.** `Utilisateur.modulesAutorises: ModulesAutorises` is a per-user feature-flag object (`dashboard`, `admin` + sous-objets `rh`, `productionChimie`, `terrain`, `stock` sous `entities/GestionModules/SousModules/`). **Depuis le lot 0 sécurité, ces drapeaux sont contrôlés côté serveur** par `@PreAuthorize("@droits.peut('module.drapeau')")` (composant `security/Droits`, droits **relus en base** à chaque appel, pas depuis le claim JWT) ; le front ne fait plus que du confort d'affichage (sidebar + `guards/module.guard.ts`). Le module **Stock v2** a 27 sous-flags : 7.3 `{catalogue, mouvements, etatStock, inventaires, synthese, approvisionnement, tableauBord}`, 7.4 `{categorisation, bonsEntree, bonsSortie, workflowValidation, historiqueDestinataire, plafonds, dotation, rapportsConso}`, 7.5 `{analyseMensuelle, chantiers, dons, comparatif, filtresCroises}`, 7.6 `{coutUnitaire, coutMouvements, valeurStock, coutSite, coutChantier, marges, tableauBordFinancier}` ; sérialisé tel quel dans le claim JWT `modules` (`@JsonInclude(NON_NULL)`). `rh.congesMesDemandes` / `rh.congesValidation` sont calculés **à la connexion seulement** (`CongeModuleEnricher`) et ne sont donc pas en base : les routes congés correspondantes restent filtrées par `CongeIdentiteService`, pas par `@droits`.

- **WebSockets (STOMP over SockJS) are a first-class channel**, not an afterthought. `WebSocketConfig` exposes `/ws`, broadcast prefixes `/topic` + `/queue`, client-to-server prefix `/app`. `/ws/**` est `permitAll` au niveau HTTP (poignée de main SockJS), mais **`JwtChannelInterceptor` exige un JWT valide au `CONNECT` STOMP** et pose le principal (nom = e-mail) : `convertAndSendToUser(email, …)` atteint donc son destinataire. Origines limitées à `OriginesAutorisees.MOTIFS`.

- **Scheduled tasks are enabled** (`@EnableScheduling` on the main class). Look for `@Scheduled` before assuming a job is triggered by a request.

- **Transactions MongoDB (lot 1 / 1a, D1).** `MongoTransactionManager` (`majority`) dans `MongoConfigurations`. Toute méthode de service qui écrit plus d'un document passe par **`configurations/ExecuteurTransactionnel`** : il rejoue le traitement entier sur `TransientTransactionError` (5 essais), puis renvoie **409 `CONFLIT_CONCURRENT`**. `@Transactional` reste permis là où il n'y a pas de concurrence (jamais sur un contrôleur). **Aucun appel externe dans une transaction** (SMTP, SMS, WebSocket) : ils partent des consommateurs de l'outbox (1b, ci-dessous) ; `apresCommit` a été supprimé. Verrou optimiste `@Version` sur `BonEntree`/`BonSortie`/`Inventaire`/`ProduitStock` → **409 `CONFLIT_VERSION`** ; violation d'index unique → **409 `DOUBLON`**. Les compensations manuelles disparaissent sous-branche par sous-branche (D1.6) : retirées des bons de stock en 1a et de la clôture d'inventaire en 1b, encore présentes dans `ProduitStockService` (bulk) et `OrdreFabricationService`.

- **Migrations versionnées par Mongock (lot 1 / 1a, D2).** Plus aucun `CommandLineRunner` de migration : `migrations/changeunits/C<ordre>…` (une ChangeUnit mince par migration) appelle un bean `migrations/*Migration.executer()`, testable directement. Registre `mongockChangeLog`, verrou `mongockLock`, exécution **avant le trafic** (`runner-type: initializingbean`), transactionnelles par défaut ; un échec refuse le démarrage. Une ChangeUnit appliquée ne repasse pas : **toute nouvelle migration = nouvelle ChangeUnit** avec un nouvel ordre. `0007` et `0008` sont `runAlways` (réparations à chaque démarrage héritées des anciens runners). L'amorçage du super-admin reste un `CommandLineRunner` (`AmorcageSuperadmin`, dépend d'une variable d'environnement). Rapports de migration : collection `migration_rapports`.

- **Index (lot 1 / 1a, D3).** `spring.data.mongodb.auto-index-creation` reste **désactivé**, et **plus aucune annotation `@Indexed`/`@CompoundIndex`** n'existe sur les entités : **la seule liste est `migrations/index/IndexesAttendus`** (93 index, dont 25 uniques). Elle est créée par les ChangeUnits `0101` à `0105` (1a) et `0107` à `0109` (1b) via `CreateurIndex` : doublons ou conflit de définition ⇒ rapport dans `migration_rapports` et démarrage refusé, sans dédoublonnage ; uniques facultatifs en filtre partiel `$type: "string"`. `IndexesAttendusIT` échoue s'il manque un index ou s'il en existe un non déclaré. Ajouter un index : une ligne dans `IndexesAttendus` **et** une nouvelle ChangeUnit ; l'ajouter aussi à `ops/mongo/detecter-doublons.js` s'il est unique (test d'alignement).

- **Pièces (lot 1 / 1b, D4) — `docs/refonte/1b-pieces.md`.** Bloc commun `entities/pieces/PieceBase` incorporé sous `piece` (statut et `@Version` restent à la racine) + interface `Piece`. Numéro **attribué à la validation** par `services/pieces/NumerotationService` (`compteurs_pieces`, `BE-2026-0001`, dans la transaction : ni trou ni doublon), nul en brouillon (`libelleNumero` = `BROUILLON-<6 derniers caractères>`) ; une référence existante est conservée. **Plus de suppression** : un brouillon s'**abandonne** (`POST /{id}/abandonner`), une pièce validée s'**annule** (`POST /{id}/annuler {motif ≥ 10}` ; mouvements inverses et soldes **dans la transaction d'annulation**, comme à la validation, 422 si une entrée a été consommée ; les autres effets par événement). Portés : bons d'entrée/sortie, inventaires (`SuppressionDefinitiveService` supprimé). Une transition = `TraceurPieces.tracer(...)` : ligne d'audit + événement, dans la transaction. Registre `registre_pieces` (consommateur seulement, aucune route).
- **Outbox (lot 1 / 1b, D5).** `EvenementPublisher.publier` (refusé hors transaction) écrit dans `evenements_domaine` ; `DispatcherEvenements` (toutes les 2 s, `outbox.dispatcher.actif`, **désactivé dans les tests** qui appellent `traiterLot()`) réclame sous bail, appelle les beans `ConsommateurEvenement` ; idempotence `evenements_consommes` ; essais 1 min → 1 h, `EN_ERREUR` après 10. Supervision super-admin `/api/exploitation/evenements`. **Règle 3** : un module n'appelle pas le service d'écriture d'un autre, il publie un événement (test ArchUnit).
- **Journal d'audit (lot 1 / 1b, D6).** `services/audit/JournalAudit.enregistrer(...)` dans la transaction métier (`journal_audit`) : différentiel, auteur, IP, `X-Correlation-Id` (`CorrelationIdFilter`) ; champs `security/Sensible` masqués `***`.
- **Garde-fous suppressions (lot 1 / 1b).** `security/RoutesSuppressionIT` (aucune route DELETE hors liste blanche) et `architecture/SuppressionsInterditesArchTest` (aucun `delete*`/`remove*` sur repository ou `MongoTemplate` hors liste blanche). Listes « en sursis » nommées avec leur sous-branche : **toute nouvelle route DELETE ou suppression fait échouer les tests** ; quand une sous-branche retire une entrée, l'enlever de la liste.
- **MongoDB custom repository pattern** is used for `ProduitRepository` (`ProduitRepositoryCustom` + `ProduitRepositoryImpl`). Follow that split — don't inline complex aggregation into the `MongoRepository` interface.

- **Reports & imports:** Apache POI (`poi-ooxml`) for Excel, OpenPDF for PDF, plus `ImportEmployeRequest/Response/Error` DTOs drive Excel-based employee import. `GeocodingService` + `WebClientConfig` hit Google Maps using the key from `google.maps.api.key`.

### Controller URL map (useful when reading SecurityConfig / frontend calls)

| Prefix | Controller |
| --- | --- |
| `/api/login`, `/auth` | LoginController, PasswordResetController |
| `/api/superadmin` | UtilisateurController |
| `/api/employe-complet` | EmployeCompletController (legacy, `rh.dossierEmploye`) |
| `/api/pointages` | PointagesController (`POST` public et limité en débit ; vues superviseur protégées) |
| `/ws` | STOMP endpoint |
| `/api/exploitation/evenements` | EvenementsExploitationController (supervision de l'outbox, super-admin ; `/{id}/relancer`) |
| **— RH 6.1 — gestion-personnel** (`controllers/rh/gestionpersonnel`) | |
| `/api/gestion-personnel/employes` | DossierEmployeController (source de vérité RH depuis 2026-04) |
| `/api/gestion-personnel/contrats` | ContratController (multipart, fichier PDF inline) |
| `/api/gestion-personnel/documents` | DocumentEmployeController (pièces administratives génériques + workflow validation) |
| `/api/gestion-personnel/organigramme` | OrganigrammeController (sur DossierEmploye, hiérarchie par superieurHierarchiqueId) |
| **— RH 6.2 — temps-presences** (`controllers/rh/tempspresences`, surface unique façade depuis 2026-06) | |
| `/api/temps-presences/pointages` | TempsPresencesPointageController |
| `/api/temps-presences/absences` | TempsPresencesAbsenceController (+ `/employe/{id}`, `/{id}/justificatif`) |
| `/api/temps-presences/conges` | TempsPresencesCongeController (+ `/demandes/employe/{id}`) |
| `/api/temps-presences/heures-supplementaires` | TempsPresencesHeureSupController (+ `/employe/{id}`) |
| `/api/temps-presences/recapitulatif` | TempsPresencesRecapController (+ `/export/excel`, `/export/pdf`) |
| **— RH 6.3 — paie** (`controllers/rh/paie`) | |
| `/api/paie/grille-salariale` | GrilleSalarialeController |
| `/api/paie/bulletins` | BulletinPaieController |
| `/api/paie/declarations-sociales` | DeclarationSocialeController |
| `/api/paie/parametres` | ParametresPaieController |
| **— RH 6.4 — developpement-rh** (`controllers/rh/developpementrh`) | |
| `/api/developpement-rh/formations` | FormationController |
| `/api/developpement-rh/evaluations` | EvaluationPeriodiqueController |
| `/api/developpement-rh/sanctions` | SanctionController |
| `/api/developpement-rh/besoins-formation` | BesoinFormationController |
| **— RH transverse** (`controllers/rh`, racine) | |
| `/api/tableau-bord-rh` | TableauBordRhController (dashboard transverse, agrège les 4 sous-modules) |
| **— Production Chimie 5.1 —** | |
| `/api/production-chimie/stock-chimie/matieres-premieres` | MatieresPremieresController (multipart fiche sécurité) |
| `/api/production-chimie/stock-chimie/mouvements` | MouvementsStockChimieController (réception, ajustement) |
| `/api/production-chimie/formulations` | FormulationsController (+ versions/comparer, versions/{n}/restaurer) |
| `/api/production-chimie/ordres` | OrdresFabricationController (+ /lancer, /terminer, /annuler, /disponibilite-mp, /kanban) |
| `/api/production-chimie/lots` | LotsController (+ /{id}/tracabilite) |
| `/api/production-chimie/controle-qualite/grilles` | GrillesControleController (+ /pour-lot/{lotId}) |
| `/api/production-chimie/controle-qualite/controles` | ControlesQualiteController (multipart photos, + /tendances) |
| `/api/production-chimie/formats-conditionnement` | FormatsConditionnementController |
| `/api/production-chimie/tableau-bord` | TableauBordProductionController (+ /rapport agrégé, /comparaison-periodes) |
| **— Stock v2 7.3 — stocks & approvisionnement** (`controllers/stockv2`, collections `stockv2_*`) | |
| `/api/stock/produits` | ProduitStockController (multipart photo + fiche technique, `/actifs`, `/{id}/photo`, `/{id}/fiche-technique`, `/bulk` transactionnel) |
| `/api/stock/categories` | CategorieStockController (arborescence : `/racines`, `/enfants`, plat ; DELETE 409 si non vide) |
| `/api/stock/mouvements` | MouvementStockController (ENTREE/SORTIE/TRANSFERT, impact sur `StockParSite`, 422 si insuffisant) |
| `/api/stock/etat-stock` | EtatStockController (consolidé / `parSite`, statut + valeur ; PUT `/seuils`) |
| `/api/stock/inventaires` | InventaireController (workflow `/comptage`, `/validation`, `/cloture`) |
| `/api/stock/synthese-mensuelle` | SyntheseMensuelleController |
| `/api/stock/approvisionnement` | ApprovisionnementController (`/suggestions`) |
| `/api/stock/tableau-bord` | TableauBordStockController |
| **— Stock v2 7.4 — contrôle des mouvements** (`controllers/stockv2`, collections `stockv2_bons_*`, `stockv2_plafonds`) | |
| `/api/stock/bons-entree` | BonEntreeController (bon multi-lignes à workflow ; `/{id}/soumettre`, `/valider`, `/refuser`, `/abandonner`, `/annuler` ; plus de DELETE depuis 1b) |
| `/api/stock/bons-sortie` | BonSortieController (idem ; valider → mouvements SORTIE, 422 si stock insuffisant) |
| `/api/stock/workflow/bons` | WorkflowStockController (Kanban unifié entrées+sorties, non paginé) |
| `/api/stock/categorisation/stats` | CategorisationStockController |
| `/api/stock/plafonds` | PlafondController (CRUD + `/consommation`) |
| `/api/stock/dotation/comparatif` | DotationController |
| `/api/stock/consommation` | ConsommationController (`/par-destinataire`, `/rapport`) |
| **— Stock v2 7.5 — analyse des consommations** (`controllers/stockv2`, collection `stockv2_chantiers` ; le reste agrégé à la volée) | |
| `/api/stock/chantiers` | ChantierController (CRUD + `/actifs`, `/{id}` DetailChantier agrégé, `/{id}/cloture` ; 409 si clôturé/réf dupliquée) |
| `/api/stock/analyse` | AnalyseStockController (`/mensuel`, `/dons`, `/comparatif`, `/croise` — **lecture seule**, agrégation des sorties EFFECTIVES) |
| **— Stock v2 7.6 — valorisation financière** (`controllers/stockv2`, collections `stockv2_parametrage_valorisation`, `stockv2_historique_cout`) | |
| `/api/stock/valorisation` | ValorisationController (`/parametrage` GET+PUT, `/couts-produits`(+`/{id}/historique`), `/mouvements`, `/valeur-stock`, `/cout-site`, `/chantiers`(+`/{id}`), `/marges`, `/tableau-bord` — lecture seule sauf PUT parametrage) |
| `/api/stock/produits/{id}/valorisation`, `/prix-vente` | ProduitStockController (2 **PATCH** : méthode de valo + prix de vente, hors formulaire multipart 7.3) |

## Testing conventions

- Controller tests use `@WebMvcTest(XxxController.class)` with `@AutoConfigureMockMvc(addFilters = false)` — security is disabled in slice tests, so `JwtRequestFilter`, `JwtUtil`, and `MyUserDetailsService` must still be declared as `@MockBean` (the Spring context loads them). Copy this pattern from `EmployeCompletControllerTest`. ⚠ Un slice ne teste **pas** les droits : tout `@PreAuthorize` se teste avec la chaîne de sécurité réelle en étendant `security/SecuriteITBase` (vrais JWT, comptes en base, helper `verifierAcces` : 401 / 403 / accès).
- Integration tests that need a real Mongo extend `MongoTestContainer` (Testcontainers `mongo:7.0`, container reused across tests). `configurations/AbstractMongoTest.java` is the main-source counterpart.
- Test profile (`application-test.yml`) has mail disabled (`spring.mail.host: disabled`) — don't wire `JavaMailSender` into tests that load the full context without mocking it.

## CI

`.github/workflows/backend.yml` runs on push to `main` only: JDK 21 setup → `./gradlew clean build -x test` → `./gradlew test`. Docker push and VPS deploy steps are commented out (étape checkout réparée en `refonte/v3-outillage`, rien d'autre de changé).

**`.github/workflows/ci-refonte.yml`** (sous-branche outillage) : sur push et PR vers `refonte/v3` et `refonte/v3-**`, deux jobs, `./gradlew test` (Testcontainers `mongo:7.0` en replica set, stratégie Docker Unix forcée par `TESTCONTAINERS_DOCKER_CLIENT_STRATEGY`, car `src/test/resources/testcontainers.properties` vise le npipe Windows du poste) et **gitleaks** sur tout l'historique. Même workflow côté front (`ng build`, `npm run test:ci`, lint, gitleaks). Les essais e2e restent locaux. Compte rendu : `docs/refonte/outillage.md`.

### Règles de la refonte V3 (outillage, 2026-11)

- **Toute sous-branche qui modifie un écran ajoute ou met à jour ses parcours e2e ; `npm run e2e` est vert avant de déclarer une sous-branche terminée.** Les parcours (Playwright) vivent dans le dépôt front (`e2e/`, mode d'emploi `docs/tests/e2e.md` du front) ; ils démarrent ce backend en **profil `e2e`** (`application-e2e.yml` : port 8089, base `cleanic_e2e` remise à zéro à chaque lancement ; jeu de départ par les ChangeUnits `9101`/`9102` `@Profile("e2e")`, garde-fou `E2eGardeFou` : base dont le nom contient `e2e` seulement ; mots de passe `E2E_SUPERADMIN_PASSWORD` / `E2E_STOCK_PASSWORD`). Une donnée de départ manque à un parcours : l'ajouter dans `E2eStockSeed` (la ChangeUnit repasse, la base étant neuve à chaque lancement).
- **`npm run lint` sans erreur sur les fichiers modifiés** (front : `npm run lint:modifies`, strict, `--max-warnings 0`).
- **gitleaks actif : aucun secret dans un commit.** `.gitleaks.toml` (règles par défaut + valeurs factices des tests), hook `.githooks/pre-commit` activé par `git config core.hooksPath .githooks` (une fois par clone ; `winget install Gitleaks.Gitleaks`), même analyse en CI. Les fuites anciennes de l'historique, déjà remplacées, sont neutralisées par empreinte dans `.gitleaksignore` (sans la valeur) : n'y ajoutez jamais une fuite nouvelle, retirez le secret et changez-le.

## Sécurité

Lot 0 sécurité (2026-11, branche `fix/securite-lot-0`) — détails et preuves : `docs/securite/correctifs-lot-0.md`.

- **Routes publiques autorisées** (liste blanche vérifiée par `security/RoutesPubliquesIT`, qui échoue si une autre route répond sans jeton) : `POST /api/login`, `POST /auth/forgot-password`, `POST /auth/reset-password`, `POST /api/pointages` (kiosque, limité par `PointageLimiteurDebit`), `/ws/**` (poignée de main ; JWT exigé au `CONNECT` STOMP), `/error`, Swagger hors prod. Aucune autre : les images publiques (`/api/produits/image/**`, `/api/employe-complet/image/**`) et `POST /api/terrain/pointages` ont été **supprimés** au lot 0 (`RoutesPubliquesIT` vérifie qu'ils ne reviennent pas). Toute nouvelle route publique : `SecurityConfig` **et** la liste blanche du test.
- **Limite de débit du kiosque** (`services/pointage/PointageLimiteurDebit`, en mémoire, par instance) : seuils réglables `pointage.limite.par-ip-par-minute` (60), `par-appareil-par-minute` (10), `codes-inconnus-avant-blocage` (5), `duree-blocage-minutes` (15) dans `application.yml` ; 429 au-delà. Seuil IP volontairement large (beaucoup d'agents derrière une IP). L'IP vient de `X-Forwarded-For` seulement depuis un mandataire interne (`server.forward-headers-strategy: native` en prod).
- **Journaux** : `application-prod.yml` remet `MongoTemplate` et `com.example` en INFO (le DEBUG de `application.yml` ne vaut qu'en dev) ; vérifié par `ProfilProdSecuriteIT`.
- **Droits par module** : chaque contrôleur porte un `@PreAuthorize`. `@droits.peut('stock.catalogue')`, `@droits.peutUnParmi('rh.calculBulletin', 'rh.historiquePaies')`, `@droits.estSuperAdmin()` ; joker `module.*` = au moins un drapeau du module ; expressions partagées dans `security/DroitsExpr` (`REFERENTIEL` pour les listes lues par plusieurs modules, `POINTAGES_SUPERVISION`). SUPERADMIN passe partout, le rôle RH passe sur tout `rh.*` (comme la sidebar), un compte désactivé est toujours refusé. Les droits sont **relus en base** (pas depuis le JWT). Les contrôles de rôle des services (`HabilitationStock`, `CongeIdentiteService`, `ParametresPointageService`…) restent en place : ne pas les retirer. Refus → 403 JSON `ACCES_REFUSE` (`GlobalExceptionHandler` et `TerrainExceptionHandler` — tout nouvel advice prioritaire doit aussi traduire `AccessDeniedException`). ⚠ Les erreurs de lecture de la requête (paramètre obligatoire manquant, `@Valid`) sont levées **avant** le contrôle de droit : 400 plutôt que 403, sans fuite de données.
- **Données sensibles** : `User.password` / `Utilisateur.password` sont en `WRITE_ONLY` (jamais renvoyés) ; sans `rh.dossierEmploye`, les dossiers employés sont renvoyés en `DossierEmployeResumeDto` (ni paie, ni données personnelles).
- **Aucun secret dans le code, les tests, les journaux** : valeurs uniquement par variables d'environnement (`.env` non suivi, modèle `.env.example`) ; `security/AucunSecretLitteralTest` échoue si une propriété sensible des `application*.yml` (hors test) ou de `docker-compose.yml` n'est pas une référence `${…}`. Jamais de `System.out.println` ni de journal contenant un corps de requête, un mot de passe, un jeton ou un code.
- **Mot de passe oublié** : code `SecureRandom` haché, validé sur e-mail + code, 5 essais, réponse identique que le compte existe ou non (`ResetPasswordService`).

## Conventions transverses

- Montants en FCFA stockés en long (pas de décimales).
- Dates stockées en ISO 8601, converties en dd/MM/yyyy côté frontend (Jackson configuré globalement, voir Project overview).
- Réponses API en JSON, pagination avec `page` et `size` pour les listes. Modules récents (Production Chimie) utilisent le record `util/PageResponse<T>` ; les modules pré-existants utilisent `Page<T>` Spring brut ou des records ad hoc.
- **Gestion des erreurs de validation métier** : lever `IllegalArgumentException` avec un message explicite ; `GlobalExceptionHandler` renvoie automatiquement `400 VALIDATION_ERROR`. `ResourceNotFoundException` → 404 NOT_FOUND. `EmployeAlreadyExistsException` → 409 CONFLICT. **Bean Validation** (`@Valid`, `@NotBlank`, etc.) : `MethodArgumentNotValidException` → 400 `VALIDATION_ERROR` avec body `{error, message, errors: [{field, message}]}`. Codes d'erreur HTTP spécifiques à un module : voir le sous-fichier correspondant dans `.claude/docs/`.
- **Multipart** : config globale `spring.servlet.multipart.max-file-size: 10MB`, `max-request-size: 15MB` (PDF de contrat peuvent dépasser le défaut 1MB Spring).

## Module RH — ✅ Backend terminé

4 sous-modules livrés : **6.1 Personnel**, **6.2 Temps & Présences**, **6.3 Paie**, **6.4 Développement RH**. Frontend Angular consomme les API REST directement (JWT, fallback `.anyRequest().authenticated()`).

- **Organisation du code** : depuis 2026-06, tout le code RH est rangé sous des sous-packages `rh` (comme `terrain` / `productionchimie`) : `controllers/rh`, `services/rh`, `repositories/rh`, `entities/rh`, `Dto/rh`, `Mapper/rh`, `Enum/rh`. Les classes **partagées** restent à la racine de leur couche car consommées hors RH : entités `EmployeComplet`, `Employe`, `Pointage`, `Absent` ; services `EmployeCompletService`, `AbsentService` ; mappers `DateMapper`, `EmployeCompletMapper`, `EmployeMapper` ; enum `DecisionControle` (production chimie).
- **Source de vérité** : `DossierEmploye` (collection `dossiers_employes`) depuis 2026-04 — tous les services RH valident et dénormalisent depuis `DossierEmployeRepository`.
- **Migration EmployeComplet → DossierEmploye (2026-06-11)** : `PointageCentraliseService`, `RecapitulatifMensuelService`, `CalculPaieService` et `OrganigrammeService` ont été migrés sur `DossierEmploye` (la jointure pointage = `Pointage.codeSecret == DossierEmploye.agentId` ; les champs paie `categorieCode`/`numeroIpres`/`numeroCss`/`rib`/`banque` existent sur `DossierEmploye` ; l'organigramme résout la hiérarchie par `superieurHierarchiqueId`). Le **retard n'est plus dérivé** (pas de `heureDebut` sur `DossierEmploye`, horaires hétérogènes) : `retardMinutes`/`nombreRetards` restent au contrat mais valent 0. Le contrôleur legacy `RhEmployeController` (`/api/employes`, CRUD sur EmployeComplet, doublon de `DossierEmployeController`) a été **supprimé**. **Plus aucun service RH (`services/rh`) ne dépend de `EmployeComplet`.** Reste l'entité partagée `EmployeComplet` et son module propre (entité/service/controller/import, consommé hors RH — ex. stock).
- **Rattachement grille manuel (2026-06-12)** : le rattachement employé → grille salariale n'est plus automatique. Le code de grille (`categorieCode`) est passé dans `CalculBulletinRequest` (champ ajouté) ; `BulletinPaieService.calculerEtSauvegarder(employeId, categorieCode, mois, annee)` → `CalculPaieService.orchestrer(employeId, categorieCode, mois, annee)` charge la grille par ce code (validation `IllegalArgumentException` si vide → 400). Les champs perso du bulletin (`numeroIpres`/`numeroCss`/`rib`/`banque`) sont désormais portés par la **grille** `CategorieProfessionnelle` (4 champs ajoutés à l'entité + DTO) et lus depuis elle. `DossierEmploye.categorieCode` n'est plus utilisé par la paie.
- **Suppression EmployeComplet — feuille de route** : aucun service `services/rh` ne dépend plus d'`EmployeComplet`. Bloqueurs restants avant suppression du module (l'image publique a été supprimée au lot 0) (`entities/EmployeComplet`, `EmployeCompletRepository/Service/Controller` `/api/employe-complet`, `EmployeCompletMapper`, DTOs `EmployeCompletDto`/`ImportEmploye*`, + 3 tests) : (1) sync `Employe` legacy maintenue par `EmployeCompletService` via `EmployeMapper` et servie par `EmployesController` `/api/employe` ; (2) import Excel `/api/employe-complet/import-excel` → à porter sur le flux `DossierEmploye`. `Dto/MouvementStock` (module stock) ne référence plus `EmployeComplet` (import mort retiré).
- **Tableau de bord RH** : `TableauBordRhService` agrège en parallèle via `CompletableFuture.supplyAsync` (pas de `$lookup` — pas de clé de jointure commune entre collections).

Détails endpoints, workflows (PeriodeEssai, DocumentEmploye, BulletinPaie, EvaluationPeriodique, Sanction), validations métier, formules de calcul de paie : **voir `.claude/docs/module-rh.md`**.

## Module Production Chimie (5.1) — ✅ Backend terminé

Module **autonome** (collections préfixées `production_chimie_`), ne partage aucune collection avec le module Stock historique. 9 contrôleurs REST sous `/api/production-chimie/`.

Patterns de référence introduits par ce module, **réutilisables ailleurs** :

- **Génération atomique de séquences** : `MongoTemplate.findAndModify(... $inc compteur ... upsert=true, returnNew=true)` avec un document `{_id: "AAAAMMJJ", compteur: long}` par jour. Test concurrent `CompteurLotServiceIT` (100 threads → 100 numéros distincts). Réutiliser ce pattern pour toute nouvelle séquence.
- **Compensation manuelle des opérations multi-document** (historique : MongoDB était en standalone). Le service applique manuellement les inverses sur échec à mi-parcours (voir `OrdreFabricationService.lancer` / `annuler`). ⚠️ Depuis le lot 1 / 1a, le replica set permet les transactions : **ne plus écrire de nouvelle compensation**, passer par `ExecuteurTransactionnel`. Celles de Production Chimie seront retirées dans leur sous-branche (D1.6). Vérifier d'abord la faisabilité (ex : `quantiteEnStock ≥ quantiteTheorique` avant toute écriture) pour éviter le cas commun.
- **Versioning par snapshot** : `FormulationService` snapshote la version courante dans `versions[]` avant chaque PUT, incrémente `versionCourante`. La version courante n'apparaît jamais dans `versions[]` (elle y entre au prochain PUT).
- **Stockage binaire inline** : pas de GridFS ni S3 — `byte[]` en `@JsonIgnore` dans le document Mongo, URL calculée par le mapper vers un endpoint de streaming dédié.

**Automatisations Formulation (MA / eau qsp / contrôle du total) — branche `feature/formulation-automatisations` :**
- **Valeurs dérivées, jamais persistées** : `FormulationCalculService` (pur, `BigDecimal`, testable isolément — `FormulationCalculServiceTest`) calcule matière active, eau de complément « qsp » et écart de total. `FormulationService` attache un `SyntheseFormulation` au DTO de lecture (`getById`/`list`/`create`/`update`/`restaurerVersion`) ; **rien n'est ajouté à l'entité `FicheFormulation`**. Pas d'arrondi intermédiaire (sommes exactes ; seules les divisions % sont bornées à 6 déc. ; l'affichage arrondit côté client).
- **Nouveaux champs entité** : `MatierePremiere.matiereActivePct` (Double 0–100, nullable) + `compterDansMa` (boolean) ; `IngredientFormulation.ingredientComplement` + `qs` (booleans), `dosage` devient nullable. **Migration Mongo non destructive** : champ absent → `false`/`null` (Spring Data). La quantité d'une ligne complément est remise à `null` avant save (`normaliserLignesComplement`) — jamais stockée.
- **Règle qsp** : au plus 1 ligne `ingredientComplement` par formule sinon `FormulationInvalideException` → **422** (`@ResponseStatus`). Le contrôle du total est **informatif** (jamais bloquant).
- **Paramétrage tolérance** : nouveau singleton `ParametresProductionChimie` (`GET/PUT /api/production-chimie/parametres`) — pattern get-or-create paresseux calqué sur `ParametresEscalade`/`ParametresPaie`, défaut `toleranceTotalPct = 0.1`. Lu par `FormulationService.toleranceCourante()`.
- ⚠️ **CDC Détergent V5 : l'eau annoncée (813 kg) est une erreur** — Σ(8 ingrédients non-eau) = 181 kg → eau exacte = **819 kg** ; les tests retiennent 819 (MA 111,16 kg / %MA 11,116 corrects).

Détails collections + indexes, services, transitions OF strictes, intégrité référentielle DELETE, dette technique (RBAC fin, réconciliation stock), codes d'erreur HTTP spécifiques : **voir `.claude/docs/module-production-chimie.md`**.

## Module Terrain (5.2) — ✅ Backend terminé

Module **autonome** Exploitation Terrain (gestion agents de propreté / entretien phytosanitaire sur sites clients). 9 contrôleurs REST sous `/api/terrain/`. Collections dédiées (`sites_clients`, `terrain_*`, `produits_phyto`, `applications_phyto`, `compteurs_terrain`). Code sous les sous-packages `terrain` : `controllers/terrain`, `services/terrain`, `repositories/terrain`, `entities/terrain`, `Dto/terrain`, `Mapper/terrain`, `Enum/terrain`.

Spécificités vs Production Chimie :
- **Format d'erreur dédié** : `TerrainExceptionHandler` (advice scopé `controllers.terrain`, `@Order(HIGHEST_PRECEDENCE)`) renvoie `{ message, timestamp, status }` — distinct du `GlobalExceptionHandler` global (`{ error, message }`) qui reste inchangé pour les autres modules.
- **Dates ISO 8601** : DTOs en `LocalDate`/`LocalDateTime` (sérialisation ISO par défaut, pas de `@JsonFormat`). Le `dd/MM/yyyy` global ne concerne que `java.util.Date`.
- **Dépendance RH lecture seule** : `ReferentielRhService` valide `DossierEmploye.departement == "Exploitation"` et dénormalise nom/matricule. Aucune écriture dans `dossiers_employes`.
- **Temps réel + job** : WebSocket `/topic/alertes-terrain`, `/topic/pointages-terrain`, `/user/queue/notifications-terrain` ; `DetectionAlertesJob` (`@Scheduled`) pour retards/absences/départs prématurés + escalade paramétrable (`ParametresEscalade` singleton).
- **Anti-spoof pointage** : `HaversineService` recalcule la distance serveur, statut `SUR_SITE`/`HORS_ZONE`/`GPS_IMPRECIS`.
- **2 PDF serveur** (OpenPDF) : `/interventions/{id}/pdf`, `/phytosanitaire/registre/pdf`. Le reste des exports est côté frontend.
- Compteurs quotidiens `INT-` / `PHYTO-` (même pattern `findAndModify` que `CompteurLotService`). Stockage fichiers `byte[]` inline (pas de GridFS).
- **Annulation motivée d'affectation** (2026-07-21) : `POST /api/terrain/planning/affectations/{id}/annuler` body `{motif}` (obligatoire, ≥ 5 car. après `trim` → 400 sinon) conserve la ligne en historique avec `statut=ANNULEE` + 3 champs de traçabilité (`motifAnnulation`, `dateAnnulation`, `annuleParNom` **déduit du JWT**, ignorés en écriture par le mapper). Garde de statut : `PLANIFIEE`/`EN_COURS` seulement, sinon **409** (donc rejouer = 409, idempotence métier). `GET /affectations/stats?dateDebut&dateFin` renvoie les compteurs des 5 statuts en un appel. ⚠️ **`DELETE /affectations/{id}` a été supprimé** : une affectation ne se supprime pas, elle s'annule (le hard delete contournait la traçabilité).
- **Transitions automatiques de statut** (2026-07-21) : `AffectationStatutScheduler` (cron 1 min, zone `Africa/Dakar`, + rattrapage au boot via `ApplicationReadyEvent`) fait avancer `statut` selon le créneau, en **deux `updateMulti` ordonnés** — `PLANIFIEE + dateDebut<=now → EN_COURS`, puis `EN_COURS + dateFin<=now → EFFECTUEE`. L'ordre fait qu'un créneau entièrement passé atteint `EFFECTUEE` en **une seule passe**. La clause `statut = source` porte à elle seule l'idempotence et la protection des états terminaux (`ANNULEE`/`REMPLACEE`/`EFFECTUEE` jamais matchés) → sûr en multi-instance, **pas de ShedLock**. `TimeConfig.clock()` est ancré sur `Africa/Dakar` (le `zone` de `@Scheduled` ne pilote que le déclenchement, pas `now`). ⚠️ Course **voulue** avec `/annuler` : une bascule en `EFFECTUEE` entre l'affichage et le clic donne un **409** — ne pas assouplir `STATUTS_ANNULABLES`. `EFFECTUEE` = « créneau écoulé » (pure horloge, sans lien avec le pointage GPS) ; condition isolée dans `conditionPassageEffectuee`, seul point à changer si le métier la redéfinit.
- **Indexes** : `terrain_affectations` (`statut_debut_fin`, `employe_id`, `site_statut`), requêtée chaque minute par `AffectationStatutScheduler`. Depuis le lot 1 / 1a, tous les index sont déclarés dans `migrations/index/IndexesAttendus` et créés par ChangeUnit (voir Architecture) ; l'ancienne `TerrainIndexesConfig` est supprimée.
- **TODO** : sous-permissions `ModulesAutorises.terrain.*` à ajouter dans `entities/GestionModules/SousModules/` (gating frontend) ; tests d'intégration.

Détails endpoints, payloads WebSocket, hypothèses (seuils escalade/maintenance, identité utilisateur courant), codes d'erreur : **voir `.claude/docs/module-terrain.md`**.

## Module Stock v2 (7.3 + 7.4 + 7.5 + 7.6) — ✅ Backend terminé

Sous-module **« Stocks & Approvisionnement » (7.3)** + **« Contrôle des mouvements » (7.4)** + **« Analyse des consommations » (7.5)** + **« Valorisation financière » (7.6)**, **autonome** (collections préfixées `stockv2_`, ne partage rien avec le stock historique `produits` ni stock-chimie `production_chimie_*`). 19 contrôleurs REST sous `/api/stock/`. Code sous les sous-packages `stockv2` : `controllers/stockv2`, `services/stockv2`, `repositories/stockv2`, `entities/stockv2`, `Dto/stockv2`, `Mapper/stockv2`, `Enum/stockv2`. **Frontend Angular figé** → contrats (chemins, champs JSON, enums, codes HTTP) respectés à la lettre. Créé sur la branche `feature/stock` (7.3 : 2026-06-17 ; 7.4 + 7.5 : 2026-06-18 ; 7.6 : 2026-06-19).

Spécificités / patterns :
- **`StockParSite` = source de vérité des quantités** (collection `stockv2_etats_stock`, index composé unique `(produitId, siteId)`). `ProduitStock.quantiteTotale` n'est **pas** stocké : dénormalisé en lecture (somme des soldes). Logique de solde mutualisée dans `StockBalanceService`. Bucket `siteId=null` pour le stock initial d'import (sans site).
- **Sites en lecture seule** depuis le module Terrain : `ReferentielSiteService` lit `sites_clients` (via `SiteClientRepository`) pour valider `siteId` et dénormaliser `siteNom`. Aucun référentiel de sites côté stock.
- **Utilisateur créateur** déduit du JWT via `CurrentUserProvider` (réutilisé depuis `services/terrain`), jamais envoyé par le client.
- **Dates ISO** : `LocalDate` avec `@JsonFormat("yyyy-MM-dd")`, `LocalDateTime` ISO, `mois` en `yyyy-MM` (pas le `dd/MM/yyyy` global).
- **Erreurs** : `GlobalExceptionHandler` global. Nouvelles exceptions — `StockOperationException` → **422** (stock insuffisant, code `STOCK_INSUFFISANT` depuis 1a via la sous-classe `StockInsuffisantException` ; écart d'inventaire non justifié, transition de workflow invalide), `StockConflitException` → **409** (code produit dupliqué). Le `/bulk` renvoie le **même corps** `{total, inserted, failed, insertedIds, errors[]}` en 200 (succès total) et 422 (échec → rollback total, 0 créé).
- **Transactionnalité** : validation des bons **en transaction** depuis le lot 1 / 1a (`ExecuteurTransactionnel`, débits gardés `StockBalanceService.debiter`, 422 `STOCK_INSUFFISANT`), clôture d'inventaire en transaction depuis 1b (écarts via mouvements `AJUSTEMENT`, `appliquerDelta` non gardé : l'inventaire global peut porter l'entrepôt en négatif). La compensation manuelle ne reste que pour le `/bulk` all-or-nothing (1e).
- **Pièces (1b)** : bons et inventaires sont des pièces (`BE`, `BS`, `INV`) : numéro à la validation / clôture, abandon d'un brouillon, annulation tracée d'une pièce validée. Les mouvements d'une pièce annulée sont **contre-passés dans la transaction d'annulation** (`ContrePassementStockService`, mouvements `origine = "ANNULATION"` + `contrePasse = true` sur l'original) et **exclus des consommations et des coûts** via `MouvementsEffectifs` — à appliquer à tout nouvel agrégat de consommation ; les rejeux de stock gardent tout.
- **Références** : `MVT-yyyyMMdd-NNN` par `CompteurStockService` (même `findAndModify` que `CompteurLotService`) ; bons et inventaires par `NumerotationService` depuis 1b (`BE-2026-0001`, `BS-…`, `INV-…`), les anciennes références (`BE-yyyyMMdd-NNN`, `INV-yyyyMMdd-NNN`) restant telles quelles.
- **Binaire inline** (`byte[] @JsonIgnore` photo + fiche technique sur `ProduitStock`), URL calculée par le mapper, endpoints de streaming protégés JWT.
- **Tests** : 10 IT Testcontainers (`services/stockv2/*IT`, dont les 4 de 7.5 : `ChantierServiceIT`, `AnalyseDonsServiceIT`, `ComparatifAnalyseServiceIT`, `FiltreCroiseServiceIT`) + 4 slices `@WebMvcTest` (`controllers/stockv2`).

**Ajout 7.4 « Contrôle des mouvements »** (par-dessus 7.3) :
- **Bon multi-lignes porteur du workflow** `BROUILLON→SOUMIS→VALIDE→EFFECTIF` ou `SOUMIS→REFUSE` (`BonEntree`/`BonSortie`, collections `stockv2_bons_entree`/`_sortie`). Un bon **ne touche au stock qu'à la validation (EFFECTIF)** : `MouvementBonGenerator` crée un `MouvementStock` 7.3 par ligne **dans la transaction de validation** (lot 1 / 1a : mouvements, soldes, coût, historique et compteur ensemble ou pas du tout ; plus de compensation). Il met à jour `StockParSite` par **débit gardé** (`quantite >= q`). Sortie insuffisante → **422 `STOCK_INSUFFISANT`** (la pré-vérif cumulée par produit donne le message, le débit gardé la garantie). Deux validations simultanées : 1 succès, 1 **409**.
- **`MouvementStock` étendu** de 5 champs optionnels : `origine` (`DIRECT`/`BON`), `bonId`, `bonReference`, `categorieEntree`/`categorieSortie`. Réfs `BE-`/`BS-yyyyMMdd-NNN` (même `CompteurStockService`).
- ⚠️ **Transition de bon invalide → 409** (`StockConflitException`) — contrat frontend figé, **distinct** du 422 « transition » d'inventaire 7.3. Refus sans commentaire → **400**.
- **Plafonds de dotation** (`stockv2_plafonds`), comparatif prévu/réel, catégorisation (sur mouvements `origine=BON`), consommation par destinataire (sur `BonSortie` EFFECTIFS) + rapports (sur mouvements SORTIE effectifs).
- **WebSocket** `StockNotificationService` → `/topic/stock-validations` (broadcast) + `/user/queue/notifications-stock` (Responsable Achats / dépassement plafond), payload `NotificationStockDto`. Depuis 1b, émis par le consommateur `NotificationsBonsConsommateur` (≈ 2 s après l'action), + `BON_ANNULE`.
- **E-mail à la création d'un bon** (`BonMailNotificationService`, 2026-07-29) : `POST /api/stock/bons-entree` et `/bons-sortie` envoient un mail HTML récapitulatif (référence, type, date, site, destinataire/fournisseur, nb de lignes, montant, auteur) + lien vers la fiche. **Destinataires** = le `SUPERADMIN` (collection `login`, seul endroit où ce rôle existe → `UserRepository.findByRoleIgnoreCase`) et les `CONTROLEUR_STOCK` actifs (collection `utilisateur` → `UtilisateurRepository.findByRoleAndActiveTrue`), sur leur **adresse de connexion**, dédoublonnée, **un envoi par destinataire**. Réutilise `EmailService.sendHtmlEmail` (même mécanique que le mail « mot de passe oublié ») ; depuis 1b, envoyé par le consommateur `MailCreationBonConsommateur` de l'événement `PIECE_CREEE`, plus pendant la requête. Lien construit depuis `app.frontend.base-url` = **URL publique de l'application** (ce que l'utilisateur ouvre dans son navigateur, à ne pas confondre avec l'API) : `http://localhost:4200` en dev, `https://pointic-cleanic.com` en prod — aligné sur le CORS de `SecurityConfig` —, surchargeable par `APP_FRONTEND_BASE_URL` ; si vide, le mail part sans lien. ⚠️ **Tolérance aux pannes obligatoire** : tout échec (SMTP, Mongo) est capté et loggé en `WARN` — la création du bon doit aboutir même sans mail. Aucun envoi sur `modifier`/`soumettre`/`valider`/`refuser`.
- **RBAC** : 8 sous-flags ajoutés à `SousModules/Stock` (`categorisation, bonsEntree, bonsSortie, workflowValidation, historiqueDestinataire, plafonds, dotation, rapportsConso`) — gating **frontend uniquement**, sérialisés dans `modules.stock`.

**Ajout 7.5 « Analyse des consommations »** (par-dessus 7.4) — **analytique, LECTURE SEULE** sauf l'unique entité persistée `Chantier` :
- **Enrichissement du contrat 7.4** : `TypeSortie` gagne la valeur `DON` (→ 5 valeurs) + enum `NatureDon`. `BonSortie`/`BonSortiePayload`/`BonSortieDto` portent `natureDon`+`beneficiaireDon` (requis si `type=DON`, `chantierId` interdit) et `chantierId`+`chantierReference` (requis si `type=DISTRIBUTION_CHANTIER`, `natureDon` interdit) — validés dans `BonSortieService`. `MouvementBonGenerator` **recopie ces 4 champs sur chaque `MouvementStock` SORTIE** (entité+DTO étendus) pour rendre dons/chantiers requêtables sur la collection mouvements. Valider un bon `DISTRIBUTION_CHANTIER` vers un chantier `CLOTURE` → **409**.
- **Périmètre « sortie effective »** des 4 endpoints d'analyse = `MouvementStock` `type=SORTIE` **ET** `origine="BON"` (jamais les saisies DIRECT 7.3 ni brouillons/soumis) — centralisé dans `AnalyseSupport`. **Valorisation** : `montant = round(quantité × ProduitStock.prixUnitaire)` (prix fixe porté par le produit), FCFA entiers. Filtre `categorieId` appliqué en mémoire (pas de catégorie sur le mouvement).
- **`Chantier`** (collection `stockv2_chantiers`, réf. unique, enum `StatutChantier {EN_COURS, CLOTURE}`) : `coutTotal`/`nbMouvements` **non maintenus à l'écriture** → recalculés à la lecture depuis `MouvementStockRepository.findByChantierId`. Conflits d'état (double clôture, édition/suppression d'un clôturé, réf dupliquée) → **409** ; clôture pose `dateFin = today`.
- **Dons** (`/analyse/dons`) agrégés depuis `BonSortie` `type=DON & statut=EFFECTIF` (une ligne par bon, montant = `montantTotal`). **Comparatif** (`/analyse/comparatif`) : barème `SensEvolution {HAUSSE, BAISSE, STABLE, ALERTE}` sur l'écart % vs mois précédent de la ligne (1ʳᵉ colonne et précédent=0 → `evolutionPct=null` pour éviter `Infinity`) ; `nbAlertes` = cellules `ALERTE`. **Filtres croisés** (`/analyse/croise`) : pivot 1D (sans `axeColonnes` → `entetesColonnes=[]`/`valeurs=[]`/`total` rempli) ou 2D ; enums `AxeAnalyse`, `MesureCroise`, `AxeComparatif`.
- **RBAC** : 5 sous-flags ajoutés à `SousModules/Stock` (`analyseMensuelle, chantiers, dons, comparatif, filtresCroises`) — gating **frontend uniquement**.

**Ajout 7.6 « Valorisation financière »** (par-dessus 7.5) — volet **FINANCIER**, calculs serveur, FCFA entiers (créé 2026-06-19) :
- **`ProduitStock.prixUnitaire` = désormais le « coût unitaire courant »** : statique si méthode `FIXE`, recalculé à chaque ENTREE de bon si `CUMP`/`DERNIER_PRIX`. 2 champs ajoutés (`methodeValorisation` enum `MethodeValorisation {CUMP,DERNIER_PRIX,FIXE}` nullable → hérite du global `ParametrageValorisation.methodeDefaut` → `FIXE` ; `prixVente` Long). **Non éditables via le formulaire multipart 7.3** : exclus de `ProduitStockMapper.toEntity`/`updateEntityFromDto` (sinon le PUT figé les écraserait), écrits seulement par les 2 PATCH dédiés. `MouvementStock` += `coutUnitaireSnapshot`/`valeurMouvement` (renseignés à chaque nouveau mouvement ; mouvements pré-7.6 = null → reconstitution au coût courant + `estEstime=true`).
- **Recalcul** (`ValorisationSupport`, pur+testé) : CUMP `round((stockAvant×ancienCout + q×pa)/(stockAvant+q))` (stock+q≤0→`round(pa)`, q≤0→inchangé), DERNIER_PRIX→`round(pa)`, FIXE→rien. Hook dans `MouvementBonGenerator.genererPourEntree` (met à jour `prixUnitaire` + historise un `HistoriquePointCout`), dans la **transaction** de validation du bon (lot 1 / 1a). Entrée DIRECTE 7.3 = pas de recalcul (snapshot au coût courant). `pa` = `LigneBon.prixUnitaire`, alimenté par le **nouveau champ optionnel `LignePayload.prixUnitaire`** (additif ; absent ⇒ coût courant, comportement 7.4/7.5 inchangé).
- **Valeur historique** (`valeurPrecedente`, `evolutionValeur`) = rejeu des mouvements × coût courant actuel (approximation, comme TableauBordStock). Seuils serveur : dérive 20 % (gravité CRITIQUE ≥40 / ATTENTION ≥20), marge mini 15 %, écart coût anormal 50 %. Marges = sorties effectives `categorieSortie=VENTE_PRODUIT`.
- **CORS** : `PATCH` ajouté à `SecurityConfig.setAllowedMethods`. 7 sous-flags ajoutés à `SousModules/Stock` (`coutUnitaire, coutMouvements, valeurStock, coutSite, coutChantier, marges, tableauBordFinancier`) — gating frontend only.

Détails endpoints (query params, payloads), entités/collections/index, formules serveur (synthèse, suggestions appro, KPIs dashboard, valorisation 7.6), workflow inventaire, workflow des bons 7.4, analyses 7.5, codes d'erreur : **voir `.claude/docs/module-stockv2.md`**.
