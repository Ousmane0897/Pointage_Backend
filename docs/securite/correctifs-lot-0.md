# Correctifs de sécurité — lot 0

Audit du 20/11/2026 (`pointage-agents/docs/audit/etat-des-lieux-v3.md`, §4.1). Branche `fix/securite-lot-0` dans les deux dépôts, créée depuis `main` (back) et `master` (front) dans des worktrees séparés (`../Pointage-Cleanic-Backend-securite`, `../pointage-agents-securite`) : les arbres de travail en cours (QR d'agence, réclamations) n'ont pas été touchés. **Rien n'a été poussé ni fusionné.**

Ce document ne contient aucune valeur de secret.

---

## 1. État de référence et bilan des tests

### Backend

| | `main` (7f6e176) | `fix/securite-lot-0` |
|---|---|---|
| Tests exécutés | 893 | **1 032** (+139 net ; détail §1.3) |
| Échecs | **0** | **0** |

La référence a été mesurée dans un worktree propre de `main`, seul sur la machine (`./gradlew test`, 7 min 52 s). Une première mesure, lancée en parallèle d'autres builds, avait produit 159 échecs, tous des `MongoTimeoutException` dus à la saturation de la machine : elle a été écartée. **La mention « 18 tests legacy cassés » de la mémoire du projet est périmée** : `main` passe intégralement.

Chaque commit backend a été validé par `./gradlew test` complet (BUILD SUCCESSFUL) avant d'être créé.

### Frontend

Avant ce lot, la suite Karma ne s'exécutait pas jusqu'au bout : `src/polyfills.ts` (`window.global`, requis par sockjs) n'était pas chargé en test, et tout spec important le service WebSocket plantait au chargement (« global is not defined »). Le nombre de specs exécutés variait donc d'un lancement à l'autre (101 ou 234 sur `master`). Corrigé par le commit `210efc7` (configuration de test seulement).

Suite complète sur la branche : **334 specs, 49 en échec**. Ces échecs **préexistent** sur `master` :

| Spec | `master` (mêmes specs, isolés) | Branche | Cause |
|---|---|---|---|
| `GestionPrivilegeComponent` (13), `SidebarComponent` (27), `AdminComponent`, `SuperAdminLoginPageComponent` | 43 échecs sur 47 | idem | specs non maintenus (fournisseurs manquants) |
| `WebsocketService (Jasmine)` (4) | 4 échecs (vérifié avec le service d'origine) | idem | spec qui espionne une API STOMP inexistante |
| `HeaderComponent` (minuterie), `ConfirmDialogComponent` | passent en isolation | passent en isolation | dépendants de l'ordre d'exécution |
| `ResetPasswordComponent` « default error message » | **échoue** | **passe** | corrigé par ce lot (`err?.error?.message`) |

Les specs ajoutés ou modifiés par ce lot passent tous : intercepteur, garde de module, pages finales du kiosque, réinitialisation du mot de passe, jeton WebSocket. `ng build` réussit après chaque commit front.

### 1.3 Tests ajoutés (backend)

| Fichier | Tests | Couvre |
|---|---|---|
| `security/SecuriteITBase` | — | socle : `@SpringBootTest` + MockMvc **filtres actifs** + Testcontainers ; comptes en base, vrais JWT ; `verifierAcces` (401 / 403 / accès) |
| `ConnexionSecuriteIT` | 7 | C1 |
| `GestionComptesSecuriteIT` | 6 | C2, compte désactivé |
| `DroitsTest` | 9 | règles de `Droits` |
| `SecretsRequisVerificateurTest`, `AucunSecretLitteralTest`, `DataLoaderSuperadminTest` | 4 + 2 + 3 | C3 |
| `DroitsPaieSecuriteIT` | 6 | C4 paie |
| `DroitsPersonnelSecuriteIT` | 6 | C4 dossiers, contrats, vue réduite |
| `DroitsModulesSecuriteIT` | 70 | C4, une route par contrôleur (RH, Stock, Terrain, Chimie, pointages) + libre-service congés |
| `PointagePublicSecuriteIT`, `PointageLimiteurDebitTest` | 5 + 7 | C5, dont 30 pointages d'appareils différents depuis la même IP sans 429 |
| `ReinitialisationMotDePasseSecuriteIT` | 3 | C6 réinitialisation (bout en bout) |
| `ResetPasswordServiceTest`, `ResetPasswordTokenRepositoryTest`, `PasswordResetControllerTest` | 12 + 4 + 3 | réécrits pour le nouveau contrat |
| `JwtChannelInterceptorTest`, `OriginesSecuriteIT` | 5 + 4 | C6 WebSocket et origines |
| `ProfilProdSecuriteIT`, `DocumentationDevSecuriteIT` | 2 + 1 | C6 Swagger et journaux sans DEBUG (profil prod réel / témoin) |
| `RoutesPubliquesIT` | 3 | C7 garde-fou, routes supprimées absentes |

Front : `auth.interceptor.spec` (+1), `module.guard.spec` (6), `final-page1/2.spec` (réécrits, 3 + 3), `websocket.service.jeton.spec` (2), specs de réinitialisation adaptés.

---

## 2. Correctifs

### C1 — Routes de connexion (critique) — `5e1aae5` (back), `969d6c9` (front)

- `SecurityConfig` : seul `POST /api/login` est public (avant : `/api/login/**`).
- `POST /api/login/change-password` est **authentifié** : l'e-mail est lu dans le JWT, plus dans le corps, et l'ancien mot de passe reste exigé. Le front dispose déjà d'un jeton à la première connexion ; l'intercepteur le lui transmet désormais.
- `GET /api/login` (tous les comptes avec leur hash) et `DELETE /api/login/{id}` sont **supprimés** (aucun appel front).
- `User.password` et `Utilisateur.password` passent en `@JsonProperty(WRITE_ONLY)` et sont exclus du `toString`. `GET /api/superadmin` ne renvoie plus de hash.
- `PUT /api/superadmin/{id}` : un mot de passe vide signifie « inchangé ». Front, écran des privilèges : mot de passe facultatif en édition, la bascule actif/inactif ne l'envoie plus, la recherche ne porte plus dessus.
- Fichiers : `LoginController`, `UtilisateurController`, `User`, `Utilisateur`, `SecurityConfig` ; front `auth.interceptor.ts`, `gestion-privilege.*`, `admin.model.ts`.

### C2 — Gestion des comptes (critique) — `d5897cd`

- `@EnableMethodSecurity`. `MyUserDetailsService` produisait déjà `ROLE_<role>`, donc `ROLE_SUPERADMIN` (vérifié par un test).
- Nouveau composant `security/Droits` : `estSuperAdmin()`, `peut()`, `peutUnParmi()`. Les droits sont **relus en base**, et un compte `active=false` est toujours refusé.
- `UtilisateurController` porte `@PreAuthorize("@droits.estSuperAdmin()")`.
- `JwtRequestFilter` : un compte désactivé avec un jeton valide reçoit **403 `COMPTE_DESACTIVE`**, un compte supprimé reçoit 401. Avant, ces deux cas produisaient une erreur 500.
- `GlobalExceptionHandler` : `AccessDeniedException` → 403 `ACCES_REFUSE` (sinon 500).
- Journaux :
  - supprimés : le `System.out.println("DATA RECU: " + utilisateur)` (mot de passe en clair) et les trois `System.out` d'`EmployeCompletController` (JSON brut, prénom, téléphone) ;
  - `utils/BcryptGen.java`, qui contenait un mot de passe littéral, est supprimé.
- Autres `System.out.println` relevés dans le dépôt, sans donnée sensible, laissés en place :
  - `EmployeCompletService` (identifiant agent, compteurs) ;
  - `PointageServices:101` (erreur de parsing) ;
  - les `DataLoader` (messages de démarrage).
- Règle morte `/api/super-admin/**` retirée.

### C3 — Secrets suivis par git (critique) — `9586398`

- Les `application*.yml` lisent les secrets dans des variables d'environnement : `${JWT_SECRET}`, `${GOOGLE_MAPS_API_KEY}` (la clé Google n'était pas lue depuis sa variable : corrigé), `${MAIL_USERNAME}`, `${MAIL_PASSWORD}`.
- En prod, **aucune valeur par défaut**. `SecretsRequisVerificateur` (`EnvironmentPostProcessor` enregistré dans `spring.factories`, qui lit lui-même les profils actifs) refuse le démarrage et nomme les variables manquantes. Vérifié en lançant `bootRun` en profil prod sans variables.
- En développement, un `.env` local est importé par Spring et lu par docker-compose (`env_file`). Il est ignoré par `.gitignore` et exclu de l'image par le nouveau `.dockerignore` (le `Dockerfile` fait `COPY . .`). `.env.example` donne les noms des variables, sans valeur.
- `DataLoader` lit le mot de passe initial dans `SUPERADMIN_INITIAL_PASSWORD`. Si la variable manque, aucun compte n'est créé et un `WARN` est journalisé ; `mustChangePassword=true` est conservé.
- Pour les tests, des valeurs factices sont posées dans `src/test/resources/config/application.yml`.
- **Hors dépôt, non modifié** : le `docker-compose.yml` du dossier parent `Projets en cours/`. Vérifiez s'il contient des secrets en clair.
- Rotation, MongoDB et purge de l'historique : **`docs/securite/rotation-secrets.md`**.

### C4 — Droits contrôlés par le serveur (haute) — `3cf5f6e`, `b4232fa`, `6f4d515`, `f391bde`, `415d16d`, `952941f` (back), `904a60b` (front)

`@PreAuthorize` est posé sur chaque contrôleur RH, Stock v2, Terrain et Production chimie, ainsi que sur `EmployeCompletController` et les vues superviseur de `PointagesController`. Le tableau complet figure au §3.

Règles :
- Comme dans la sidebar, SUPERADMIN passe partout, le rôle RH passe sur tout `rh.*`, et un ancien `rh: true` vaut tous les drapeaux RH.
- `module.*` signifie : au moins un drapeau du module.
- `DossierEmployeController` : sans `rh.dossierEmploye`, la liste et la fiche renvoient un `DossierEmployeResumeDto` (id, matricule, nom, prénom, poste, département, site, statut), donc **ni RIB, banque, IPRES/CSS, ni téléphone, adresse, e-mail, naissance, code de pointage**. Vérifié champ par champ avec un jeton `stock.*`.
- Les contrôles déjà présents dans les services (`HabilitationStock`, `CongeIdentiteService`, `ParametresCongesService`, `JourFerieService`…) sont tous conservés.
- `TerrainExceptionHandler`, prioritaire sur `controllers.terrain`, traduit aussi le refus en 403 : son repli `Exception` le transformait en 500.
- Front : `guards/module.guard.ts` (`canActivateChild` sur `/admin`) applique une table « préfixe de route → drapeaux » avec les mêmes règles que la sidebar. Sans droit, l'utilisateur est redirigé vers la page par défaut. C'est un confort d'affichage seulement.

**Écarts avec le tableau validé, justifiés par le code :**
- **Congés et jours fériés : authentifiés, sans drapeau.**
  - « Mes demandes », le solde, la création et la modification d'une demande sont du libre-service. `rh.congesMesDemandes` et `rh.congesValidation` ne sont calculés qu'à la connexion, ne sont pas en base, et `rh.congesParametres` / `rh.joursFeries` n'existent qu'au front.
  - Le périmètre reste filtré par `CongeIdentiteService` (lecture et dépôt), et les écritures du barème et des fériés restent réservées RH/SUPERADMIN par les services.
  - Un test vérifie que le libre-service reste ouvert.
- **Terrain, Production chimie, référentiels Stock :** la lecture accepte n'importe quel drapeau du module, l'écriture exige le drapeau précis. Les écrans se lisent entre eux (sélecteurs de sites, produits, chantiers, formulations, lots).
- **Paramètres :**
  - lecture : tout drapeau du module ;
  - écriture : drapeau principal (`rh.calculBulletin`, `stock.coutUnitaire`, `productionChimie.formulations`, `terrain.alertes`, ce dernier pour la lecture aussi) ;
  - `ParametresPointageController` n'est pas sur `main` (voir §7).
- **Ordre des commits :** un commit par module plutôt qu'un commit « paramètres » à part. Les paramètres sont couverts dans le commit de leur module.

### C5 — Dernier pointage public (haute) — `5257750` (back), `4577018` (front)

- `GET /api/pointages/{codeSecret}`, qui renvoyait nom, GPS, adresse et appareil pour un code à 4 chiffres, est **supprimé** avec sa règle `permitAll`. Sans jeton, la route renvoie 401.
- `POST /api/pointages` renvoie `PointageConfirmationDto` : prénom, statut, date, heures. Ni nom, ni GPS, ni adresse, ni appareil, ni code.
- `PointageLimiteurDebit`, seuils réglables dans `application.yml` (`pointage.limite.*`) :
  - au plus **60 appels par minute par IP** (`par-ip-par-minute`) et **10 par appareil** (`par-appareil-par-minute`), fenêtre glissante. Le seuil par IP est large : un site peut faire pointer beaucoup d'agents derrière une seule IP (testé : 30 pointages valides d'appareils différents depuis la même IP en une minute, aucun 429) ;
  - **blocage de 15 minutes** (`duree-blocage-minutes`) **après 5 codes inconnus d'affilée** (`codes-inconnus-avant-blocage`), remis à zéro au premier succès ;
  - au-delà : 429. La limite `canPoint` existante est conservée.
