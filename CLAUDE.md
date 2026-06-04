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

- **Auth is stateless JWT.** `security/SecurityConfig.java` disables CSRF, sets `SessionCreationPolicy.STATELESS`, and installs `JwtRequestFilter` before `UsernamePasswordAuthenticationFilter`. The filter skips `OPTIONS` (preflight) and rejects expired/invalid tokens with 401. Public routes are whitelisted there: `/api/login/**`, `/auth/forgot-password`, `/auth/reset-password/**`, Swagger, `/ws/**`, image endpoints (`/api/produits/image/**`, `/api/employe-complet/image/**`), and the mobile clock-in surface: `POST /api/pointages`, `GET /api/pointages/{codeSecret}` (statut), and the legacy `/pointages/**` tree (mobile clocks in without a token). **Les vues superviseur sont protégées** depuis 2026-06 : `GET /api/pointages/today`, `/api/pointages/historique/**` (recherche + exports) et `GET /api/pointages` (getAll) exigent un JWT (ordre des matchers : règles `.authenticated()` placées avant le `permitAll` car `/{codeSecret}` recouvre `/today`). Everything else is `.authenticated()`. When adding a controller, decide explicitly whether to permit it here.

- **CORS allowlist is in `SecurityConfig`, not a properties file.** Frontend origins (`pointic-cleanic.com`, `app.pointic-cleanic.com`, ngrok subdomains, localhost) are hardcoded. Add new origins there.

- **Two parallel user models coexist.** `User` (collection used by `LoginRepository` + `DataLoader` bootstrap superadmin `diarra.niang@cleanicsenegal.com`) and `Utilisateur` (richer admin entity with `RoleAdmin`, `ModulesAutorises`, activation flags). `MyUserDetailsService` bridges them for Spring Security. Don't collapse them without understanding which flows use which.

- **Module-based authorization.** `Utilisateur.modulesAutorises: ModulesAutorises` is a per-user feature-flag object (booleans for top-level modules + nested sub-module objects under `entities/GestionModules/SousModules/`). Route-level authorization in `SecurityConfig` is coarse (`.authenticated()`); fine-grained gating is **delegated to the Angular frontend** which reads `ModulesAutorises` from the JWT/`AuthResponse2` to show/hide screens — there are **no `@PreAuthorize`/`@Secured` annotations** on the backend. Top-level flags include `Dashboard, Admin, StatistiquesAgences, Planifications, Calendrier, JourFeries, Employes, Agences, RH`; sub-modules `CollecteLivraison, Absences, Pointages, Stock` carry nested booleans. The `RH` flag (added 2026-04-30) gates the entire RH module 6.1–6.4. `RoleAdmin.RH` exists in the enum but is just a profile tag — without `ModulesAutorises.RH=true`, the RH screens stay hidden.

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
| `/api/produits`, `/api/stock` | ProduitController, StockController |
| `/api/besoins` | CollecteBesoinController |
| `/api/dashboard`, `/api/dashboard_par_agence` | DashboardController, DashboardParAgence |
| `/ws` | STOMP endpoint |
| **— RH 6.1 —** | |
| `/api/gestion-personnel/employes` | DossierEmployeController (source de vérité RH depuis 2026-04) |
| `/api/contrats` | ContratController (multipart, fichier PDF inline) |
| `/api/gestion-personnel/periodes-essai` | PeriodeEssaiController (sur PeriodeEssai, source de vérité depuis 2026-04-29) |
| `/api/gestion-personnel/documents` | DocumentEmployeController (pièces administratives génériques + workflow validation) |
| `/api/organigramme` | OrganigrammeController (legacy, sur EmployeComplet) |
| `/api/rh-employes`, `/api/employes` | RhEmployeController (legacy, sur EmployeComplet) |
| **— RH 6.2 —** | |
| `/api/pointage-centralise` | PointageCentraliseController |
| `/api/rh-absences`, `/api/conges` | RhAbsenceController, DemandeCongeController |
| `/api/heures-supplementaires` | HeureSupplementaireController |
| `/api/recapitulatif-mensuel` | RecapitulatifMensuelController |
| **— RH 6.3 —** | |
| `/api/grille-salariale` | GrilleSalarialeController |
| `/api/bulletins-paie` | BulletinPaieController |
| `/api/declarations-sociales` | DeclarationSocialeController |
| `/api/parametres-paie` | ParametresPaieController |
| **— RH 6.4 —** | |
| `/api/formations` | FormationController |
| `/api/evaluations` | EvaluationPeriodiqueController |
| `/api/sanctions` | SanctionController |
| `/api/besoins-formation` | BesoinFormationController |
| `/api/tableau-bord-rh` | TableauBordRhController |
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

