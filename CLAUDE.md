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
| **— RH 6.1 —** | |
| `/api/contrats`, `/api/periodes-essai` | ContratController, PeriodeEssaiController |
| `/api/organigramme` | OrganigrammeController |
| `/api/rh-employes` | RhEmployeController |
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

## Testing conventions

- Controller tests use `@WebMvcTest(XxxController.class)` with `@AutoConfigureMockMvc(addFilters = false)` — security is disabled in slice tests, so `JwtRequestFilter`, `JwtUtil`, and `MyUserDetailsService` must still be declared as `@MockBean` (the Spring context loads them). Copy this pattern from `EmployesControllerTest`.
- Integration tests that need a real Mongo extend `MongoTestContainer` (Testcontainers `mongo:7.0`, container reused across tests). `configurations/AbstractMongoTest.java` is the main-source counterpart.
- Test profile (`application-test.yml`) has mail disabled (`spring.mail.host: disabled`) — don't wire `JavaMailSender` into tests that load the full context without mocking it.

## CI

`.github/workflows/backend.yml` runs on push to `main` only: JDK 21 setup → `./gradlew clean build -x test` → `./gradlew test`. Docker push and VPS deploy steps are commented out. The `develop` branch (current working branch) is not covered by CI.

## Module RH — ✅ Backend terminé

Les 4 sous-modules RH sont livrés. Le frontend Angular (repo séparé) consomme directement les API REST exposées ici, authentifiées par JWT (`AuthInterceptor`) et soumises au fallback `.anyRequest().authenticated()` de `SecurityConfig`.

**Résumé quantitatif** : 4 sous-modules × ~10 entités MongoDB × ~5 controllers × ~5 services chacun, plus un tableau de bord RH qui agrège les 4 sources.

### Résumé global du backend RH

#### Collections MongoDB

| Sous-module | Collection(s) |
| --- | --- |
| 6.1 Personnel | `employes`, `employes_complet`, `contrats` |
| 6.2 Temps & Présences | `rh_absences`, `conges`, `heures_supplementaires` (réutilise aussi `pointages` pré-RH) |
| 6.3 Paie | `categories_professionnelles`, `bulletins_paie`, `declarations_sociales`, `parametres_paie` |
| 6.4 Développement RH | `formations`, `sessions_formation`, `participations_formation`, `evaluations_formation`, `besoins_formation`, `grilles_evaluation`, `evaluations_periodiques`, `sanctions` |

#### Services

| Sous-module | Services |
| --- | --- |
| 6.1 | `EmployeServices`, `EmployeCompletService`, `ContratService`, `OrganigrammeService`, `PeriodeEssaiService`, `RhEmployeService` |
| 6.2 | `PointageCentraliseService`, `RhAbsenceService`, `DemandeCongeService`, `HeureSupplementaireService`, `RecapitulatifMensuelService` |
| 6.3 | `CategorieProfessionnelleService`, `BulletinPaieService`, `CalculPaieService` (moteur de paie : IPRES/CSS/AT-MP/TRIMF/IR), `DeclarationSocialeService`, `ParametresPaieService` |
| 6.4 | `FormationService`, `EvaluationPeriodiqueService`, `SanctionService`, `BesoinFormationService`, `TableauBordRhService` |

#### Controllers

Voir la table « Controller URL map » ci-dessus, sections RH 6.1 à 6.4.

#### Points d'implémentation à connaître

- **`TableauBordRhService` agrège en parallèle** via `CompletableFuture.supplyAsync` plutôt qu'un `$lookup` géant — les collections sources n'ont pas de clé de jointure commune, chaque KPI reste testable isolément.
- **Source de vérité pour les présences/retards** : `PointageCentraliseService` (6.2) — parse `HH:mm` de `EmployeComplet.heureDebut` vs `Pointage.heureArrive` pour calculer `retardMinutes`. Ne pas réimplémenter ailleurs.
- **Département côté `EmployeComplet`** : utiliser `agence[0]` (tableau de sites/agences). Le champ `poste` n'est pas un département. Les collections RH plus récentes (`Sanction`, `RhAbsence`, `DemandeConge`, `EvaluationPeriodique`, `BulletinPaie`) portent un vrai `departement` dénormalisé.
- **Récidive disciplinaire** : `SanctionService.estRecidiviste(employeId, type, dateRef)` via `countByEmployeIdAndTypeAndDateSanctionBetween` sur 12 mois glissants, seuil ≥ 2. `alertesRecidive()` élargit à tous types confondus.
- **Workflow évaluation périodique** : `BROUILLON → AUTO_EVALUATION → EVALUATION_MANAGER → VALIDE`. La note globale est la moyenne pondérée `Σ(note×poids)/Σpoids` selon la `GrilleEvaluation` référencée ; mapping alphabétique A≥4.5, B≥3.5, C≥2.5, sinon D. À la validation, les `BesoinFormation` portés par la `ValidationEvaluationRequest` sont créés automatiquement (`source=EVALUATION`).
- **Procédure disciplinaire sénégalaise** : `Sanction.dureeMiseAPied` plafonnée à 8 jours (Code du Travail), délai de respect calculé automatiquement entre `dateConvocation` et `dateEntretien`.
- **Grille d'évaluation par défaut** : seedée au démarrage (`DataLoader`) avec 5 critères (Expertise technique 30, Autonomie 20, Communication 15, Travail en équipe 15, Atteinte des objectifs 20 — total 100).

