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

## Module RH — ✅ Backend terminé

Les 4 sous-modules RH sont livrés. Le frontend Angular (repo séparé) consomme directement les API REST exposées ici, authentifiées par JWT (`AuthInterceptor`) et soumises au fallback `.anyRequest().authenticated()` de `SecurityConfig`.

**Résumé quantitatif** : 4 sous-modules × ~10 entités MongoDB × ~5 controllers × ~5 services chacun, plus un tableau de bord RH qui agrège les 4 sources.

### Résumé global du backend RH

#### Collections MongoDB

| Sous-module | Collection(s) |
| --- | --- |
| 6.1 Personnel | `dossiers_employes` (source de vérité RH), `contrats`, `periodes_essai`, `demandes_validation_periode_essai`, `documents_employes`. `employes` + `employes_complet` restent pour le pointage mobile/legacy. |
| 6.2 Temps & Présences | `rh_absences`, `conges`, `heures_supplementaires` (réutilise aussi `pointages` pré-RH) |
| 6.3 Paie | `categories_professionnelles`, `bulletins_paie`, `declarations_sociales`, `parametres_paie` |
| 6.4 Développement RH | `formations`, `sessions_formation`, `participations_formation`, `evaluations_formation`, `besoins_formation`, `grilles_evaluation`, `evaluations_periodiques`, `sanctions` |

#### Services

| Sous-module | Services |
| --- | --- |
| 6.1 | `DossierEmployeService` (CRUD RH, source de vérité), `ContratService` (multipart + fichier PDF), `PeriodeEssaiService` + `DemandeValidationPeriodeEssaiService` (workflow Manager → RH → Confirmation), `DocumentEmployeService` (pièces administratives + workflow validation). `EmployeServices`, `EmployeCompletService`, `OrganigrammeService`, `RhEmployeService` subsistent pour le pointage mobile / legacy. |
| 6.2 | `PointageCentraliseService`, `RhAbsenceService`, `DemandeCongeService`, `HeureSupplementaireService`, `RecapitulatifMensuelService` |
| 6.3 | `CategorieProfessionnelleService`, `BulletinPaieService`, `CalculPaieService` (moteur de paie : IPRES/CSS/AT-MP/TRIMF/IR), `DeclarationSocialeService`, `ParametresPaieService` |
| 6.4 | `FormationService`, `EvaluationPeriodiqueService`, `SanctionService`, `BesoinFormationService`, `TableauBordRhService` |

#### Controllers

Voir la table « Controller URL map » ci-dessus, sections RH 6.1 à 6.4.

#### Points d'implémentation à connaître

- **Principe d'indépendance du module RH** (depuis 2026-04) : les services RH — `ContratService`, `RhAbsenceService`, `DemandeCongeService`, `HeureSupplementaireService`, `BesoinFormationService`, `SanctionService`, `EvaluationPeriodiqueService`, `FormationService` (addParticipant), `TableauBordRhService` (KPIs effectif/turnover/répartitions) — valident et dénormalisent depuis `DossierEmployeRepository` ; ne plus les brancher sur `EmployeCompletRepository`. **Exceptions maintenues** : `PointageCentraliseService`, `RecapitulatifMensuelService` et `CalculPaieService` utilisent encore `EmployeComplet` car ils font la jointure avec `Pointage` via `agentId`/`codeSecret` et lisent `heureDebut` pour le calcul des retards.
- **`TableauBordRhService` agrège en parallèle** via `CompletableFuture.supplyAsync` plutôt qu'un `$lookup` géant — les collections sources n'ont pas de clé de jointure commune, chaque KPI reste testable isolément. `calculerPersonnel` lit `DossierEmploye` ; `calculerTempsPresence` passe par `PointageCentraliseService`.
- **Source de vérité pour les présences/retards** : `PointageCentraliseService` (6.2) — parse `HH:mm` de `EmployeComplet.heureDebut` vs `Pointage.heureArrive` pour calculer `retardMinutes`. Ne pas réimplémenter ailleurs.
- **Département** : sur `DossierEmploye` c'est un champ de 1er niveau. Côté `EmployeComplet` legacy on utilise `agence[0]`. Les collections RH récentes (`Sanction`, `RhAbsence`, `DemandeConge`, `EvaluationPeriodique`, `BulletinPaie`) portent un vrai `departement` dénormalisé.
- **Récidive disciplinaire** : `SanctionService.estRecidiviste(employeId, type, dateRef)` via `countByEmployeIdAndTypeAndDateSanctionBetween` sur 12 mois glissants, seuil ≥ 2. `alertesRecidive()` élargit à tous types confondus.
- **Workflow évaluation périodique** : `BROUILLON → AUTO_EVALUATION → EVALUATION_MANAGER → VALIDE`. La note globale est la moyenne pondérée `Σ(note×poids)/Σpoids` selon la `GrilleEvaluation` référencée ; mapping alphabétique A≥4.5, B≥3.5, C≥2.5, sinon D. À la validation, les `BesoinFormation` portés par la `ValidationEvaluationRequest` sont créés automatiquement (`source=EVALUATION`).
- **Procédure disciplinaire sénégalaise** : `Sanction.dureeMiseAPied` plafonnée à 8 jours (Code du Travail), délai de respect calculé automatiquement entre `dateConvocation` et `dateEntretien`.
- **Grille d'évaluation par défaut** : seedée au démarrage (`DataLoader`) avec 5 critères (Expertise technique 30, Autonomie 20, Communication 15, Travail en équipe 15, Atteinte des objectifs 20 — total 100).