- **Organisation du code** : depuis 2026-06, tout le code RH est rangé sous des sous-packages `rh` (comme `terrain` / `productionchimie`) : `controllers/rh`, `services/rh`, `repositories/rh`, `entities/rh`, `Dto/rh`, `Mapper/rh`, `Enum/rh`. Les classes **partagées** restent à la racine de leur couche car consommées hors RH : entités `EmployeComplet`, `Employe`, `Pointage`, `Absent` ; services `EmployeCompletService`, `AbsentService` ; mappers `DateMapper`, `EmployeCompletMapper`, `EmployeMapper` ; enum `DecisionControle` (production chimie). Les services RH legacy (`OrganigrammeService`, `RhEmployeService`, `PointageCentraliseService`, `RecapitulatifMensuelService`, `CalculPaieService`) sont dans `services/rh` mais importent ces classes partagées restées à la racine.
- **Source de vérité** : `DossierEmploye` (collection `dossiers_employes`) depuis 2026-04 — tous les services RH valident et dénormalisent depuis `DossierEmployeRepository`.
- **Exceptions legacy maintenues** : `PointageCentraliseService`, `RecapitulatifMensuelService`, `CalculPaieService` utilisent encore `EmployeComplet` car ils joignent avec `Pointage` via `agentId`/`codeSecret` et lisent `heureDebut` pour le calcul des retards.
- **Tableau de bord RH** : `TableauBordRhService` agrège en parallèle via `CompletableFuture.supplyAsync` (pas de `$lookup` — pas de clé de jointure commune entre collections).

Détails endpoints, workflows (PeriodeEssai, DocumentEmploye, BulletinPaie, EvaluationPeriodique, Sanction), validations métier, formules de calcul de paie : **voir `.claude/docs/module-rh.md`**.

## Module Production Chimie (5.1) — ✅ Backend terminé

Module **autonome** (collections préfixées `production_chimie_`), ne partage aucune collection avec le module Stock historique. 9 contrôleurs REST sous `/api/production-chimie/`.

Patterns de référence introduits par ce module, **réutilisables ailleurs** :

- **Génération atomique de séquences** : `MongoTemplate.findAndModify(... $inc compteur ... upsert=true, returnNew=true)` avec un document `{_id: "AAAAMMJJ", compteur: long}` par jour. Test concurrent `CompteurLotServiceIT` (100 threads → 100 numéros distincts). Réutiliser ce pattern pour toute nouvelle séquence.
- **Compensation manuelle des opérations multi-document** : MongoDB en standalone (pas de transactions multi-doc). Le service applique manuellement les inverses sur échec à mi-parcours (voir `OrdreFabricationService.lancer` / `annuler`). Vérifier d'abord la faisabilité (ex : `quantiteEnStock ≥ quantiteTheorique` avant toute écriture) pour éviter le cas commun.
- **Versioning par snapshot** : `FormulationService` snapshote la version courante dans `versions[]` avant chaque PUT, incrémente `versionCourante`. La version courante n'apparaît jamais dans `versions[]` (elle y entre au prochain PUT).
- **Stockage binaire inline** : pas de GridFS ni S3 — `byte[]` en `@JsonIgnore` dans le document Mongo, URL calculée par le mapper vers un endpoint de streaming dédié.

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
- **TODO** : sous-permissions `ModulesAutorises.terrain.*` à ajouter dans `entities/GestionModules/SousModules/` (gating frontend) ; tests d'intégration.

Détails endpoints, payloads WebSocket, hypothèses (seuils escalade/maintenance, identité utilisateur courant), codes d'erreur : **voir `.claude/docs/module-terrain.md`**.