### 6.1 Gestion du personnel — ✅ Terminé

Endpoints livrés :

- Dossier employé : CRUD `/api/employes`, `/api/employe-complet` (avec photo et contrat PDF en multipart)
- Contrats : CRUD `/api/contrats`, GET `/api/contrats/alertes-echeance`
- Organigramme : GET `/api/organigramme?departement=`, GET `/api/organigramme/arbre`
- Période d'essai : GET `/api/periodes-essai/alertes`, PUT `/api/periodes-essai/{id}/titulariser`
- Documents employé : upload du contrat PDF inline (`POST /api/employe-complet/employe` part `contrat`, GET/DELETE `/api/employe-complet/{id}/contrat`)

### 6.2 Temps & Présences — ✅ Terminé

- Pointage centralisé : GET `/api/pointage-centralise?date=&departement=&site=&statut=&q=&page=&size=`, GET `/api/pointage-centralise/resume?date=`
- Absences : CRUD `/api/rh-absences`, POST `/api/rh-absences/{id}/justificatif` (upload)
- Congés : CRUD `/api/conges`, PUT `/api/conges/{id}/approuver`, PUT `/api/conges/{id}/refuser`, GET `/api/conges/solde/{employeId}`
- Heures supplémentaires : CRUD `/api/heures-supplementaires`, PUT `/api/heures-supplementaires/{id}/valider`
- Récapitulatif mensuel : GET `/api/recapitulatif-mensuel?mois=&annee=&departement=`, exports Excel/PDF

### 6.3 Paie — ✅ Terminé

- Grille salariale (catégories professionnelles) : CRUD `/api/grille-salariale`
- Bulletin de paie : POST `/api/bulletins-paie/calculer`, CRUD `/api/bulletins-paie`, workflow `/valider` `/payer` `/annuler`, GET `/{id}/pdf`, GET `/historique?employeId=&annee=`
- Déclarations sociales : GET `/api/declarations-sociales/ipres?periode=`, GET `/api/declarations-sociales/css?periode=`, exports PDF/Excel, PUT `/{id}/transmettre`
- Paramètres de paie : GET/PUT `/api/parametres-paie` (taux IPRES/CSS/AT-MP/TRIMF et barème IR — stockés en Mongo, pas en dur)

### 6.4 Développement RH — ✅ Terminé

Endpoints livrés :

- **Formations** : CRUD `/api/formations`, CRUD `/api/formations/{id}/sessions` et `/api/formations/sessions/{sessionId}`, GET/POST `/api/formations/sessions/{sessionId}/participants`, PUT `/api/formations/participations/{id}/presence` et `/completion`, GET/POST `/api/formations/sessions/{sessionId}/evaluations` (évaluations à chaud).
- **Évaluations périodiques** : CRUD `/api/evaluations`, workflow `PUT /{id}/auto-evaluer` `PUT /{id}/evaluer-manager` `PUT /{id}/valider`.
- **Sanctions** : CRUD `/api/sanctions`, GET `/api/sanctions/historique/{employeId}`, GET `/api/sanctions/alertes-recidive`, PUT `/api/sanctions/{id}/statut`.
- **Besoins de formation** : CRUD `/api/besoins-formation`, GET `/api/besoins-formation/employe/{employeId}`, PUT `/api/besoins-formation/{id}/statut`.
- **Tableau de bord RH** : GET `/api/tableau-bord-rh?dateDebut=&dateFin=&departement=&site=` (agrège effectif + turnover + absentéisme + retards moyens + solde congés + masse salariale + formations + sanctions).

Déviations par rapport à la spec initiale du frontend :

- `/api/formations/{id}/evaluations` → `/api/formations/sessions/{sessionId}/evaluations` (sessionId required dans le TS).
- `/api/tableau-bord-rh?periode=&departement=` → `?dateDebut=&dateFin=&departement=&site=` (conforme à `FiltreTableauBord` du frontend).
- Ajout de `/api/besoins-formation` (non listé dans la spec initiale, nécessaire pour l'écran besoins).

### Conventions

- Montants en FCFA stockés en long (pas de décimales)
- Dates stockées en ISO 8601, converties en dd/MM/yyyy côté frontend
- Taux de cotisation (IPRES, CSS, IR) dans un fichier de configuration
  ou une collection MongoDB dédiée, pas en dur dans le code
- Réponses API en JSON, pagination avec `page` et `size` pour les listes