- L'IP est celle de la connexion. En prod, `server.forward-headers-strategy: native` fait que `X-Forwarded-For` n'est cru que s'il vient d'un mandataire interne : un client ne peut pas choisir son IP. Le `deviceId` est fourni par le client, donc la règle par IP est la protection principale.
- `pointage-agents/nginx.conf` transmet `X-Real-IP`, `X-Forwarded-For` et désormais `X-Forwarded-Proto` sur `/api/` et `/ws`. ⚠ Ce fichier ne sert qu'au montage docker mono-hôte : en production, l'API est servie par `api.pointic-cleanic.com`, derrière un proxy du VPS que nous ne voyons pas. **Si ce proxy ne pose pas `X-Forwarded-For`, tous les clients partagent la même IP**, d'où le seuil à 60 par minute. À vérifier dès que l'accès au VPS est possible.
- Front : la page PIN transmet la confirmation par l'état de navigation, et les pages finales n'appellent plus le GET. Les routes deviennent `pagefinal1` / `pagefinal2`, sans le code PIN dans l'URL. `PageCodeService` est supprimé.
- L'historique authentifié ne change pas.

### C6 — Durcissements moyens

- **Réinitialisation du mot de passe** — `4323041` (back), `9c007eb` (front) :
  - code à 6 chiffres tiré par `SecureRandom`, **stocké haché**, valable 10 minutes, une demande invalide les précédentes ;
  - validation sur **e-mail + code** (avant, n'importe quel code valide de n'importe quel compte suffisait) ;
  - **5 essais**, puis le code est supprimé, et il est à usage unique ;
  - **même réponse que le compte existe ou non**, échec SMTP compris ;
  - purge horaire des codes expirés ;
  - le champ `tentatives` s'ajoute au document éphémère, absent vaut 0, sans migration ;
  - front : l'écran demande l'e-mail (prérempli) et n'affiche plus « Email introuvable ».