### 6.1 Gestion du personnel — ✅ Terminé

Endpoints livrés :

- **Dossier employé RH (nouvelle source de vérité, depuis 2026-04)** :
  CRUD multipart `/api/gestion-personnel/employes` (part JSON `dossier` + `photo` optionnelle).
  Champs : matricule (unique), identité (nom, prénom, genre HOMME/FEMME, dateNaissance,
  nationalité, numeroIdentification, situationMatrimoniale CELIBATAIRE/MARIE,
  nombreEnfants conditionnel), poste, département, siteAffecte, dateEntrée,
  statut `ACTIF | EN_PERIODE_ESSAI | SUSPENDU | SORTI`, superieurHierarchiqueId/Nom,
  dureeEssaiMois (stocké uniquement si statut=EN_PERIODE_ESSAI, remis à null à la
  titularisation), contacts (tél, email, adresse), contactUrgence (sous-doc
  {nom, lienParente, telephone}), photo (byte[]). Champs paie techniques
  (categorieCode, numeroIpres, numeroCss, rib, banque) également portés sur l'entité.
  Endpoints additionnels : `GET /{id}/photo` (permitAll), `PUT /{id}/statut`,
  `PUT /{id}/titulariser`, `GET /alertes-essai`.
- **Import bulk de dossiers employés** : `POST /api/gestion-personnel/employes/bulk`
  (JSON pur, sans photos — le CRUD unitaire reste utilisé ensuite pour les photos).
  Remplace la boucle d'appels unitaires que faisait le frontend lors d'un import Excel.
  Body : `{ employes: DossierEmployeBulkLigneDto[], strategieErreurs?: "TOUT_OU_RIEN"
  | "IMPORTER_LIGNES_VALIDES" }`. Le champ `strategieErreurs` est **optionnel**
  depuis 2026-04 : si absent ou null, le défaut **TOUT_OU_RIEN** est appliqué par
  le record `DossierEmployeBulkImportRequest` (rétro-compatibilité frontend).
  Réponse : `{ total, inserted, failed, insertedIds, errors: [{ index, matricule,
  field, code, message }] }`. Codes : **200** tout inséré, **207** partiel
  (IMPORTER_LIGNES_VALIDES avec erreurs), **422** TOUT_OU_RIEN avec ≥ 1 erreur
  (aucune insertion), **400** payload invalide (vide / trop grand). Limite
  configurable `rh.import.bulk.max-size: 1000`.
  Codes d'erreur métier : `CHAMP_OBLIGATOIRE`, `VALEUR_INVALIDE`,
  `MATRICULE_DUPLIQUE_PAYLOAD`, `MATRICULE_DUPLIQUE_BASE`, `SUPERIEUR_INEXISTANT`,
  `VALIDATION_CONDITIONNELLE`, `REFERENCE_CIRCULAIRE` (cycle de supérieurs détecté
  par DFS 3-couleurs sur le graphe intra-payload). Le supérieur hiérarchique peut
  être référencé via `superieurHierarchiqueMatricule` (champ spécifique au DTO bulk) :
  résolu en deux passes — soit dans le payload lui-même (pass B d'`updateFirst`
  post-`saveAll()`), soit en base (pass A).
  **Atomicité** : MongoDB en standalone ne supportant pas les transactions multi-
  documents, l'atomicité `TOUT_OU_RIEN` est garantie par validation préalable
  complète du batch avant tout `saveAll()`. Si `saveAll()` échoue à mi-parcours
  (race condition sur l'index unique `matricule`, erreur I/O), une
  `BulkInsertPartialFailureException` est levée → HTTP 500 avec body
  `{error: "BULK_INSERT_PARTIAL_FAILURE", message, idsDejaInseres: [...]}` ;
  aucun rollback compensatoire n'est tenté.
  **RBAC fine-grained sur l'import bulk à traiter dans une PR dédiée** — pour
  l'instant l'endpoint tombe sous l'authentification JWT standard
  (`.anyRequest().authenticated()` dans `SecurityConfig`).
  Validation plus stricte qu'au CRUD unitaire sur un point : `situationMatrimoniale
  = MARIE` exige `nombreEnfants` non null dans le bulk (alors que `create()` se
  contente de nullifier `nombreEnfants` hors MARIE).
- **Contrats** : CRUD multipart `/api/contrats` (part JSON `contrat` + `fichier` optionnel).
  `Contrat.fichierContrat` (byte[]) + `fichierContratNom` + `fichierContratMimeType`.
  Nouveaux endpoints `GET /api/contrats/{id}/fichier` (télécharge le PDF),
  `DELETE /api/contrats/{id}/fichier`. GET `/api/contrats/alertes`.
  `ContratService` valide désormais l'employé via `DossierEmployeRepository`.
  `TypeContratRh` : **ALTERNANCE remplacé par PRESTATION** ({CDI, CDD, STAGE, PRESTATION}).
  `ContratAlternanceMigrationRunner` (CommandLineRunner, `@Order(1000)`) migre
  les documents existants au démarrage via `mongoTemplate.updateMulti` — idempotent.
  Champ optionnel `dureeEssaiMois` (Integer, **convention RH en mois**) sur
  `Contrat` / `ContratDto` : pris en compte s'il est > 0 (override explicite).
  **Le modèle Angular `Contrat` n'expose pas ce champ pour l'instant** : à
  défaut, `ContratService.resoudreDureeEssaiMois` dérive la durée depuis
  `DossierEmploye.dureeEssaiMois` quand l'employé est en `EN_PERIODE_ESSAI`.
  Si une durée résolue > 0 est trouvée,
  `PeriodeEssaiService.seedFromContrat(contrat, mois)` crée automatiquement
  une `PeriodeEssai` (statut EN_COURS, alertes par défaut à 30 / 15 / 7
  jours). La conversion mois → jours pour `PeriodeEssai.dureeJours` se fait
  via `dateDebut.plusMonths(mois)` (calendaire-correct, p.ex. 3 mois ≠ 90
  jours fixes selon les mois traversés).
- **Période d'essai (depuis 2026-04-29)** : nouvelle source de vérité dans la
  collection `periodes_essai` (entité `PeriodeEssai`, **liée à un Contrat** via
  `contratId`). 7 endpoints sous `/api/gestion-personnel/periodes-essai` (consommés
  directement par le frontend `PeriodeEssaiService` Angular) :
  - `GET /` (paginé, filtre `statut`), `GET /{id}`,
    `PUT /{id}/prolonger {nouvelleDateFin, commentaire}` →
    statut `PROLONGE`, recalcul des alertes, append d'une `DecisionPeriodeEssai`.
  - `GET /alertes` : retourne les périodes EN_COURS / PROLONGE dont au moins une
    alerte non envoyée a une `dateAlerte ≤ today`.
  - `GET /validations?statut=`, `POST /{periodeEssaiId}/validations {commentaire}`,
    `PUT /validations/{demandeId} {decision, commentaire}` —
    workflow `EN_ATTENTE_MANAGER → VALIDEE_MANAGER → VALIDEE_RH → CONFIRMEE`,
    ou `REFUSEE` à n'importe quelle étape (collection
    `demandes_validation_periode_essai`). `ActionValidation` accepté :
    `VALIDER`, `CONFIRMER`, `REFUSER`. Transitions illégales → 400
    `VALIDATION_ERROR`. Doublon de demande active sur la même période → 409
    `DEMANDE_VALIDATION_CONFLICT` (nouvelle exception
    `DemandeValidationConflictException`).
    À l'étape `CONFIRMEE`, `applyTitularisation` bascule
    `PeriodeEssai.statut → TITULARISE` (avec `DecisionPeriodeEssai` de trace) et
    `DossierEmploye.statut → ACTIF`, `dureeEssaiMois → null`.
  - **Pas d'endpoint POST direct sur `periodes-essai`** : la création est
    déclenchée uniquement à la création d'un `Contrat` portant
    `dureeEssaiJours > 0` (rétrocompat : un contrat sans ce champ ne crée pas de
    période). Pas de seed rétroactif sur les contrats existants.
  - `DossierEmploye.dureeEssaiMois` reste lisible en **lecture-only legacy** ;
    la source de vérité pour la durée et la date de fin est désormais
    `PeriodeEssai`. L'endpoint
    `GET /api/gestion-personnel/employes/alertes-essai` (sur `DossierEmploye`)
    subsiste pour rétrocompat — préférer
    `GET /api/gestion-personnel/periodes-essai/alertes` côté frontend.
- **Documents employé (depuis 2026-04-29)** : nouvelle collection
  `documents_employes` portant toutes les pièces administratives génériques
  (CNI, diplôme, certificat, attestation, contrat scanné, autre) attachées à
  un employé, en complément des cas spécifiques déjà gérés (contrat PDF dans
  `Contrat`, photo dans `DossierEmploye`, justificatif dans `RhAbsence`).
  Endpoints `/api/gestion-personnel/documents` (consommés par
  `DocumentEmployeService` Angular) :
  - `GET /` paginé avec filtres `employeId` et `categorie`,
    `GET /employe/{employeId}` (raccourci non paginé),
    `GET /{id}`, `GET /{id}/telecharger` (flux binaire,
    `Content-Disposition: attachment`).
  - `POST /` multipart (`document` JSON + `fichier`) : 404 si employé
    inexistant, 400 si `nom`/`categorie`/`fichier` manquant. À l'upload,
    snapshot `employeNom/Prenom` depuis `DossierEmploye`,
    `dateUpload = now()`, `statut = EN_ATTENTE`. Limite multipart globale
    10MB (`spring.servlet.multipart.max-file-size`).
  - `PUT /{id}` JSON (métadonnées only) : `nom`, `categorie`,
    `dateExpiration`, `commentaire` modifiables ; `statut` et `fichier`
    NE SONT PAS éditables ici (statut → `/valider`, fichier → DELETE +
    re-POST).
  - `PUT /{id}/valider` body `{statut: VALIDE|REFUSE, commentaire?}` :
    workflow d'approbation RH. La **révision est autorisée** (passage
    VALIDE ↔ REFUSE possible) tant que le document n'est pas EXPIRE,
    pour permettre au RH de corriger une décision erronée.
  - `DELETE /{id}` supprime le document (et son `byte[]`).
  - **Statut `EXPIRE` dérivé à la lecture, jamais persisté** : si
    `dateExpiration < today` ET statut stocké ∈ {VALIDE, EN_ATTENTE},
    le DTO renvoie `EXPIRE`. `REFUSE + dateExpiration passée` reste
    `REFUSE` (état terminal). Logique implémentée dans
    `DocumentEmployeService.deriverStatutAffiche`.
  - Catégories : `CNI, DIPLOME, CERTIFICAT, ATTESTATION, CONTRAT, AUTRE`
    (enum `CategorieDocument`).
- **Legacy / coexistence** : CRUD `/api/employe-complet` (EmployeComplet, photo + contrat PDF),
  `/api/employes` et `/api/rh-employes` (RhEmployeController sur EmployeComplet),
  `/api/organigramme` reste en fonction pour le pointage mobile et les flux
  legacy. **`/api/periodes-essai` (ancien `PeriodeEssaiController` sur
  `EmployeComplet`) a été supprimé** au profit de
  `/api/gestion-personnel/periodes-essai`.

### 6.2 Temps & Présences — ✅ Terminé

- Pointage centralisé : GET `/api/pointage-centralise?date=&departement=&site=&statut=&q=&page=&size=`, GET `/api/pointage-centralise/resume?date=`
- **Absences** : CRUD `/api/rh-absences`, POST `/api/rh-absences/{id}/justificatif` (upload).
  Champ `typeAutrePrecision` obligatoire quand `type=AUTRE` (validation dans
  `RhAbsenceService.validerCoherenceType` → 400 `VALIDATION_ERROR` via le nouveau
  handler `IllegalArgumentException`). Nettoyage à null si `type != AUTRE` pour
  éviter les résidus incohérents. Validation employé via `DossierEmployeRepository`.
- Congés : CRUD `/api/conges`, PUT `/api/conges/{id}/approuver`, PUT `/api/conges/{id}/refuser`, GET `/api/conges/solde/{employeId}`. Validation employé via `DossierEmployeRepository`.
- Heures supplémentaires : CRUD `/api/heures-supplementaires`, PUT `/api/heures-supplementaires/{id}/valider`. Validation employé via `DossierEmployeRepository`.
- Récapitulatif mensuel : GET `/api/recapitulatif-mensuel?mois=&annee=&departement=`, exports Excel/PDF. Reste sur `EmployeComplet` (jointure Pointage).

### 6.3 Paie — ✅ Terminé

- **Grille salariale** (catégories professionnelles) : CRUD `/api/grille-salariale`.
  `CategorieProfessionnelle` porte désormais 3 listes embedded supplémentaires
  (spec frontend 2026-04) : `prets` (`{libelle, montant, dureeMois}`),
  `avancesSurSalaire` (idem), `retenues` (`{libelle, montant}` sans durée).
  Ces rubriques sont des retenues personnelles qui **diminuent le net POST-cotisations**
  — elles n'impactent **pas** les assiettes IPRES/CSS/IR (restent sur le brut).
- **Bulletin de paie** : POST `/api/bulletins-paie/calculer`, CRUD `/api/bulletins-paie`,
  workflow `/valider` `/payer` `/annuler`, GET `/{id}/pdf`, GET `/historique?employeId=&annee=`.
  `CalculPaieService` expose 3 méthodes publiques testables :
  `calculerTotalPrets(List<PretCategorie>)`, `calculerTotalAvances(...)`,
  `calculerTotalRetenues(...)`. Formule du net :
  `netAPayer = brut - totalCotisSalariales - IR - TRIMF - totalPrets - totalAvances - totalRetenues`.
  `BulletinPaie` snapshot les rubriques appliquées (`pretsAppliques`,
  `avancesAppliquees`, `retenuesAppliquees`, `totalPrets/Avances/Retenues`) pour
  traçabilité — une modification ultérieure de la grille n'affecte pas les bulletins
  déjà générés. Le PDF (OpenPDF) affiche une **section conditionnelle « Retenues
  personnelles »** uniquement si au moins une rubrique est présente.
- Déclarations sociales : GET `/api/declarations-sociales/ipres?periode=`, GET `/api/declarations-sociales/css?periode=`, exports PDF/Excel, PUT `/{id}/transmettre`. **Aucun impact** des nouvelles retenues (agrégation somme `totalCotisationsSalariales` et `totalCotisationsPatronales` des bulletins, tous deux calculés sur le brut).
- Paramètres de paie : GET/PUT `/api/parametres-paie` (taux IPRES/CSS/AT-MP/TRIMF et barème IR — stockés en Mongo, pas en dur).

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
- **Gestion des erreurs de validation métier** : lever `IllegalArgumentException`
  avec un message explicite ; `GlobalExceptionHandler` renvoie
  `400 VALIDATION_ERROR` automatiquement. `ResourceNotFoundException` →
  404 NOT_FOUND. `EmployeAlreadyExistsException` → 409 CONFLICT.
  Module Production Chimie (5.1) : `StockChimieInsuffisantException` → 409
  `STOCK_CHIMIE_INSUFFISANT`, `TransitionOfInterditeException` → 409
  `TRANSITION_OF_INTERDITE`, `ControleQualiteInvalideException` → 400
  `CONTROLE_QUALITE_INVALIDE`, `EntiteReferenceeException` → 409
  `ENTITE_REFERENCEE`, `ProductionException` (fallback) → 409
  `PRODUCTION_ERROR`. **Bean Validation** (`@Valid`, `@NotBlank`, etc.) :
  `MethodArgumentNotValidException` → 400 `VALIDATION_ERROR` avec body
  `{error, message, errors: [{field, message}]}` — handler ajouté en même
  temps que le module 5.1, applicable à tous les nouveaux modules.
- **Multipart** : config globale `spring.servlet.multipart.max-file-size: 10MB`,
  `max-request-size: 15MB` (PDF de contrat peuvent dépasser le défaut 1MB Spring).

## Module Production Chimie (5.1) — ✅ Backend terminé

Backend du module **Exploitation v2 / Production Chimie** consommé par le front Angular sur `feature/exploitation-v2-production-chimie`. Module **autonome** : ne partage **aucune collection** avec le module Stock historique (les MP chimie vivent dans `production_chimie_matieres_premieres`, séparées de `produits`). Lecture seule possible sur le module RH si jamais on veut résoudre le nom d'un opérateur d'OF (non utilisé actuellement — le front envoie déjà `operateurResponsableNom` dénormalisé).

**Résumé quantitatif** : 9 contrôleurs REST sous `/api/production-chimie/`, 11 services métier, 10 repositories, 21 entités/sous-entités, 25 DTOs, 8 mappers MapStruct, 5 exceptions custom, 9 enums.

### Collections MongoDB (préfixées `production_chimie_`)

| Collection | Rôle | Index notables |
| --- | --- | --- |
| `production_chimie_matieres_premieres` | MP chimie (byte[] fiche sécurité inline) | `code` unique, `actif` |
| `production_chimie_mouvements_stock` | Trace ENTREE/SORTIE/AJUSTEMENT — `quantiteEnStock` dénormalisée sur la MP | composé `(matierePremiereId, date)`, `ordreFabricationId`, composé `(type, date)` |
| `production_chimie_formulations` | Fiches avec `versions[]` snapshots | `code` unique, `statut` |
| `production_chimie_ordres_fabrication` | OF avec `historiqueStatuts[]` + `consommationMp[]` | `numero` unique, `statut`, `dateLancementPrevue`, `operateurResponsableId` |
| `production_chimie_lots` | Générés au `/terminer` d'un OF | `numero` unique, `(produitNom, dateFabrication)`, `statutControle`, `statutStock`, `formulationId` |
| `production_chimie_grilles_controle` | Grilles CQ par produitNom ou formulationId | `produitNom`, `formulationId` |
| `production_chimie_controles_qualite` | Mesures + photos byte[] inline | `lotId`, `(produitNom, dateControle)`, `decision` |
| `production_chimie_formats_conditionnement` | Catalogue bidons/bouteilles | `code` unique, `actif` |
| `production_chimie_compteurs_of` | `_id: AAAAMMJJ`, `compteur: long` — atomicité génération | clé `_id` |
| `production_chimie_compteurs_lots` | idem pour les n° de lot | clé `_id` |

Les indexes sont créés explicitement au démarrage via `configurations/ProductionChimieIndexesConfig` (l'auto-index-creation de Spring Data Mongo n'est pas activée globalement).

### Services

| Domaine | Service | Particularités |
| --- | --- | --- |
| Stock chimie | `MatierePremiereService`, `MouvementStockChimieService` | Reception et ajustement compensent manuellement la cohérence `quantiteEnStock` (MongoDB standalone, pas de transactions) |
| Formats | `FormatConditionnementService` | CRUD simple, valide `uniteVolume ∈ {L, ML}` |
| Formulations | `FormulationService` | **Versioning** : snapshot avant chaque PUT, restauration de version, diff ingredients/etapes/dureePeremption |
| Ordres | `OrdreFabricationService` | **Transitions critiques** : EN_ATTENTE→EN_COURS (`/lancer`) crée N SORTIE + décrémente `quantiteEnStock` avec compensation manuelle si échec ; EN_COURS→TERMINE (`/terminer`) génère un Lot ; annulation depuis EN_COURS crée des ENTREE inverses |
| Lots | `LotService` | Agrégation `/{id}/tracabilite` reconstruit OF + version formulation utilisée + consommations |
| CQ | `GrilleControleService`, `ControleQualiteService` | Création contrôle **bascule le lot** (VALIDE → `statutControle=VALIDE`, `statutStock=EN_STOCK` ; REJET → `REJETE`, `BLOQUE`). REJET sans commentaire non vide → 400 |
| Tableau de bord | `TableauBordProductionService` | `/rapport` retourne KPIs + volumes/produit + évolution mensuelle (YYYY-MM) + rendements + répartition CQ + comparaison période précédente |
| Compteurs | `CompteurOfService`, `CompteurLotService` | `MongoTemplate.findAndModify(... $inc compteur ... upsert=true, returnNew=true)` — atomique sous concurrence, format `OF-AAAAMMJJ-XXX` / `AAAAMMJJ-XXX` |

### Points d'implémentation à connaître

- **Génération atomique des numéros (OF et lot)** : c'est le pattern de référence pour toute séquence atomique dans ce repo (avant ce module aucun mécanisme n'existait). Document `{_id: "AAAAMMJJ", compteur: long}`, incrémenté via `findAndModify(... new Update().inc("compteur", 1L), options().upsert(true).returnNew(true))`. Format final padding `%03d`. Le test d'intégration `CompteurLotServiceIT` valide 100 appels concurrents sur 20 threads → 100 numéros distincts. **Réutiliser ce pattern** plutôt que d'inventer autre chose si un nouveau module a besoin d'une séquence.

- **Compensation manuelle des opérations multi-document** : MongoDB est en standalone en prod (pas de transactions multi-doc). Le lancement d'un OF crée N SORTIE et décrémente N MP — si la 3ème échoue à mi-chemin, le service crée des ENTREE inverses pour les 2 premières puis renvoie `500 PRODUCTION_ERROR`. Annulation d'un OF EN_COURS : crée des ENTREE inverses pour chaque consommation persistée et réajuste `quantiteEnStock`. Même esprit que `BulkInsertPartialFailureException` du module RH.

- **Vérification préalable de disponibilité MP** : `/lancer` charge toutes les MP, vérifie `quantiteEnStock ≥ quantiteTheorique` pour chacune **avant** la moindre écriture. Si une seule manque → `409 STOCK_CHIMIE_INSUFFISANT`, **aucune** sortie n'est créée. Évite d'avoir à compenser dans le cas commun.

- **Transitions d'OF autorisées (strict)** :
  - `EN_ATTENTE → EN_COURS` (via `/lancer`)
  - `EN_COURS → TERMINE` (via `/terminer`)
  - `EN_ATTENTE | EN_COURS → ANNULE` (via `/annuler`, motif obligatoire)
  - Toute autre transition → `409 TRANSITION_OF_INTERDITE`.
  - Le PUT sur un OF n'est autorisé **que** si statut = `EN_ATTENTE`.

- **Versioning des fiches de formulation** : à chaque PUT, snapshot de la version courante (numéro courant + ingrédients/étapes/dureePeremption + `motif` venant du DTO + auteur depuis `SecurityContextHolder`) est pushé dans `versions[]`, **puis** les changements appliqués, **puis** `versionCourante++`. La version courante elle-même n'apparaît jamais dans `versions[]` (elle y entre au prochain PUT). Restauration vN : snapshot la courante + recopie ingrédients/étapes/dureePeremption depuis vN + incrémente. L'OF capture `formulationVersion` au moment de sa création — la durée de péremption pour le calcul `datePeremption` du lot est résolue depuis la version snapshot, jamais depuis la version courante.

- **Intégrité référentielle DELETE** : `DELETE /matieres-premieres/{id}` compte les formulations dont `ingredients.matierePremiereId` contient cet id → `409 ENTITE_REFERENCEE` si > 0. `DELETE /formulations/{id}` compte les OF référençant cette formulation → `409 ENTITE_REFERENCEE` si > 0.

- **Stockage des fichiers binaires** : pas de GridFS ni S3 — byte[] inline dans le document Mongo (pattern `DocumentEmployeService` du module RH). Fiche sécurité MP stockée sur `MatierePremiere.ficheSecurite` (`@JsonIgnore`), photo CQ stockée dans `ControleQualite.photos[i].contenu`. Les DTOs n'exposent pas le binaire — `ficheSecuriteUrl` / `photos[i].url` sont **calculées** par le mapper et pointent vers des endpoints de streaming dédiés (`GET .../fiche-securite`, `GET .../controles/{id}/photos/{index}`).

- **Module sidebar côté front** : `ModulesAutorises.productionChimie` ajouté (sous-objet `entities/GestionModules/SousModules/ProductionChimie` avec 7 booléens : `formulations, ordresFabrication, lots, controleQualite, matieresPremieres, conditionnement, tableauBord`). Le JWT le porte automatiquement dès que `Utilisateur.modulesAutorises` est peuplé côté admin. **Le sous-objet est null par défaut sur les users existants** — à synchroniser via l'écran admin si on veut que la sidebar Production Chimie s'affiche.

- **Pagination** : nouveau record générique `util/PageResponse<T>(List<T> content, long totalElements)` introduit pour ce module afin de masquer les champs internes Spring `Page<T>` (totalPages, number, etc.). Convertit depuis `Page<T>` Spring via `PageResponse.from(page)`. Les modules pré-existants continuent d'utiliser `Page<T>` brut ou des records ad hoc — pas de refactor.

- **Fichier `production-chimie.http`** à la racine du repo : golden path 14 étapes + 4 edge cases pour validation REST Client / Postman. Utiliser comme référence de payload quand on doute de la forme attendue par le front.

### Dette technique connue (à traiter dans des lots dédiés)

- **RBAC fin différé** : aucun contrôle backend des sous-permissions `productionChimie.formulations`, `.ordresFabrication`, `.lots`, etc. La sidebar Angular filtre déjà l'UI, mais un appel direct avec un JWT valide (même sans la permission) passera. Approche recommandée si on veut combler : annotation custom `@RequireModule("productionChimie.formulations")` + Aspect AOP qui décode le JWT (besoin d'ajouter `JwtUtil.extractModules(token)` qui n'existe pas encore — `generateToken` embarque `claim("modules", modules)` mais aucune méthode publique ne le décode).
- **Réconciliation `quantiteEnStock` ↔ somme des mouvements** : pas d'endpoint admin de check/reconcile. Si une réception/ajustement échoue entre l'insert du mouvement et l'update de la MP, on a une dérive. À prévoir si pertinent en prod.
- **Photos CQ** : indexées par position dans la liste (`/controles/{id}/photos/{index}`). Suppression d'une photo individuelle non implémentée — il faut DELETE + re-POST le contrôle.

### Conventions (rappels spécifiques au module)

- Volumes normalisés en litres dans le tableau de bord (`L` = 1, `ML` = 0.001, autres unités prises brutes — la majorité des produits chimie sont liquides).
- `dureePeremptionJours` est en **jours** (vs `dureeEssaiMois` en mois côté RH — attention à ne pas mélanger).
- Le n° d'OF utilise `LocalDate.now()` côté serveur ; si plusieurs OF sont créés à cheval sur minuit UTC vs `Africa/Dakar`, le compteur peut « rebaser » d'un jour à l'autre — c'est attendu (clé `_id` = date).
