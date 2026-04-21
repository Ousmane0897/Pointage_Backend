# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Spring Boot 3.3.6 backend (Java 21, Gradle) for **Pointage Cleanic** — an employee time-tracking / attendance SaaS. Persistence is MongoDB. The app is multi-module (dashboard, planning, pointages, absences, agences/sites, employés, stock, collecte besoins, utilisateurs/auth) and is consumed by an Angular frontend plus a mobile client used for on-site check-ins.

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

- **Auth is stateless JWT.** `security/SecurityConfig.java` disables CSRF, sets `SessionCreationPolicy.STATELESS`, and installs `JwtRequestFilter` before `UsernamePasswordAuthenticationFilter`. The filter skips `OPTIONS` (preflight) and rejects expired/invalid tokens with 401. Public routes are whitelisted there: `/api/login/**`, `/auth/forgot-password`, `/auth/reset-password/**`, Swagger, `/ws/**`, image endpoints (`/api/produits/image/**`, `/api/employe-complet/image/**`), and notably the entire `/pointages/**` + `/api/pointages/**` tree (mobile clocks in without a token). Everything else is `.authenticated()`. When adding a controller, decide explicitly whether to permit it here.

- **CORS allowlist is in `SecurityConfig`, not a properties file.** Frontend origins (`pointic-cleanic.com`, `app.pointic-cleanic.com`, ngrok subdomains, localhost) are hardcoded. Add new origins there.

- **Two parallel user models coexist.** `User` (collection used by `LoginRepository` + `DataLoader` bootstrap superadmin `diarra.niang@cleanicsenegal.com`) and `Utilisateur` (richer admin entity with `RoleAdmin`, `ModulesAutorises`, activation flags). `MyUserDetailsService` bridges them for Spring Security. Don't collapse them without understanding which flows use which.

- **Module-based authorization.** `Utilisateur.modulesAutorises: ModulesAutorises` is a per-user feature-flag object (booleans for top-level modules + nested sub-module objects under `entities/GestionModules/SousModules/`). Route-level authorization in `SecurityConfig` is coarse (`.authenticated()`); fine-grained gating is enforced in service/controller logic against this object.

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

## Testing conventions

- Controller tests use `@WebMvcTest(XxxController.class)` with `@AutoConfigureMockMvc(addFilters = false)` — security is disabled in slice tests, so `JwtRequestFilter`, `JwtUtil`, and `MyUserDetailsService` must still be declared as `@MockBean` (the Spring context loads them). Copy this pattern from `EmployesControllerTest`.
- Integration tests that need a real Mongo extend `MongoTestContainer` (Testcontainers `mongo:7.0`, container reused across tests). `configurations/AbstractMongoTest.java` is the main-source counterpart.
- Test profile (`application-test.yml`) has mail disabled (`spring.mail.host: disabled`) — don't wire `JavaMailSender` into tests that load the full context without mocking it.

## CI

`.github/workflows/backend.yml` runs on push to `main` only: JDK 21 setup → `./gradlew clean build -x test` → `./gradlew test`. Docker push and VPS deploy steps are commented out. The `develop` branch (current working branch) is not covered by CI.

## Module RH — Backend à créer

Le frontend Angular du module RH est terminé dans un repo séparé.
Le backend doit exposer les API REST que le frontend consomme déjà.

Les services Angular appellent `http://localhost:8080/api/...` via AuthInterceptor (JWT).
Chaque endpoint doit être sécurisé avec Spring Security + JWT,
cohérent avec l'authentification existante du projet.

Base de données : MongoDB
ORM : Spring Data MongoDB

### Structure attendue pour chaque sous-module RH

Respecter les patterns existants du projet. Pour chaque fonctionnalité :
- Entity (document MongoDB) dans le package `entities/` ou `models/`
- Repository (MongoRepository) dans `repositories/`
- Service dans `services/`
- Controller REST dans `controllers/`
- DTOs si le projet en utilise

### 6.1 Gestion du personnel — Endpoints attendus par le frontend

- Dossier employé : CRUD `/api/employes`
- Contrats : CRUD `/api/contrats`, GET `/api/contrats/alertes-echeance`
- Organigramme : GET `/api/organigramme?departement=`, GET `/api/organigramme/arbre`
- Période d'essai : GET `/api/periodes-essai/alertes`, PUT `/api/periodes-essai/{id}/titulariser`
- Documents employé : CRUD `/api/employes/{id}/documents`, POST upload fichier

### 6.2 Temps & Présences — Endpoints attendus par le frontend

- Pointage centralisé : GET `/api/pointages?date=&departement=&statut=`
- Absences : CRUD `/api/absences`, POST `/api/absences/{id}/justificatif` (upload)
- Congés : CRUD `/api/conges`, PUT `/api/conges/{id}/approuver`, PUT `/api/conges/{id}/refuser`,
  GET `/api/conges/solde/{employeId}`
- Heures supplémentaires : CRUD `/api/heures-supplementaires`,
  PUT `/api/heures-supplementaires/{id}/valider`
- Récapitulatif mensuel : GET `/api/recapitulatif-mensuel?mois=&annee=&departement=`,
  GET `/api/recapitulatif-mensuel/export/excel`, GET `/api/recapitulatif-mensuel/export/pdf`

### 6.3 Paie — Endpoints attendus par le frontend

- Grille salariale : CRUD `/api/grille-salariale`
- Bulletin de paie : POST `/api/bulletins-paie/calculer`, CRUD `/api/bulletins-paie`
- Génération PDF : GET `/api/bulletins-paie/{id}/pdf`
- Historique : GET `/api/bulletins-paie/historique?employeId=&annee=`
- Déclarations sociales : GET `/api/declarations-sociales/ipres?periode=`,
  GET `/api/declarations-sociales/css?periode=`,
  GET `/api/declarations-sociales/export/pdf`, GET `/api/declarations-sociales/export/excel`

### 6.4 Développement RH — Endpoints attendus par le frontend

- Formations : CRUD `/api/formations`, CRUD `/api/formations/{id}/sessions`,
  POST `/api/formations/{id}/sessions/{sessionId}/participants`,
  POST `/api/formations/{id}/evaluations`
- Évaluations périodiques : CRUD `/api/evaluations`,
  PUT `/api/evaluations/{id}/auto-evaluer`, PUT `/api/evaluations/{id}/evaluer-manager`,
  PUT `/api/evaluations/{id}/valider`
- Sanctions : CRUD `/api/sanctions`, GET `/api/sanctions/historique/{employeId}`
- Tableau de bord RH : GET `/api/tableau-bord-rh?periode=&departement=`
  (agrège les KPIs depuis les autres collections)

### Conventions

- Montants en FCFA stockés en long (pas de décimales)
- Dates stockées en ISO 8601, converties en dd/MM/yyyy côté frontend
- Taux de cotisation (IPRES, CSS, IR) dans un fichier de configuration
  ou une collection MongoDB dédiée, pas en dur dans le code
- Réponses API en JSON, pagination avec `page` et `size` pour les listes