- **WebSocket** — `776f175` (back), `581791c` (front) :
  - `JwtChannelInterceptor` valide le Bearer au `CONNECT` et pose le principal (e-mail). Sans jeton, avec un jeton invalide ou un compte désactivé, la connexion est refusée ;
  - les envois `convertAndSendToUser(email, …)` pour les congés et le stock atteignent enfin leur destinataire ;
  - origines : liste unique `OriginesAutorisees` pour le CORS et `/ws` (avant : `*` sur `/ws`), passée en `setAllowedOriginPatterns`. Les motifs locaux fonctionnent désormais ; le joker `*.ngrok-free.dev`, jamais effectif auparavant, n'est **pas** réactivé ; `http://localhost:4200` est ajouté pour le dev ;
  - front : le jeton est relu à chaque (re)connexion, et aucune tentative n'est faite sans jeton.
- **Swagger** — `672bc7e` : désactivé en prod (`/v3/api-docs` et `/swagger-ui.html` → 404, testé avec le profil prod réel), toujours servi en dev.
- **Journaux** — `672bc7e` : `org.springframework.security` et `org.springframework.web` repassent de DEBUG à INFO. En prod, `MongoTemplate` (qui trace les requêtes Mongo, données personnelles comprises) et `com.example` passent aussi en INFO via `application-prod.yml` (`36fc9f0`) ; le DEBUG reste en dev. Vérifié par `ProfilProdSecuriteIT` sur le vrai profil prod (le test échoue sans le correctif).
- **Photos des employés** :
  - le front n'utilise **jamais** `/api/employe-complet/image/**` ni `/api/produits/image/**` : ces deux images publiques sont **supprimées** (C7) ;
  - règle morte `/api/dossier-employe/*/photo` retirée ;
  - l'organigramme affichait `photoUrl` dans un `<img src>`, sans jeton, vers une URL qui ne correspond à aucun contrôleur : les photos ne s'affichaient pas. Elles sont maintenant chargées en blob avec le JWT, comme dans la liste des employés (`9f7e5a9`).

