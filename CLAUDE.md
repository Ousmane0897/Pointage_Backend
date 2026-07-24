# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Spring Boot 3.3.6 backend (Java 21, Gradle) for **Pointage Cleanic** — an employee time-tracking / attendance SaaS. Persistence is MongoDB. The app is multi-module (dashboard, planning, pointages, absences, agences/sites, employés, stock, collecte besoins, utilisateurs/auth, RH) and is consumed by an Angular frontend plus a mobile client used for on-site check-ins.

Base package is `com.example.Pointage_Cleanic` (the hyphenated `Pointage-Cleanic` is not a legal Java package — see `HELP.md`).

## Build, run, test

Gradle wrapper is committed. Use it directly; do not rely on a locally installed `gradle`.

```bash
./gradlew clean build -x test        # compile + package, skip tests (CI first stage)
./gradlew test                        # run all tests (CI second stage)
./gradlew bootRun                     # run locally (needs MongoDB on localhost:27017)
./gradlew test --tests "com.example.Pointage_Cleanic.controllers.EmployeCompletControllerTest"
./gradlew test --tests "*EmployeCompletControllerTest.get_all_ok"   # single test method
```

Tests are forced single-threaded (`maxParallelForks = 1`, JUnit parallel disabled) in `build.gradle` — do not re-enable parallel execution without checking why it was disabled (Testcontainers / shared Mongo state).

### Running with Docker

```bash
docker-compose up --build             # Mongo (port 27018 on host) + Spring Boot on 8080, profile=prod
```

`docker-compose.yml` currently hardcodes `MAIL_PASSWORD`, `JWT_SECRET`, and `GOOGLE_MAPS_API_KEY` — treat those as already-compromised and regenerate before any production deploy; do not commit replacements.

### Profiles & configuration

- `application.yml` — default, points at `mongodb://localhost:27017/cleanic`, logs `com.example=DEBUG`.
- `application-dev.yml` — dev JWT secret + SMTP via `MAIL_USERNAME` / `MAIL_PASSWORD` env vars.
- `application-prod.yml` — Mongo host `mongo` (docker-compose service), `JWT_SECRET` from env.
- `application-test.yml` — dummy JWT secret, disabled mail; auto-applied in tests.

Jackson is configured globally for `dd/MM/yyyy` dates in `Africa/Dakar`. Preserve that when adding DTOs — don't hardcode other formats.

## Architecture

Standard layered Spring MVC: `controllers → services → repositories (Spring Data Mongo) → entities (@Document)`. DTOs live in `Dto/`, MapStruct mappers in `Mapper/` (the `-Amapstruct.defaultComponentModel=spring` compiler arg wires them as Spring beans — don't add `@Mapper(componentModel="spring")` manually).

Things that only make sense after reading several files:

- **Auth is stateless JWT.** `security/SecurityConfig.java` disables CSRF, sets `SessionCreationPolicy.STATELESS`, and installs `JwtRequestFilter` before `UsernamePasswordAuthenticationFilter`. The filter skips `OPTIONS` (preflight) and rejects expired/invalid tokens with 401. **Depuis 2026-07-21, un `authenticationEntryPoint` global renvoie aussi 401 quand aucun token n'est fourni** (auparavant : 403, défaut `Http403ForbiddenEntryPoint` de Spring Security). Le changement est sans risque de masquage ici car ce backend n'a **aucune autorisation par rôle** (pas de `@PreAuthorize`, tout est `.authenticated()`) : un 403 de la couche sécurité signifiait donc toujours « non authentifié ». Public routes are whitelisted there: `/api/login/**`, `/auth/forgot-password`, `/auth/reset-password/**`, Swagger, `/ws/**`, image endpoints (`/api/produits/image/**`, `/api/employe-complet/image/**`), and the mobile clock-in surface: `POST /api/pointages`, `GET /api/pointages/{codeSecret}` (statut), and the legacy `/pointages/**` tree (mobile clocks in without a token). **Les vues superviseur sont protégées** depuis 2026-06 : `GET /api/pointages/today`, `/api/pointages/historique/**` (recherche + exports) et `GET /api/pointages` (getAll) exigent un JWT (ordre des matchers : règles `.authenticated()` placées avant le `permitAll` car `/{codeSecret}` recouvre `/today`). Everything else is `.authenticated()` — y compris `/api/terrain/**` et `/api/stock/**` (module Stock v2, ajouté 2026-06), explicitement listés avant le `anyRequest`. When adding a controller, decide explicitly whether to permit it here.

- **CORS allowlist is in `SecurityConfig`, not a properties file.** Frontend origins (`pointic-cleanic.com`, `app.pointic-cleanic.com`, ngrok subdomains, localhost) are hardcoded. Add new origins there.

- **Two parallel user models coexist.** `User` (collection used by `LoginRepository` + `DataLoader` bootstrap superadmin `diarra.niang@cleanicsenegal.com`) and `Utilisateur` (richer admin entity with `RoleAdmin`, `ModulesAutorises`, activation flags). `MyUserDetailsService` bridges them for Spring Security. Don't collapse them without understanding which flows use which.

- **Module-based authorization.** `Utilisateur.modulesAutorises: ModulesAutorises` is a per-user feature-flag object (booleans for top-level modules + nested sub-module objects under `entities/GestionModules/SousModules/`). Route-level authorization in `SecurityConfig` is coarse (`.authenticated()`); fine-grained gating is **delegated to the Angular frontend** which reads `ModulesAutorises` from the JWT/`AuthResponse2` to show/hide screens — there are **no `@PreAuthorize`/`@Secured` annotations** on the backend. Top-level flags include `Dashboard, Admin, StatistiquesAgences, Planifications, Calendrier, JourFeries, Employes, Agences, RH`; sub-modules `CollecteLivraison, Absences, Pointages, Stock` carry nested booleans. The `RH` flag (added 2026-04-30) gates the entire RH module 6.1–6.4. Le module **Stock v2** a son propre objet `ModulesAutorises.stock` (`SousModules/Stock`, ajouté 2026-06) avec 27 sous-flags : 7 pour 7.3 `{catalogue, mouvements, etatStock, inventaires, synthese, approvisionnement, tableauBord}` + 8 pour 7.4 `{categorisation, bonsEntree, bonsSortie, workflowValidation, historiqueDestinataire, plafonds, dotation, rapportsConso}` + 5 pour 7.5 `{analyseMensuelle, chantiers, dons, comparatif, filtresCroises}` + 7 pour 7.6 `{coutUnitaire, coutMouvements, valeurStock, coutSite, coutChantier, marges, tableauBordFinancier}` ; sérialisé tel quel dans le claim JWT `modules` (`@JsonInclude(NON_NULL)` → les utilisateurs existants restent inchangés). `RoleAdmin.RH` exists in the enum but is just a profile tag — without `ModulesAutorises.RH=true`, the RH screens stay hidden.

- **WebSockets (STOMP over SockJS) are a first-class channel**, not an afterthought. `WebSocketConfig` exposes `/ws`, broadcast prefixes `/topic` + `/queue`, client-to-server prefix `/app`. Used for the admin ↔ superadmin cancel/validate workflow (see `Dto/AnnulationRequestMessage.java`, `Dto/AnnulationDecisionMessage.java`, `Dto/CancelRequestDto.java`, `Dto/ValidationRequestDto.java`). `/ws/**` is permitAll but `/ws/info` requires auth — mirror that pattern for new endpoints.

- **Scheduled tasks are enabled** (`@EnableScheduling` on the main class). Look for `@Scheduled` before assuming a job is triggered by a request.

- **MongoDB custom repository pattern** is used for `ProduitRepository` (`ProduitRepositoryCustom` + `ProduitRepositoryImpl`). Follow that split — don't inline complex aggregation into the `MongoRepository` interface.

- **Reports & imports:** Apache POI (`poi-ooxml`) for Excel, OpenPDF for PDF, plus `ImportEmployeRequest/Response/Error` DTOs drive Excel-based employee import. `GeocodingService` + `WebClientConfig` hit Google Maps using the key from `google.maps.api.key`.

### Controller URL map (useful when reading SecurityConfig / frontend calls)

| Prefix | Controller |
| --- | --- |
| `/api/login`, `/auth` | LoginController, PasswordResetController |
| `/api/superadmin` | UtilisateurController |
| `/api/employe`, `/api/employe-complet` | EmployesController, EmployeCompletController |
| `/api/agences`, `/api/site` | AgencesController, SitesController |
| `/api/planification`, `/api/ferie` | PlanificationController, FerieController |
| `/api/pointages`, `/api/absences` | PointagesController (public), AbsencesControllers |
| `/api/produits` | ProduitController (**stock historique** — entité `Produit`, collection `produits`. ⚠️ Ne mappe PAS `/api/stock`, qui appartient au module Stock v2 7.3/7.4/7.5 ci-dessous) |
| `/api/besoins` | CollecteBesoinController |
| `/api/dashboard`, `/api/dashboard_par_agence` | DashboardController, DashboardParAgence |
| `/ws` | STOMP endpoint |
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
| `/api/stock/bons-entree` | BonEntreeController (bon multi-lignes à workflow ; `/{id}/soumettre`, `/valider`, `/refuser`) |
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

- Controller tests use `@WebMvcTest(XxxController.class)` with `@AutoConfigureMockMvc(addFilters = false)` — security is disabled in slice tests, so `JwtRequestFilter`, `JwtUtil`, and `MyUserDetailsService` must still be declared as `@MockBean` (the Spring context loads them). Copy this pattern from `EmployesControllerTest`.
- Integration tests that need a real Mongo extend `MongoTestContainer` (Testcontainers `mongo:7.0`, container reused across tests). `configurations/AbstractMongoTest.java` is the main-source counterpart.
- Test profile (`application-test.yml`) has mail disabled (`spring.mail.host: disabled`) — don't wire `JavaMailSender` into tests that load the full context without mocking it.

## CI

`.github/workflows/backend.yml` runs on push to `main` only: JDK 21 setup → `./gradlew clean build -x test` → `./gradlew test`. Docker push and VPS deploy steps are commented out. The `develop` branch (current working branch) is not covered by CI.

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
- **Suppression EmployeComplet — feuille de route** : aucun service `services/rh` ne dépend plus d'`EmployeComplet`. Bloqueurs restants avant suppression du module (`entities/EmployeComplet`, `EmployeCompletRepository/Service/Controller` `/api/employe-complet`, `EmployeCompletMapper`, DTOs `EmployeCompletDto`/`ImportEmploye*`, + 3 tests) : (1) sync `Employe` legacy maintenue par `EmployeCompletService` via `EmployeMapper` et servie par `EmployesController` `/api/employe` ; (2) endpoint image public `GET /api/employe-complet/image/{agentId}` (front/mobile) → à reporter sur un endpoint photo `DossierEmploye` ; (3) import Excel `/api/employe-complet/import-excel` → à porter sur le flux `DossierEmploye`. `Dto/MouvementStock` (module stock) ne référence plus `EmployeComplet` (import mort retiré).
- **Tableau de bord RH** : `TableauBordRhService` agrège en parallèle via `CompletableFuture.supplyAsync` (pas de `$lookup` — pas de clé de jointure commune entre collections).

Détails endpoints, workflows (PeriodeEssai, DocumentEmploye, BulletinPaie, EvaluationPeriodique, Sanction), validations métier, formules de calcul de paie : **voir `.claude/docs/module-rh.md`**.

## Module Production Chimie (5.1) — ✅ Backend terminé

Module **autonome** (collections préfixées `production_chimie_`), ne partage aucune collection avec le module Stock historique. 9 contrôleurs REST sous `/api/production-chimie/`.

Patterns de référence introduits par ce module, **réutilisables ailleurs** :

- **Génération atomique de séquences** : `MongoTemplate.findAndModify(... $inc compteur ... upsert=true, returnNew=true)` avec un document `{_id: "AAAAMMJJ", compteur: long}` par jour. Test concurrent `CompteurLotServiceIT` (100 threads → 100 numéros distincts). Réutiliser ce pattern pour toute nouvelle séquence.
- **Compensation manuelle des opérations multi-document** : MongoDB en standalone (pas de transactions multi-doc). Le service applique manuellement les inverses sur échec à mi-parcours (voir `OrdreFabricationService.lancer` / `annuler`). Vérifier d'abord la faisabilité (ex : `quantiteEnStock ≥ quantiteTheorique` avant toute écriture) pour éviter le cas commun.
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
- **Indexes** : `TerrainIndexesConfig` sur `terrain_affectations` (`statut_debut_fin`, `employe_id`, `site_statut`), qui n'en avait aucun alors que `AffectationStatutScheduler` la requête chaque minute. ⚠️ `spring.data.mongodb.auto-index-creation` est **désactivé** dans ce projet : les annotations `@Indexed`/`@CompoundIndex` présentes sur les entités sont **inopérantes** — tout index doit passer par un `*IndexesConfig` programmatique.
- **TODO** : sous-permissions `ModulesAutorises.terrain.*` à ajouter dans `entities/GestionModules/SousModules/` (gating frontend) ; tests d'intégration.

Détails endpoints, payloads WebSocket, hypothèses (seuils escalade/maintenance, identité utilisateur courant), codes d'erreur : **voir `.claude/docs/module-terrain.md`**.

## Module Stock v2 (7.3 + 7.4 + 7.5 + 7.6) — ✅ Backend terminé

Sous-module **« Stocks & Approvisionnement » (7.3)** + **« Contrôle des mouvements » (7.4)** + **« Analyse des consommations » (7.5)** + **« Valorisation financière » (7.6)**, **autonome** (collections préfixées `stockv2_`, ne partage rien avec le stock historique `produits` ni stock-chimie `production_chimie_*`). 19 contrôleurs REST sous `/api/stock/`. Code sous les sous-packages `stockv2` : `controllers/stockv2`, `services/stockv2`, `repositories/stockv2`, `entities/stockv2`, `Dto/stockv2`, `Mapper/stockv2`, `Enum/stockv2`. **Frontend Angular figé** → contrats (chemins, champs JSON, enums, codes HTTP) respectés à la lettre. Créé sur la branche `feature/stock` (7.3 : 2026-06-17 ; 7.4 + 7.5 : 2026-06-18 ; 7.6 : 2026-06-19).

Spécificités / patterns :
- **`StockParSite` = source de vérité des quantités** (collection `stockv2_etats_stock`, index composé unique `(produitId, siteId)`). `ProduitStock.quantiteTotale` n'est **pas** stocké : dénormalisé en lecture (somme des soldes). Logique de solde mutualisée dans `StockBalanceService`. Bucket `siteId=null` pour le stock initial d'import (sans site).
- **Sites en lecture seule** depuis le module Terrain : `ReferentielSiteService` lit `sites_clients` (via `SiteClientRepository`) pour valider `siteId` et dénormaliser `siteNom`. Aucun référentiel de sites côté stock.
- **Utilisateur créateur** déduit du JWT via `CurrentUserProvider` (réutilisé depuis `services/terrain`), jamais envoyé par le client.
- **Dates ISO** : `LocalDate` avec `@JsonFormat("yyyy-MM-dd")`, `LocalDateTime` ISO, `mois` en `yyyy-MM` (pas le `dd/MM/yyyy` global).
- **Erreurs** : `GlobalExceptionHandler` global. Nouvelles exceptions — `StockOperationException` → **422** (stock insuffisant, écart d'inventaire non justifié, transition de workflow invalide), `StockConflitException` → **409** (code produit dupliqué). Le `/bulk` renvoie le **même corps** `{total, inserted, failed, insertedIds, errors[]}` en 200 (succès total) et 422 (échec → rollback total, 0 créé).
- **Transactionnalité** sans transaction Mongo : compensation manuelle (pattern `OrdreFabricationService`) pour le `/bulk` all-or-nothing et la clôture d'inventaire (écarts appliqués via mouvements `AJUSTEMENT`).
- **Références** générées par `CompteurStockService` (même `findAndModify` que `CompteurLotService`) : `MVT-yyyyMMdd-NNN`, `INV-yyyyMMdd-NNN`.
- **Binaire inline** (`byte[] @JsonIgnore` photo + fiche technique sur `ProduitStock`), URL calculée par le mapper, endpoints de streaming protégés JWT.
- **Tests** : 10 IT Testcontainers (`services/stockv2/*IT`, dont les 4 de 7.5 : `ChantierServiceIT`, `AnalyseDonsServiceIT`, `ComparatifAnalyseServiceIT`, `FiltreCroiseServiceIT`) + 4 slices `@WebMvcTest` (`controllers/stockv2`).

**Ajout 7.4 « Contrôle des mouvements »** (par-dessus 7.3) :
- **Bon multi-lignes porteur du workflow** `BROUILLON→SOUMIS→VALIDE→EFFECTIF` ou `SOUMIS→REFUSE` (`BonEntree`/`BonSortie`, collections `stockv2_bons_entree`/`_sortie`). Un bon **ne touche au stock qu'à la validation (EFFECTIF)** : `MouvementBonGenerator` crée un `MouvementStock` 7.3 par ligne (compensation manuelle all-or-nothing) qui met à jour `StockParSite` par le mécanisme 7.3. Sortie insuffisante → **422** (pré-vérif cumulée par produit avant toute écriture).
- **`MouvementStock` étendu** de 5 champs optionnels : `origine` (`DIRECT`/`BON`), `bonId`, `bonReference`, `categorieEntree`/`categorieSortie`. Réfs `BE-`/`BS-yyyyMMdd-NNN` (même `CompteurStockService`).
- ⚠️ **Transition de bon invalide → 409** (`StockConflitException`) — contrat frontend figé, **distinct** du 422 « transition » d'inventaire 7.3. Refus sans commentaire → **400**.
- **Plafonds de dotation** (`stockv2_plafonds`), comparatif prévu/réel, catégorisation (sur mouvements `origine=BON`), consommation par destinataire (sur `BonSortie` EFFECTIFS) + rapports (sur mouvements SORTIE effectifs).
- **WebSocket** `StockNotificationService` → `/topic/stock-validations` (broadcast) + `/user/queue/notifications-stock` (Responsable Achats / dépassement plafond), payload `NotificationStockDto`.
- **RBAC** : 8 sous-flags ajoutés à `SousModules/Stock` (`categorisation, bonsEntree, bonsSortie, workflowValidation, historiqueDestinataire, plafonds, dotation, rapportsConso`) — gating **frontend uniquement**, sérialisés dans `modules.stock`.

**Ajout 7.5 « Analyse des consommations »** (par-dessus 7.4) — **analytique, LECTURE SEULE** sauf l'unique entité persistée `Chantier` :
- **Enrichissement du contrat 7.4** : `TypeSortie` gagne la valeur `DON` (→ 5 valeurs) + enum `NatureDon`. `BonSortie`/`BonSortiePayload`/`BonSortieDto` portent `natureDon`+`beneficiaireDon` (requis si `type=DON`, `chantierId` interdit) et `chantierId`+`chantierReference` (requis si `type=DISTRIBUTION_CHANTIER`, `natureDon` interdit) — validés dans `BonSortieService`. `MouvementBonGenerator` **recopie ces 4 champs sur chaque `MouvementStock` SORTIE** (entité+DTO étendus) pour rendre dons/chantiers requêtables sur la collection mouvements. Valider un bon `DISTRIBUTION_CHANTIER` vers un chantier `CLOTURE` → **409**.
- **Périmètre « sortie effective »** des 4 endpoints d'analyse = `MouvementStock` `type=SORTIE` **ET** `origine="BON"` (jamais les saisies DIRECT 7.3 ni brouillons/soumis) — centralisé dans `AnalyseSupport`. **Valorisation** : `montant = round(quantité × ProduitStock.prixUnitaire)` (prix fixe porté par le produit), FCFA entiers. Filtre `categorieId` appliqué en mémoire (pas de catégorie sur le mouvement).
- **`Chantier`** (collection `stockv2_chantiers`, réf. unique, enum `StatutChantier {EN_COURS, CLOTURE}`) : `coutTotal`/`nbMouvements` **non maintenus à l'écriture** → recalculés à la lecture depuis `MouvementStockRepository.findByChantierId`. Conflits d'état (double clôture, édition/suppression d'un clôturé, réf dupliquée) → **409** ; clôture pose `dateFin = today`.
- **Dons** (`/analyse/dons`) agrégés depuis `BonSortie` `type=DON & statut=EFFECTIF` (une ligne par bon, montant = `montantTotal`). **Comparatif** (`/analyse/comparatif`) : barème `SensEvolution {HAUSSE, BAISSE, STABLE, ALERTE}` sur l'écart % vs mois précédent de la ligne (1ʳᵉ colonne et précédent=0 → `evolutionPct=null` pour éviter `Infinity`) ; `nbAlertes` = cellules `ALERTE`. **Filtres croisés** (`/analyse/croise`) : pivot 1D (sans `axeColonnes` → `entetesColonnes=[]`/`valeurs=[]`/`total` rempli) ou 2D ; enums `AxeAnalyse`, `MesureCroise`, `AxeComparatif`.
- **RBAC** : 5 sous-flags ajoutés à `SousModules/Stock` (`analyseMensuelle, chantiers, dons, comparatif, filtresCroises`) — gating **frontend uniquement**.

**Ajout 7.6 « Valorisation financière »** (par-dessus 7.5) — volet **FINANCIER**, calculs serveur, FCFA entiers (créé 2026-06-19) :
- **`ProduitStock.prixUnitaire` = désormais le « coût unitaire courant »** : statique si méthode `FIXE`, recalculé à chaque ENTREE de bon si `CUMP`/`DERNIER_PRIX`. 2 champs ajoutés (`methodeValorisation` enum `MethodeValorisation {CUMP,DERNIER_PRIX,FIXE}` nullable → hérite du global `ParametrageValorisation.methodeDefaut` → `FIXE` ; `prixVente` Long). **Non éditables via le formulaire multipart 7.3** : exclus de `ProduitStockMapper.toEntity`/`updateEntityFromDto` (sinon le PUT figé les écraserait), écrits seulement par les 2 PATCH dédiés. `MouvementStock` += `coutUnitaireSnapshot`/`valeurMouvement` (renseignés à chaque nouveau mouvement ; mouvements pré-7.6 = null → reconstitution au coût courant + `estEstime=true`).
- **Recalcul** (`ValorisationSupport`, pur+testé) : CUMP `round((stockAvant×ancienCout + q×pa)/(stockAvant+q))` (stock+q≤0→`round(pa)`, q≤0→inchangé), DERNIER_PRIX→`round(pa)`, FIXE→rien. Hook dans `MouvementBonGenerator.genererPourEntree` (met à jour `prixUnitaire` + historise un `HistoriquePointCout`), **participe à la compensation manuelle** all-or-nothing. Entrée DIRECTE 7.3 = pas de recalcul (snapshot au coût courant). `pa` = `LigneBon.prixUnitaire`, alimenté par le **nouveau champ optionnel `LignePayload.prixUnitaire`** (additif ; absent ⇒ coût courant, comportement 7.4/7.5 inchangé).
- **Valeur historique** (`valeurPrecedente`, `evolutionValeur`) = rejeu des mouvements × coût courant actuel (approximation, comme TableauBordStock). Seuils serveur : dérive 20 % (gravité CRITIQUE ≥40 / ATTENTION ≥20), marge mini 15 %, écart coût anormal 50 %. Marges = sorties effectives `categorieSortie=VENTE_PRODUIT`.
- **CORS** : `PATCH` ajouté à `SecurityConfig.setAllowedMethods`. 7 sous-flags ajoutés à `SousModules/Stock` (`coutUnitaire, coutMouvements, valeurStock, coutSite, coutChantier, marges, tableauBordFinancier`) — gating frontend only.

Détails endpoints (query params, payloads), entités/collections/index, formules serveur (synthèse, suggestions appro, KPIs dashboard, valorisation 7.6), workflow inventaire, workflow des bons 7.4, analyses 7.5, codes d'erreur : **voir `.claude/docs/module-stockv2.md`**.