### C7 — Routes inutilisées (moyenne) — `fd12f76`, `fa61d34`, `cef31ed`, `2951707`

- **Garde-fou** (`fd12f76`) : `RoutesPubliquesIT` parcourt les ≈ 380 routes MVC exposées, les appelle sans jeton et exige 401 sauf pour la liste blanche écrite dans le test.
- **Pas d'accès aux journaux nginx**, mais **aucune application mobile** : le seul client de l'API est `pointage-agents`, qui n'appelle aucune des routes retirées (§4). Ce constat tient lieu de preuve de non-utilisation.
- **Règles mortes retirées** (`fa61d34`) : `/pointages/**`, `/api/planification/**`, `/api/dashboard_par_agence`. Aucun contrôleur ne les sert : un appel recevait déjà une 404.
- **`ProduitController` supprimé** (`cef31ed`).
- **Routes en sursis supprimées** (`2951707`) : `GET /api/produits/image/**` (`ProduitImageController`) et `GET /api/employe-complet/image/**`, avec leur `permitAll` et leurs entrées dans la liste blanche, ainsi que `POST /api/terrain/pointages` (les GET de `terrain_pointages` restent jusqu'au correctif des alertes). Le comptage provisoire (`67d7a45` : `SuiviRoutesLegacyFilter`, `GET /api/securite/routes-legacy`, collection `securite_appels_legacy`, jamais déployée) est retiré avec elles. `RoutesPubliquesIT` vérifie que les trois routes n'existent plus.

---

## 3. Tableau final « route → droit exigé »

`R` = référentiel multi-modules : `rh.*`, `terrain.*`, `stock.*`, `productionChimie.*` ou `dashboard`. Le SUPERADMIN passe partout, le rôle RH passe sur tout `rh.*`.

| Contrôleur | Droit exigé |
|---|---|
| **Public** | `POST /api/login`, `POST /auth/forgot-password`, `POST /auth/reset-password`, `POST /api/pointages` (limité), `/ws/**` (JWT au CONNECT), `/error`, Swagger hors prod |
| LoginController `POST /change-password` | authentifié |
| UtilisateurController `/api/superadmin` | `@droits.estSuperAdmin()` |
| PointagesController `GET /`, `/today`, `/historique/**` | `terrain.pointage` \| `rh.pointageCentralise` \| `dashboard` |
| EmployeCompletController (legacy) | `rh.dossierEmploye` |
| GrilleSalarialeController | `rh.grilleSalariale` ; GET aussi `rh.calculBulletin` |
| BulletinPaieController | `rh.calculBulletin` ; GET liste, fiche, historique, PDF aussi `rh.historiquePaies` |
| DeclarationSocialeController | `rh.declarations` |
| ParametresPaieController | GET `rh.grilleSalariale` \| `calculBulletin` \| `declarations` ; PUT `rh.calculBulletin` |
| DossierEmployeController | écritures `rh.dossierEmploye` ; GET liste, fiche, photo → **R** (vue réduite sans `rh.dossierEmploye`) |
| ContratController | `rh.contrats` ; GET aussi `rh.dossierEmploye` |
| DocumentEmployeController | `rh.documents` ; GET aussi `rh.dossierEmploye` |
| OrganigrammeController | `rh.organigramme` ; `/departements` tout `rh.*` |
| TempsPresencesPointageController | `rh.pointageCentralise` |
| TempsPresencesAbsenceController | `rh.absences` \| `rh.conges` \| `rh.dossierEmploye` |
| TempsPresencesCongeController | authentifié (périmètre `CongeIdentiteService`, barème RH/SUPERADMIN dans le service) |
| TempsPresencesJourFerieController | authentifié (écritures RH/SUPERADMIN dans le service) |
| TempsPresencesHeureSupController | `rh.heuresSupplementaires` |
| TempsPresencesRecapController | `rh.recapitulatif` |
| FormationController, BesoinFormationController | `rh.formations` |
| EvaluationPeriodiqueController | `rh.evaluations` (`auto-evaluer` n'est appelé par aucun écran : pas d'exception) |
| SanctionController | `rh.sanctions` |
| TableauBordRhController | `rh.tableauBordRh` |
| ProduitStockController | GET `stock.*` ; écritures `stock.catalogue` ; PATCH valorisation/prix-vente `stock.coutUnitaire` |
| CategorieStockController | GET `stock.*` ; écritures `stock.catalogue` |
| EtatStockController | GET `stock.*` ; PUT `/seuils` `stock.catalogue` \| `stock.etatStock` |
| MouvementStockController | `stock.*` |
| ChantierController | GET `stock.*` ; écritures `stock.chantiers` |
| InventaireController | `stock.inventaires` |
| SyntheseMensuelleController / ApprovisionnementController / TableauBordStockController | `stock.synthese` / `stock.approvisionnement` / `stock.tableauBord` |
| BonEntreeController / BonSortieController | `stock.bonsEntree` / `stock.bonsSortie`, ou `stock.workflowValidation` (+ `HabilitationStock` pour valider et refuser) |
| WorkflowStockController | `stock.workflowValidation` |
| CategorisationStockController | `stock.categorisation` |
| PlafondController | `stock.plafonds` ; GET aussi `stock.bonsSortie` \| `stock.dotation` |
| DotationController | `stock.dotation` |
| ConsommationController | `/par-destinataire` `stock.historiqueDestinataire` ; `/rapport` `stock.rapportsConso` |
| AnalyseStockController | `/mensuel` `analyseMensuelle`, `/dons` `dons`, `/comparatif` `comparatif`, `/croise` `filtresCroises` |
| ValorisationController | `/parametrage` GET `stock.*`, PUT `coutUnitaire` (+ `HabilitationStock`) ; `/couts-produits` `coutUnitaire`, `/mouvements` `coutMouvements`, `/valeur-stock` `valeurStock`, `/cout-site` `coutSite`, `/chantiers` `coutChantier`, `/marges` `marges`, `/tableau-bord` `tableauBordFinancier` |
| SitesClientsController | GET **R** ; écritures `terrain.sitesClients` |
| PlanningController | GET `terrain.*` \| `dashboard` ; écritures `terrain.planning` |
| PointagesTerrainController | GET `terrain.*` (`/aujourd-hui` aussi `dashboard`) ; plus de POST |
| AlertesController (escalade incluse) | GET `terrain.*` \| `dashboard` ; `/parametres` et écritures `terrain.alertes` |
| InterventionsController / ControlesTerrainController / MaterielController / PhytosanitaireController | GET `terrain.*` ; écritures `terrain.interventions` / `controleQualite` / `materiel` / `phytosanitaire` |
| TableauBordTerrainController | `terrain.tableauBord` |
| Production chimie (MatieresPremieres, MouvementsStockChimie, Formulations, Ordres, Lots, ControlesQualite, GrillesControle, FormatsConditionnement) | GET `productionChimie.*` ; écritures `matieresPremieres` / `matieresPremieres` / `formulations` / `ordresFabrication` / `lots` / `controleQualite` / `controleQualite` / `conditionnement` |
| TableauBordProductionController | `productionChimie.tableauBord` |
| ParametresProductionChimieController | GET `productionChimie.*` ; PUT `productionChimie.formulations` |

---

## 4. Routes retirées (C7)

### 4.1 Décision

Le VPS n'est pas accessible : les commandes du §4.3 n'ont pas été lancées. Mais **il n'existe aucune application mobile** (la mention de `CLAUDE.md` était obsolète) : le seul client de l'API est `pointage-agents`, et l'analyse du §4.3 montre qu'il n'appelle aucune de ces routes. Elles sont donc toutes supprimées, sans période d'observation.

| Route | Constat | Décision |
|---|---|---|
| `/pointages/**`, `/api/planification/**`, `/api/dashboard_par_agence` | aucun contrôleur, déjà 404 | règles retirées (`fa61d34`) |
| `GET` / `DELETE /api/login` | aucun appel du front | supprimés en C1 |
| `/api/produits` (hors image) | aucun appel du front, stock historique remplacé par Stock v2 | supprimé (`cef31ed`) ; entité, dépôt, `ProduitService` et collection gardés |
| `GET /api/produits/image/**`, `GET /api/employe-complet/image/**` | publiques, aucun appel du front | supprimées avec leur `permitAll` (`2951707`) |
| `POST /api/terrain/pointages` | aucun appel du front (`terrain-pointage.service.ts` `pointer` n'est appelé nulle part) | supprimé (`2951707`) ; les GET restent jusqu'au correctif des alertes |

### 4.2 Suivi des routes en sursis : retiré

Un comptage provisoire (`67d7a45`) devait observer ces routes pendant 4 semaines avant leur suppression. Sans client mobile, il n'a plus d'objet. Sont supprimés (`2951707`) : `SuiviRoutesLegacyFilter`, `SuiviRoutesLegacyService`, `SuiviRoutesLegacyController` (`GET /api/securite/routes-legacy`), `AppelRouteLegacy` et leurs tests. La collection `securite_appels_legacy` n'a jamais été déployée ; si elle existe dans une base de développement, elle peut être supprimée.

### 4.3 Analyse initiale du code et commandes nginx (pour mémoire)

**Front** (`pointage-agents`, recherche dans tout `src/`) : **aucun appel** à :
- `/api/produits` (y compris `/image`) ;
- `/api/employe-complet` (y compris `/image`) ;
- `POST /api/terrain/pointages` (la méthode `terrain-pointage.service.ts` `pointer` existe mais n'est appelée nulle part) ;
- `/api/dashboard_par_agence` ;
- `/pointages/` sans `/api` ;
- `GET` et `DELETE /api/login`.

`planification.service.ts` appelle `/api/planification/*`, mais aucun contrôleur n'existe : ce code est mort (reporté au lot 1).

**Commandes à lancer sur le VPS** (lecture seule) :

```bash
# 1. Où sont les journaux ? conteneurs et nginx éventuel sur l'hôte
docker ps --format '{{.Names}}\t{{.Image}}'
ls -l --time-style=long-iso /var/log/nginx/access.log* 2>/dev/null

# 2. nginx sur l'hôte (journaux tournants compressés compris)
zgrep -hE '"[A-Z]+ /api/produits' /var/log/nginx/access.log* | awk '{print $6,$7,$9}' | sort | uniq -c | sort -rn | head -50
zgrep -hcE '"POST /api/terrain/pointages' /var/log/nginx/access.log*
zgrep -hE '"[A-Z]+ /(pointages|api/planification|api/dashboard_par_agence|api/super-admin|api/employe-complet)' /var/log/nginx/access.log* | awk '{print $6,$7,$9}' | sort | uniq -c | sort -rn | head
zgrep -hE '"(GET|DELETE) /api/login' /var/log/nginx/access.log* | awk '{print $6,$7,$9}' | sort | uniq -c
# période couverte (première et dernière ligne, tous fichiers confondus)
zcat -f $(ls -tr /var/log/nginx/access.log*) | head -1 | awk '{print $4}'; tail -1 /var/log/nginx/access.log | awk '{print $4}'

# 3. nginx dans un conteneur (remplacer <nginx> par le nom vu en 1)
docker logs <nginx> 2>&1 | grep -E '"[A-Z]+ /api/produits' | awk '{print $6,$7,$9}' | sort | uniq -c | sort -rn | head -50
docker logs <nginx> 2>&1 | grep -cE '"POST /api/terrain/pointages'
docker logs <nginx> 2>&1 | grep -E '"[A-Z]+ /(pointages|api/planification|api/dashboard_par_agence|api/super-admin|api/employe-complet)' | awk '{print $6,$7,$9}' | sort | uniq -c
docker logs <nginx> 2>&1 | grep -E '"(GET|DELETE) /api/login' | awk '{print $6,$7,$9}' | sort | uniq -c
# période couverte : première et dernière ligne, démarrage du conteneur, rotation json-file
docker logs <nginx> 2>&1 | head -1 | awk '{print $4}'; docker logs <nginx> 2>&1 | tail -1 | awk '{print $4}'
docker inspect -f '{{.State.StartedAt}} {{.HostConfig.LogConfig}}' <nginx>
```

La période réellement couverte est **la plus courte** de ces sources : `docker logs` repart de zéro quand le conteneur est recréé. Si elle ne dépasse pas quelques jours, prolongez l'observation avant de conclure.

Si l'accès au VPS devient possible, ces commandes permettent de confirmer après coup qu'aucun client inconnu n'appelait ces routes. Le nginx du front tourne dans son conteneur (`pointage-agents/nginx.conf`) : c'est le cas 3.

**Reporté au lot 1, sans y toucher :**
- les GET de `terrain_pointages` ;
- `EmployeCompletController` (import Excel) ;
- le dépôt `pointage_request` ;
- `front: services/planification.service.ts` ;
- les abonnements à `/topic/annulationRequests` (super-admin, notification).

---

## 5. Effets sur le front et écrans à retester à la main

### 5.1 Changements de comportement pour le front

- **Routes supprimées** (aucune n'était appelée par `pointage-agents`) :
  - `GET /api/login`, `DELETE /api/login/{id}` ;
  - `GET /api/pointages/{codeSecret}` ;
  - toutes les routes `/api/produits/**`, image comprise ;
  - `GET /api/employe-complet/image/{agentId}` ;
  - `POST /api/terrain/pointages` ;
  - Swagger en prod (`/v3/api-docs`, `/swagger-ui.html` → 404).
- **401** : toute route hors liste blanche (§3) sans jeton ou avec un jeton invalide, dont `POST /api/login/change-password`.
- **403** : `ACCES_REFUSE` si le compte n'a pas le droit du module ; `COMPTE_DESACTIVE` si le compte a été désactivé après l'émission du jeton.
- **429** : pointage du kiosque au-delà de 60 appels par minute par IP ou de 10 par appareil, ou pendant les 15 minutes qui suivent 5 codes inconnus d'affilée.
- **Nouvelle clé JWT** : au déploiement, tous les jetons en circulation deviennent invalides (401). Chaque utilisateur doit se reconnecter.

### 5.2 Écrans à retester à la main

1. **Connexion**, puis **changement de mot de passe à la première connexion** (`mustChangePassword`). Le jeton est désormais envoyé.
2. **Gestion des privilèges** :
   - créer un compte (mot de passe obligatoire) ;
   - modifier un compte **sans** retaper le mot de passe, puis **avec** un nouveau mot de passe ;
   - basculer un compte en actif/inactif ;
   - vérifier qu'un compte désactivé est rejeté immédiatement, même déjà connecté.
3. **Mot de passe oublié de bout en bout** : demande, e-mail, saisie de l'e-mail et du code, nouveau mot de passe. Vérifier aussi qu'un e-mail inconnu affiche le même message.
4. **Kiosque** : PIN, pointage d'arrivée puis de départ. Le prénom doit s'afficher sur la page finale, et le code ne doit plus apparaître dans l'URL.
5. **Notifications temps réel** : validation d'un congé, d'un bon de stock, alertes terrain. Vérifier dans la console du navigateur que `STOMP connecté` apparaît.
6. **Compte limité à un seul module** (par exemple `stock.bonsSortie`) :
   - le menu ne montre que ce module ;
   - une URL `/admin/rh/...` saisie à la main redirige vers la page par défaut ;
   - le sélecteur d'employés du bon de sortie fonctionne, avec la vue réduite.
7. **Organigramme** : les photos s'affichent (elles ne s'affichaient pas avant).
8. **Swagger** : inaccessible en prod, accessible en dev.

---

## 6. Variables d'environnement à définir sur le serveur avant de déployer

Dans le `.env` du dossier backend (`chmod 600`), à partir de `.env.example` :

| Variable | Obligatoire en prod |
|---|---|
| `JWT_SECRET` (≥ 32 caractères, **nouvelle valeur**) | oui (démarrage refusé sinon) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` (**nouveau** mot de passe d'application) | oui |
| `GOOGLE_MAPS_API_KEY` (**nouvelle** clé restreinte) | oui |
| `SUPERADMIN_INITIAL_PASSWORD` | non (sans elle, le super-admin initial n'est pas recréé) |
| `APP_FRONTEND_BASE_URL` | non (défaut `https://pointic-cleanic.com`) |

Ordre de déploiement, rotation et purge de l'historique : `docs/securite/rotation-secrets.md`.

---

## 7. Fusion des branches QR d'agence / réclamations

> **Hors périmètre du lot 0 (décision du 2026-11-23).** Le lot 0 part de `main` (back) et `master` (front) : il **ne contient pas** les QR d'agence (`feature/pointage-qr-agence`) ni les réclamations (`feature/reclamation-qr-site`, commitées en l'état en `6718d78`, non vérifiées). Leur mise en conformité avec ce lot se fera **une fois ces fonctionnalités terminées**, en fusionnant `fix/securite-lot-0` dans leur branche, puis en appliquant la liste ci-dessous.

`feature/reclamation-qr-site` (back) et ses équivalents front ne sont pas sur `main`/`master`. Leur fusion **après** ce lot (consigne : pas de fusion de la branche QR d'agence dans `main` sans votre feu vert) demandera :

- **Conflit attendu sur `SecurityConfig`** : le bloc `permitAll` a été réécrit (`/api/login/**` remplacé par `POST /api/login`, suppression de `GET /api/pointages/{codeSecret}` et des règles mortes, CORS en `OriginesAutorisees`). Gardez la version de ce lot et rajoutez seulement les règles des branches :
  - `GET /api/pointages/agence-qr` ;
  - `GET /api/reclamations-publiques/site` ;
  - `POST /api/reclamations-publiques` ;
  - `.authenticated()` sur `/api/pointages/parametres`, placé avant les règles publiques.
- **Liste blanche de `RoutesPubliquesIT`** : ajouter ces trois mêmes routes publiques (`GET /api/pointages/agence-qr`, `GET /api/reclamations-publiques/site`, `POST /api/reclamations-publiques`).
- **Lignes C4 à appliquer :**
  - `ParametresPointageController` : authentifié, contrôle RH/SUPERADMIN déjà fait par le service ;
  - `AgencesQrController` : `@PreAuthorize("@droits.peut('terrain.sitesClients')")`, en gardant le contrôle de `AgenceQrAdminService` ;
  - `ReclamationController` : authentifié, contrôle RH/SUPERADMIN déjà fait par le service ;
  - `ReclamationPubliqueController` : public, avec son limiteur.
- **C5 sur la branche QR** : le `POST /api/pointages` de la branche renvoie `PointageResponse` (`@JsonUnwrapped Pointage` : nom, GPS, adresse…) et le front y lit `type`, `heureArrive`/`heureDepart`, `agenceNom`, `avertissement`, `codeSecret`. Il faudra :
  - étendre `PointageConfirmationDto` avec `type`, `agenceNom` et `avertissement` (sans `codeSecret`) ;
  - garder la suppression du GET ;
  - garder la navigation des pages finales par l'état.
- **`TerrainExceptionHandler` et `GlobalExceptionHandler`** : les deux branches les modifient. Conservez le gestionnaire `AccessDeniedException` → 403.

---

## 8. Ce qui n'a pas été fait, et pourquoi

- **`ProduitService`** n'est plus appelé que par son test depuis la suppression de `ProduitController` : à retirer au lot 1 avec le stock historique.
- **Rotation des secrets, variables du VPS, purge de l'historique git** : actions manuelles qui vous reviennent (`rotation-secrets.md`). L'historique n'a pas été réécrit.
- **MongoDB sans authentification et port 27018 publié** : procédure décrite (`rotation-secrets.md` §4), non appliquée à `docker-compose.yml` sans votre accord.
- **`docker-compose.yml` du dossier parent** : hors dépôt, non modifié.
- **Notifications Terrain ciblées** : `TerrainNotificationService` envoie à un **identifiant**, alors que le principal STOMP est l'**e-mail**. Ces messages ciblés n'arrivent toujours pas (les diffusions `/topic` fonctionnent). C'est un correctif fonctionnel, hors périmètre.
- **Jeton après changement de mot de passe** : `change-password` régénère le JWT sans `CongeModuleEnricher` (comportement préexistant). Les drapeaux congés calculés à la connexion manquent jusqu'à la reconnexion suivante, sans effet sur la sécurité serveur.
- **WebSocket** : si le service est instancié avant la connexion de l'utilisateur, il ne se reconnecte pas tout seul après la connexion. Les écrans qui l'injectent sont tous sous `/admin`, donc après la connexion.
- **Erreurs de lecture avant contrôle de droit** : un paramètre obligatoire manquant ou un `@Valid` échoué donne 400 avant le contrôle `@PreAuthorize` (fonctionnement de Spring). Aucune donnée n'est renvoyée.
- **Specs front non maintenus** (§1) : préexistants, non corrigés (hors sécurité).
- **Droits V3** (5 niveaux, masquage de champs) : lot 1, comme prévu.
