# État des lieux de PointIC face au CDC V3 (WTK-CDC-2026-005 V1.4)

Audit en lecture seule réalisé le 20/11/2026. Aucun fichier des dépôts n'a été modifié ; seuls ce rapport et `checklist-v3.csv` ont été créés.

## Périmètre et méthode

| Élément | Valeur |
|---|---|
| Backend | `Pointage-Cleanic-Backend`, branche `feature/reclamation-qr-site` (Spring Boot + MongoDB) |
| Frontend | `pointage-agents`, branche `feature/lot-1b-creation` (Angular) |
| Copies ignorées | `pointage-agents-fix-telephone`, `Pointage-Cleanic-Backend-fix-telephone` (copies de travail) |
| État des dépôts | Les deux arbres de travail contiennent des modifications non commitées (travail en cours sur les réclamations et les QR d'agence, daté du 10 au 18/11/2026). L'audit porte sur l'arbre de travail, ces modifications comprises. |
| Méthode | Un audit par module (socle et transverses, RH, Exploitation, Stock, Industrie, autres modules), puis consolidation. Les constats de sécurité critiques ont été revérifiés à la lecture du code. |
| Ce qui n'a pas été fait | Aucun code n'a été exécuté : les comportements décrits (plantage, alertes fausses) sont déduits de la lecture. Aucune base de données n'a été consultée : les volumes ne sont pas connus. |

**Convention des preuves.** `back:` part du package `src/main/java/com/example/Pointage_Cleanic/` du backend, sauf pour les fichiers de racine (`build.gradle`, `docker-compose.yml`, `src/main/resources/application*.yml`). `front:` part de `src/app/` du frontend, sauf pour les fichiers de racine (`angular.json`, `package.json`…).

**À lire en premier :** la section 4.1 (sécurité) de la phase 4. Deux routes publiques exposent les comptes, et des secrets sont commités.

# Phase 1 — Structure du projet PointIC

Audit en lecture seule réalisé le 20/11/2026. Aucun fichier des dépôts n'a été modifié.

## 1. Dépôts audités

| Dépôt | Chemin | Branche courante | État du répertoire de travail |
|---|---|---|---|
| Backend Spring Boot + MongoDB | `Pointage-Cleanic-Backend` | `feature/reclamation-qr-site` (dernier commit `ca412a9 test(pointage): règle de rattachement employé ↔ site`) | 21 fichiers modifiés non commités (QR d'agence, `SiteClient`, `SecurityConfig`, `GlobalExceptionHandler`…) |
| Frontend Angular | `pointage-agents` | `feature/lot-1b-creation` (dernier commit `4cacf00` du 12/10/2026, « écran des QR codes d'agence et géolocalisation des sites ») | 21 fichiers modifiés non commités (dont `CLAUDE.md`, `sidebar.component.html`, écrans QR d'agence) |
| Copie de travail | `Pointage-Cleanic-Backend-fix-telephone` | `fix/pointage-telephone-autre-agent` (`a24ef48`, 18/11/2026) | mentionnée seulement, non auditée |
| Copie de travail | `pointage-agents-fix-telephone` | `fix/pointage-telephone-autre-agent` (`def48df`, 18/11/2026) | mentionnée seulement, non auditée |

Les deux dépôts sont présents. Un `docker-compose.yml` d'assemblage se trouve aussi dans le dossier parent `Projets en cours/` (hors dépôt git).

Remarque : la branche du frontend porte le même nom qu'une branche d'un autre projet (`feature/lot-1b-creation`), alors que son contenu (QR d'agence) relève du pointage. À confirmer avec l'équipe.

## 2. Arborescence backend (3 niveaux utiles)

Package racine : `src/main/java/com/example/Pointage_Cleanic/` (noté `J/`). 904 fichiers Java dans `src/` (main + test). Dossiers ignorés : `build/`, `out/`, `.gradle/`, `.idea/`.

```
Pointage-Cleanic-Backend/
├── .claude/docs/            documentation de module pour Claude (4 fichiers)
├── .github/workflows/       CI (backend.yml, main.yml vide)
├── gradle/wrapper/          Gradle 8.13
├── src/main/resources/      application.yml + profils dev / prod / test
├── src/main/java/…/J/
│   ├── configurations/      11 : Mongo, Jackson, WebSocket, OpenAPI, Google Maps, WebClient, horloge, index
│   ├── security/            3 : SecurityConfig, JwtRequestFilter, JwtUtil
│   ├── controllers/         7 legacy à la racine + productionchimie(10) reclamation(2) rh(1+18) stockv2(18) terrain(10)
│   ├── services/            9 racine + pointage(5) productionchimie(13) reclamation(4) rh(36) stockv2(42) terrain(20)
│   ├── repositories/        11 racine + pointage(1) productionchimie(11) projections(2) reclamation(1) rh(20) stockv2(12) terrain(13)
│   ├── entities/            7 racine + GestionModules(1+5) pointage(1) productionchimie(20) reclamation(2) rh(41) stock(1) stockv2(17) terrain(26)
│   ├── Dto/                 16 racine + pointage(4) productionchimie(28) reclamation(4) rh(54) stockv2(60) terrain(26)
│   ├── Mapper/              MapStruct : 3 racine + productionchimie(8) rh(15) stockv2(8) terrain(7)
│   ├── Enum/                13 racine + pointage(2) reclamation(2) rh(34) stockv2(27) terrain(19)
│   ├── exception/           26 : GlobalExceptionHandler, TerrainExceptionHandler, exceptions métier
│   ├── DataLoader/          12 : seeds et migrations au démarrage (CommandLineRunner)
│   ├── util/ utils/         utilitaires (PageResponse, CodeSecretUtil, rattachement site, BcryptGen)
│   ├── interfaces/ models/  divers (3 fichiers)
└── src/test/java/…/         123 classes de test, 941 annotations @Test (comptage grep)
```

Rôle des dossiers principaux :

| Dossier | Rôle |
|---|---|
| `security/` | JWT stateless, liste blanche des routes publiques, CORS codé en dur (`SecurityConfig`) |
| `configurations/` | Beans transverses. `MongoConfigurations` active `@EnableMongoAuditing` et importe `MongoTransactionManager` sans déclarer de bean |
| `controllers/` racine | Surface legacy : login, mot de passe, utilisateurs, `EmployeComplet`, pointage kiosque, `Produit` (stock historique), paramètres de pointage |
| `controllers/rh/` | RH 6.1 à 6.4 (gestion du personnel, temps et présences, paie, développement RH) + tableau de bord RH |
| `controllers/terrain/` | Exploitation terrain : sites clients, QR d'agence, planning, pointages GPS, alertes, interventions, contrôles, matériel, phytosanitaire, tableau de bord |
| `controllers/productionchimie/` | Industrie : matières premières, stock chimie, formulations, OF, lots, contrôle qualité, conditionnement, tableau de bord |
| `controllers/stockv2/` | Stock v2 (7.3 à 7.6) : catalogue, mouvements, bons, inventaires, analyse, valorisation |
| `controllers/reclamation/` | Réclamations publiques par QR de site + traitement interne |
| `DataLoader/` | Superadmin de bootstrap, grilles et formations d'exemple, sites de démonstration, migrations idempotentes |

## 3. Arborescence frontend (3 niveaux utiles)

386 fichiers `.ts` hors tests, 43 fichiers `.spec.ts` (347 blocs `it(`), 199 composants, 105 fichiers dans `services/`, 75 dans `models/`. Dossiers ignorés : `node_modules/`, `dist/`, `.angular/`.

```
pointage-agents/
├── docs/backend/              2 notes (périmètre de validation des congés, pointage centralisé)
├── docs/notes-de-livraison/   historique-affectations.html
├── src/environments/          environment.ts (dev) / environment.prod.ts (apiUrl '/api', wsUrl '/ws')
├── src/app/
│   ├── app.routes.ts          190 routes en loadComponent
│   ├── home-page/ page-code-pin/ final-page1/ final-page2/   kiosque de pointage agent (code secret)
│   ├── reclamation-publique/  formulaire public de réclamation par QR de site
│   ├── super-admin-login-page/ changement-password/
│   ├── guards/ services/ models/ constants/ shared/
│   └── adminPage/
│       ├── admin/ header/ sidebar/ notification/ confirm-dialog/ page-par-defaut-apres-login/
│       ├── auth/              forgot-password, reset-password
│       ├── gestion-privilege/ super-admin/    droits et utilisateurs
│       ├── ressources-humaines/  gestion-du-personnel, temps-et-presences, paie, developpement-rh, reclamations
│       ├── exploitation-v2/   dashboard, production-chimie (7 sous-dossiers), terrain (10 sous-dossiers)
│       └── stock-v2/          stocks-approvisionnement, controle-mouvements, analyse-consommations, valorisation-financiere, shared
```

| Dossier | Rôle |
|---|---|
| `adminPage/admin/` | Shell protégé (header, sidebar, router-outlet) |
| `adminPage/sidebar/` | Menu filtré par `ModulesAutorises` lu dans le JWT. Groupes : Tableau de bord, Ressources humaines, Industrie (5.1), Exploitation (5.2), Stock (7.3 à 7.6) |
| `adminPage/exploitation-v2/dashboard/` | Tableau de bord par défaut après connexion : pointages GPS du jour, alertes, affectations, effectif |
| `adminPage/ressources-humaines/` | 67 routes RH |
| `adminPage/exploitation-v2/` | 65 routes (28 production chimie, 36 terrain, 1 dashboard) |
| `adminPage/stock-v2/` | 45 routes |
| `services/` | Un service HTTP par domaine + services d'export (PDF jsPDF, Excel xlsx) côté client |
| `constants/` | Constantes métier côté front (`paie.constants.ts`, `stock-v2-valorisation.constants.ts`, `terrain.constants.ts`…) |

## 4. Versions

| Élément | Version | Preuve |
|---|---|---|
| Java | 21 (source et cible) | `back:build.gradle` (`JavaVersion.VERSION_21`), `back:Dockerfile` (`eclipse-temurin:21-jdk-jammy`) |
| Gradle | 8.13 (wrapper), image de build `gradle:8.10.2-jdk21` | `back:gradle/wrapper/gradle-wrapper.properties`, `back:Dockerfile` |
| Spring Boot | 3.3.6 | `back:build.gradle` (plugin et BOM) |
| Spring Data MongoDB | 4.3.6 (géré par le BOM Boot 3.3.6, présent dans le cache Gradle local) | `back:build.gradle` |
| MongoDB visé | `mongo:6` en docker-compose, `mongo:7.0` en Testcontainers | `back:docker-compose.yml`, `back:src/test/…` (MongoTestContainer) |
| Angular | 19.2.15 (core), Material et CDK 19.2.19 | `front:package.json`, `front:package-lock.json` |
| TypeScript | 5.5.4 | `front:package-lock.json` |
| Node | `node:18` dans le Dockerfile de build, v24 sur le poste local | `front:Dockerfile` |
| Tailwind | 3.4.18 | `front:package-lock.json` |
| Tests front | Karma 6.4 + Jasmine 5.1 | `front:package.json` |

## 5. Dépendances notables

| Besoin | Backend | Frontend |
|---|---|---|
| Sécurité | `spring-boot-starter-security`, `jjwt` 0.11.5 | `jwt-decode` 4 |
| PDF | OpenPDF 1.3.30, utilisé par 6 services : `PointageServices`, `rh/BulletinPaieService`, `rh/DeclarationSocialeService`, `rh/RecapitulatifMensuelService`, `terrain/InterventionPdfService`, `terrain/RegistrePhytoPdfService` | `jspdf` 3.0.3 + `jspdf-autotable` 5 (la plupart des exports PDF sont côté client) |
| Excel | Apache POI `poi-ooxml` 5.4.0 | `xlsx` 0.18.5, `file-saver` |
| WebSocket | `spring-boot-starter-websocket` (STOMP + SockJS sur `/ws`) | `@stomp/stompjs` 7.2, `sockjs-client` |
| Mail | `spring-boot-starter-mail` (SMTP, envoi synchrone) | — |
| SMS / WhatsApp | non trouvé (aucune dépendance Twilio, Orange, WhatsApp) | non trouvé |
| Planification | `@EnableScheduling` (`PointageCleanicApplication`), 2 jobs : `services/terrain/AffectationStatutScheduler`, `services/terrain/DetectionAlertesJob`. Pas de ShedLock ni Quartz | — |
| HTTP sortant | `spring-boot-starter-webflux` (WebClient vers Google Geocoding) | `@googlemaps/js-api-loader` |
| Documentation API | springdoc-openapi 2.3.0 | — |
| Mapping | MapStruct 1.5.5, Lombok | — |
| Tests | spring-boot-starter-test, Mockito 5.12, Testcontainers 1.20.2 (mongodb 1.19.2) | Karma, Jasmine |
| UI | — | Angular Material, FullCalendar 6, angular-calendar, Chart.js 4.5 + ng2-charts 8, lucide-angular, ngx-toastr, ngx-spinner |
| QR et terrain | — | `angularx-qrcode`, `qrcode`, `signature_pad`, `browser-image-compression` |
| Paquets parasites | — | `build`, `ng`, `production` déclarés en dépendances (sans usage apparent) |
| SSR | — | `@angular/ssr` et script `serve:ssr` présents, mais `angular.json` ne configure aucun serveur |
| PWA | — | non trouvé (pas de `manifest`, pas de `@angular/service-worker`) |

## 6. Modules métier présents

| Module | Génération | Backend | Frontend | Remarque |
|---|---|---|---|---|
| Authentification, utilisateurs, droits | legacy | `J/controllers/LoginController`, `UtilisateurController`, `PasswordResetController`, `J/entities/User`, `Utilisateur`, `GestionModules/ModulesAutorises` | `super-admin-login-page/`, `adminPage/gestion-privilege/`, `adminPage/super-admin/`, `services/login.service.ts` | Deux modèles d'utilisateur (`User` collection `login`, `Utilisateur` collection `utilisateur`). Droits par drapeaux booléens, filtrés côté front uniquement |
| Pointage kiosque (code secret) | legacy | `J/controllers/PointagesController`, `J/entities/Pointage`, `ParametresPointageController`, `J/services/pointage/` | `home-page/`, `page-code-pin/`, `final-page1/`, `final-page2/` | Routes publiques sans jeton |
| Employés legacy | legacy | `J/entities/EmployeComplet`, `Employe`, `EmployeCompletController` | — (services front supprimés) | Gardé pour l'import Excel et l'image publique ; `DossierEmploye` est la source RH |
| Stock historique | legacy | `J/entities/stock/Produit`, `J/controllers/ProduitController` (`/api/produits`) | aucun écran (dossier `stock/` absent du front) | Code mort côté front, collection `produits` toujours mappée |
| RH (6.1 à 6.4) | v2 | `J/*/rh/` | `adminPage/ressources-humaines/` | Détaillé par un autre agent |
| Exploitation terrain (5.2) | v2 | `J/*/terrain/` | `adminPage/exploitation-v2/terrain/` | Détaillé par un autre agent |
| Industrie, production chimie (5.1) | v2 | `J/*/productionchimie/` | `adminPage/exploitation-v2/production-chimie/` | A son propre stock (`production_chimie_mouvements_stock`) |
| Stock v2 (7.3 à 7.6) | v2 | `J/*/stockv2/` | `adminPage/stock-v2/` | Collections `stockv2_*` |
| Réclamations par QR de site | v2 (en cours) | `J/*/reclamation/` | `reclamation-publique/`, `adminPage/ressources-humaines/reclamations/` | Branche courante du backend |
| Direction, Commercial, QHSE, Finances, Contrôle de gestion | absents | non trouvé | non trouvé | Voir `autres.md` |

Recouvrement legacy / v2 :

- **Exploitation** : le dossier front `exploitation/` a été supprimé (note historique dans `front:CLAUDE.md`, section « Module Exploitation »). Côté backend, les contrôleurs legacy cités dans `back:CLAUDE.md` (`EmployesController`, `AgencesController`, `SitesController`, `PlanificationController`, `FerieController`, `AbsencesControllers`, `CollecteBesoinController`, `DashboardController`) sont introuvables (grep `class X` vide). Le `CLAUDE.md` backend est donc en retard sur le code.
- **Stock** : le front n'a plus que `stock-v2/`. Le backend garde `Produit` (collection `produits`) et `ProduitController`, sans écran consommateur. Le stock chimie (`production_chimie_matieres_premieres`, `production_chimie_mouvements_stock`) reste un second stock distinct de `stockv2_etats_stock`.
- **Employé** : trois représentations (`employes`, `employes_complet`, `dossiers_employes`). La source de vérité déclarée est `DossierEmploye`.

Collections MongoDB déclarées : 68 `@Document` (liste extraite par grep), préfixées par module (`stockv2_*`, `production_chimie_*`, `terrain_*`) ou sans préfixe pour RH et legacy.

## 7. Conventions des CLAUDE.md et de la documentation Claude

Il n'existe pas de dossier `docs/claude/` dans les deux dépôts. L'équivalent est `back:.claude/docs/` (4 fichiers : `module-rh.md`, `module-terrain.md`, `module-production-chimie.md`, `module-stockv2.md`, 738 lignes au total). Le front a `docs/backend/` (2 notes de spécification).

**Backend (`back:CLAUDE.md`, 242 lignes)**

1. Architecture en couches `controllers → services → repositories → entities`, DTO dans `Dto/`, mappers MapStruct câblés en Spring par argument de compilation.
2. Code de chaque module rangé dans des sous-packages (`rh`, `terrain`, `productionchimie`, `stockv2`) ; collections préfixées par module.
3. Auth JWT stateless ; aucune annotation `@PreAuthorize` : tout est `.authenticated()`, les droits fins sont délégués au front via `ModulesAutorises`.
4. Erreurs : `IllegalArgumentException` donne 400 `VALIDATION_ERROR`, `ResourceNotFoundException` 404, exceptions de module 409 et 422 ; format distinct pour le terrain (`TerrainExceptionHandler`).
5. Numérotation par `findAndModify` avec compteur quotidien (`CompteurLotService`, `CompteurStockService`, `CompteurTerrainService`), testée en concurrence.
6. Pas de transaction Mongo multi-document : compensation manuelle (pattern `OrdreFabricationService`).
7. Fichiers en `byte[]` inline dans les documents (pas de GridFS ni S3).
8. Index : `auto-index-creation` désactivé, les index passent par des classes `*IndexesConfig`.
9. Montants FCFA en `long`, dates `dd/MM/yyyy` en `Africa/Dakar` pour `java.util.Date`, ISO pour `LocalDate` dans les modules récents.
10. Tests : `@WebMvcTest` sans filtres de sécurité, intégration avec Testcontainers `mongo:7.0`, exécution mono-thread.

**Frontend (`front:CLAUDE.md`, 1 758 lignes, dont l'essentiel décrit les écrans module par module)**

1. Angular 19 en composants standalone, sans NgModule ; toutes les routes en `loadComponent()`.
2. `AuthGuard` lit le JWT dans `localStorage` ; `AuthInterceptor` ajoute le jeton.
3. Menu et écrans masqués selon `ModulesAutorises` décodé du JWT, propagé par `BehaviorSubject`.
4. Formulaires réactifs uniquement (pas de `ngModel`) dans les modules récents.
5. Graphiques ng2-charts + Chart.js ; exports PDF jsPDF et Excel xlsx côté client.
6. Locale fr-FR, dates `dd/MM/yyyy`, montants FCFA sans décimale.
7. Icônes Lucide à enregistrer dans `lucide-icons.ts` (PascalCase).
8. Services dans `src/app/services/`, modèles dans `src/app/models/`, constantes réglementaires dans `src/app/constants/`.
9. Pas de script de lint : le mode strict de TypeScript tient lieu de contrôle.
10. Le module `exploitation/` legacy est supprimé ; `stock/` est annoncé « à supprimer » mais n'existe déjà plus dans l'arborescence.

## 8. Configuration

### Profils Spring

| Fichier | Contenu (clés seulement) |
|---|---|
| `application.yml` | `spring.data.mongodb.uri` (valeur littérale locale), `spring.mail.*`, Jackson (`dd/MM/yyyy`, `Africa/Dakar`), multipart 10 Mo / 15 Mo, `jwt.secret` (littéral), springdoc, `google.maps.api.key` (littéral), `app.frontend.base-url`, `app.conges.jours-acquis-par-mois` (défaut 2), `rh.import.bulk.max-size` (1000), `rh.pointage.tolerance-retard-minutes` (15), niveaux de log |
| `application-dev.yml` | `jwt.secret` (littéral), `spring.mail.username` / `password` par variables |
| `application-prod.yml` | `spring.data.mongodb.uri` (littéral, hôte `mongo`), mail par variables, `jwt.secret` par variable, `app.*`, `google.maps.api.key` (littéral) |
| `application-test.yml` | `jwt.secret` factice, `spring.mail.host: disabled` |

Des secrets sont présents en clair dans `application.yml`, `application-dev.yml` et `application-prod.yml` (clé JWT, clé Google Maps). Ces fichiers sont suivis par git. Valeurs non recopiées.

### Variables d'environnement attendues (noms seulement)

| Variable | Utilisation |
|---|---|
| `SPRING_PROFILES_ACTIVE` | profil (`prod` en Docker) |
| `SPRING_DATA_MONGODB_URI` | surcharge de l'URI Mongo (compose parent) |
| `JWT_SECRET` | `application-prod.yml` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP (dev et prod) |
| `APP_FRONTEND_BASE_URL` | lien dans les e-mails |
| `CONGES_JOURS_ACQUIS_PAR_MOIS` | acquisition de congés (défaut 2) |
| `GOOGLE_MAPS_API_KEY` | présent dans `.env` (non suivi par git) et dans `docker-compose.yml`, mais non lu par les YAML (clé littérale) |

### Déploiement

| Élément | Constat | Preuve |
|---|---|---|
| Backend Docker | build multi-étapes Gradle puis `eclipse-temurin:21`, profil `prod` forcé, port 8080, `--add-opens java.time` | `back:Dockerfile` |
| Backend compose | `mongo:6` (port hôte 27018) + Spring Boot. **Le fichier suivi par git contient en clair le mot de passe SMTP, la clé JWT et la clé Google Maps** (le `CLAUDE.md` backend les déclare compromis) | `back:docker-compose.yml` |
| Frontend Docker | build `node:18`, servi par `nginx:alpine` | `front:Dockerfile` |
| Nginx | SPA (`try_files`), proxy `/api/` et `/ws` vers `springboot:8080`, en-têtes anti-cache, pas de TLS dans la conf | `front:nginx.conf` |
| Compose d'assemblage | Mongo + backend + frontend sur un réseau commun, mêmes secrets en clair | `Projets en cours/docker-compose.yml` (hors dépôt) |
| CI | `backend.yml` sur push `main` : build sans tests puis tests. Étapes Docker push et déploiement VPS en commentaire. La première étape a un `name` sans `uses` (étape probablement invalide). `main.yml` vide (2 octets). Aucune CI frontend | `back:.github/workflows/` |
| Hébergement | domaines `pointic-cleanic.com`, `app.pointic-cleanic.com`, `www.pointic-cleanic.com` dans le CORS, sous-domaines ngrok pour les tests ; déploiement sur VPS suggéré par la CI commentée. Hébergeur non trouvé | `back:J/security/SecurityConfig.java` |
| WebSocket | `/ws` avec `setAllowedOriginPatterns("*")` et SockJS | `back:J/configurations/WebSocketConfig.java` |
| MongoDB | standalone (pas de replica set dans les compose) | `back:docker-compose.yml` |

## 9. Volumes de données

Aucun script de reprise, export ou jeu de données volumineux n'a été trouvé (pas de `.csv`, `.xlsx` ni `.json` de données dans les dépôts hors configuration). Seeds au démarrage :

| Seed | Volume | Preuve |
|---|---|---|
| Superadmin de bootstrap | 1 compte (collection `login`) | `back:J/DataLoader/DataLoader.java` |
| Grille d'évaluation et formations d'exemple | 1 grille, 3 formations | `back:J/DataLoader/DataLoader.java` |
| Sites clients de démonstration | 2 sites, 1 grille terrain, 1 produit phyto, 1 paramétrage d'escalade, si collections vides | `back:J/DataLoader/TerrainDataLoader.java` |
| Paramètres de paie | 1 document singleton | `back:J/DataLoader/ParametresPaieDataLoader.java` |
| Migrations idempotentes | 9 runners (affectations, QR d'agence, statuts de congé, alternance, agentId, droits, période d'essai, module RH, entrepôt unique de stock) | `back:J/DataLoader/*Runner.java` |

Les volumes réels de production ne sont pas visibles dans le code. Les journaux des runners (« N dossier(s) back-fillé(s) ») les donneraient à l'exécution. Le seul plafond visible est `rh.import.bulk.max-size: 1000` (import en masse).

## 10. Points d'attention pour la suite

1. Secrets en clair dans des fichiers suivis par git (`docker-compose.yml`, `application*.yml`) : à régénérer avant la V3.
2. `CLAUDE.md` backend décrit des contrôleurs legacy qui n'existent plus : la documentation n'est pas une source fiable sans vérification.
3. Aucune autorisation côté serveur : les droits par module ne sont appliqués que par le front.
4. MongoDB en standalone, sans transaction : toute la cohérence multi-document repose sur des compensations manuelles.
5. Aucun module Direction, Commercial, QHSE, Finances ni Contrôle de gestion (voir `autres.md`).

# Phase 2 — Inventaire technique

Ordre : socle et sujets transverses, puis RH, Exploitation (avec Pointage QR et PWA), Stock, Industrie, et les modules sans code dédié (Direction, Commercial, QHSE, Finances). Les notes de fin de chaque partie sont regroupées en phase 4.

## Socle, sécurité et sujets transverses — état des lieux

Audit en lecture seule, le 20/11/2026.
- Backend : `Pointage-Cleanic-Backend` (préfixe `back:` = `src/main/java/com/example/Pointage_Cleanic/`, sauf mention contraire). Spring Boot 3.3.6, Java 21, MongoDB (`mongo:6` dans `docker-compose.yml`).
- Frontend : `pointage-agents` (préfixe `front:` = `src/app/`). Angular 19.2.
- Les deux dépôts sont présents. Dossiers `*-fix-telephone`, `build/`, `out/`, `node_modules/`, `dist/` ignorés.
- Aucun secret n'est recopié. Quand une valeur sensible existe, le rapport dit seulement où elle se trouve.

### 1. Collections MongoDB du socle

| Collection | Classe | Champs principaux | Références | Index | Remarques |
|---|---|---|---|---|---|
| `login` | `entities/User.java` | id, email, password (hash BCrypt), mustChangePassword, role (String) | aucune (lien implicite par e-mail avec `utilisateur`) | aucun `@Indexed` (pas d'unicité sur email) | Source de vérité du mot de passe (`MyUserDetailsService.loadUserByUsername`). Seul endroit où existe `SUPERADMIN` (seedé par `DataLoader.seedSuperadmin`). |
| `utilisateur` | `entities/Utilisateur.java` | prenom, nom, email, password (hash, **dupliqué** avec `login`), poste, role (`Enum/RoleAdmin`), mustChangePassword, modulesAutorises, motifDesactivation, active | aucune (pas de lien vers `dossiers_employes`) | aucun | Profil des comptes « admin ». `active=false` bloque la connexion (`MyUserDetailsService`, `DisabledException`). |
| (sous-document) `modulesAutorises` | `entities/GestionModules/ModulesAutorises.java` | dashboard, admin, rh (objet), productionChimie, terrain, stock | — | — | Booléens uniquement. Sous-flags : `SousModules/Rh.java` (19), `Stock.java` (27), `Terrain.java` (9), `ProductionChimie.java` (7). `RhDeserializer` accepte l'ancien format booléen. |
| `ResetPasswordToken` | `entities/ResetPasswordToken.java` | email, token (OTP 6 chiffres), expiration | email | aucun (pas de TTL, pas d'unicité) | Jeton supprimé seulement s'il est utilisé (`ResetPasswordService.resetPassword`). |
| `stockv2_compteurs` | `entities/stockv2/CompteurStock.java` | _id = `PREFIXE-yyyyMMdd` ou `PREFIXE-yyyy`, compteur | — | _id | Compteurs Stock (BE, BS, INV, MVT, CH). |
| `production_chimie_compteurs_of`, `production_chimie_compteurs_lots` | `entities/productionchimie/CompteurOf.java`, `CompteurLot.java` | _id, compteur | — | _id | Compteurs OF et lots. |
| `compteurs_terrain` | `entities/terrain/CompteurTerrain.java` | _id, compteur | — | _id | Compteurs INT et PHYTO (journaliers). |
| `stockv2_suppressions` | `entities/stockv2/SuppressionStockLog.java` | document supprimé, auteur, date | — | — | Seul journal de suppression du code (Stock v2). |
| Notifications | non trouvé | — | — | — | Aucune collection : les notifications STOMP ne sont pas persistées. |
| Journal d'audit / journal des accès | non trouvé | — | — | — | Aucune collection. |
| Rôles / privilèges | non trouvé | — | — | — | Les rôles sont un enum (`Enum/RoleAdmin.java` : 9 valeurs, sans SUPERADMIN) plus la chaîne `"SUPERADMIN"` dans `login`. Pas de collection de profils. |

Données dupliquées entre modules (impact direct sur F001) :
- **Employé** : `employes` (`entities/Employe.java`), `employes_complet` (`entities/EmployeComplet.java`, legacy, encore servi par `controllers/EmployeCompletController.java`), `dossiers_employes` (`entities/rh/DossierEmploye.java`). Plus `utilisateur` et `login` sans lien vers le dossier.
- **Article** : `produits` (`entities/stock/Produit.java`, legacy, `controllers/ProduitController.java`), `stockv2_produits` (`entities/stockv2/ProduitStock.java`), `production_chimie_matieres_premieres` (`entities/productionchimie/MatierePremiere.java`), `produits_phyto` (`entities/terrain/ProduitPhytosanitaire.java`).
- **Fournisseur** et **client** : aucune collection. Texte libre dans `MatierePremiere.fournisseur`, `BonEntree.fournisseur`, `ProduitStock.fournisseurPrincipal`, `ProduitPhytosanitaire.fournisseur`, `Chantier.client`, `DestinataireBon.clientNom`.
- **Site** : `sites_clients` (`entities/terrain/SiteClient.java`) est référencé par `siteId` dans le Stock v2 (`Chantier`, `Inventaire`, `Plafond`, `DestinataireBon`) : c'est le seul référentiel réellement partagé. `DossierEmploye.siteAffecte` est une chaîne dérivée.

### 2. API REST du socle

#### `LoginController` — `back:controllers/LoginController.java`, base `/api/login`

| Méthode | Chemin | Accès réel (SecurityConfig) | Rôle métier |
|---|---|---|---|
| POST | `/api/login` | public | Authentifie (e-mail + mot de passe) et renvoie un JWT. Cherche d'abord `utilisateur`, puis `login` (super-admin, `generateToken2` sans `modules`). |
| POST | `/api/login/change-password` | public | Change le mot de passe (e-mail, ancien, nouveau), minimum 6 caractères, renvoie un nouveau JWT. |
| GET | `/api/login` | **public** | Renvoie toute la collection `login`, **hash des mots de passe compris** (`User` n'a aucun `@JsonIgnore`). |
| DELETE | `/api/login/{id}` | **public** | Suppression physique d'un compte `login`. |

`/api/login/**` est en `permitAll` (`back:security/SecurityConfig.java`, `securityFilterChain`) : le motif couvre aussi `/api/login` lui-même. Les deux dernières routes sont donc ouvertes sans jeton. **Constat critique.**

#### `UtilisateurController` — `back:controllers/UtilisateurController.java`, base `/api/superadmin`

| Méthode | Chemin | Accès réel | Rôle métier |
|---|---|---|---|
| POST | `/api/superadmin` | tout compte authentifié | Crée un `utilisateur` et sa ligne `login` (rôle, modules). Journalise le corps reçu par `System.out.println`, mot de passe en clair compris. |
| GET | `/api/superadmin` | tout compte authentifié | Liste les utilisateurs, hash du mot de passe compris. |
| GET | `/api/superadmin/{id}` | tout compte authentifié | Détail d'un utilisateur. |
| PUT | `/api/superadmin/{id}` | tout compte authentifié | Modifie profil, rôle, modules, actif ; resynchronise `login`. |
| DELETE | `/api/superadmin/{id}` | tout compte authentifié | Suppression physique de `utilisateur` et `login`. |

Aucune vérification de rôle : n'importe quel compte connecté peut se donner tous les modules ou supprimer un compte (élévation de privilèges). La règle `/api/super-admin/**` de `SecurityConfig` (avec un tiret) ne vise pas ce contrôleur.

#### `PasswordResetController` — `back:controllers/PasswordResetController.java`, base `/auth`

| Méthode | Chemin | Accès | Rôle métier |
|---|---|---|---|
| POST | `/auth/forgot-password` | public | Envoie un code à 6 chiffres par e-mail, valable 10 min (`ResetPasswordService.sendResetPasswordEmail`). Répond une erreur si l'e-mail est inconnu (énumération des comptes possible). |
| POST | `/auth/reset-password` | public | Réinitialise à partir du code seul (`tokenRepo.findByToken`), sans e-mail ni limite d'essais. |

#### WebSocket — `back:configurations/WebSocketConfig.java`

| Point d'entrée | Accès | Rôle |
|---|---|---|
| `/ws` (SockJS, STOMP) | public (`permitAll`), `setAllowedOriginPatterns("*")` | Broker simple en mémoire `/topic`, `/queue`. Aucun `ChannelInterceptor` : le JWT envoyé dans `connectHeaders` (`front:services/websocket.service.ts`) n'est jamais lu. |

Les autres contrôleurs (RH, Stock, Terrain, Production chimie, pointage) relèvent des autres agents. Total des routes DELETE : 39 (voir 5.2).

### 3. Frontend socle

#### Routes racines (`front:app.routes.ts`)

| Chemin | Composant | Garde |
|---|---|---|
| `''`, `home` | `home-page` | aucune |
| `code-pin`, `pagefinal1/:codeSecret`, `pagefinal2/:codeSecret` | pointage agent par code | aucune |
| `reclamation` | `reclamation-publique` | aucune |
| `super-admin-login` | `super-admin-login-page` | aucune |
| `forgot-password`, `reset-password` | `adminPage/auth/*` | aucune |
| `change-password` | `changement-password` | `AuthGuard` |
| `admin` (+ environ 150 routes enfants : RH, Stock v2, Exploitation v2, `gestion-privilege`…) | `adminPage/admin` | `AuthGuard` sur le parent seulement |

Seules 2 routes portent `canActivate` (`front:app.routes.ts`). Aucune garde par module : un compte connecté peut ouvrir toute URL d'un module qu'on lui a masqué dans le menu.

#### Garde, intercepteur, services transverses

| Élément | Fichier | Comportement |
|---|---|---|
| `AuthGuard` | `front:guards/auth.guard.ts` | Vérifie la présence du jeton et `mustChangePassword`. Pas de contrôle de module ni de rôle. Journaux `console.log` laissés. |
| `AuthInterceptor` | `front:auth.interceptor.ts` (déclaré dans `app.config.ts` via `HTTP_INTERCEPTORS`) | Ajoute `Bearer` sur `/api/`, sauf `/api/login` et pointage public. Déconnecte si le jeton est expiré (contrôle côté client). Pas de refresh. |
| `LoginService` | `front:services/login.service.ts` | Jeton et `modulesAutorises` dans `localStorage`, décodage `jwt-decode`, `isTokenExpired`, `logout`. |
| `AdminService` | `front:services/admin.service.ts` | CRUD `/api/superadmin`. |
| `AuthService` | `front:services/auth.service.ts` | Mot de passe oublié et réinitialisation. |
| `WebsocketService` | `front:services/websocket.service.ts` | STOMP/SockJS, 11 abonnements (alertes terrain, stock, congés, plus `/topic/annulationRequests` et `/topic/annulationDecisions` qu'aucun service backend n'émet). |
| `ConfirmDialogService` | `front:shared/confirm-dialog.service.ts` | Seul service partagé de `shared/` avec `text-normalize.util.ts`. Aucun composant générique (pièce jointe, export, import, panneau latéral). |
| Sidebar | `front:adminPage/sidebar/sidebar.component.ts` | Masque les menus selon `modulesAutorises` et le rôle (`SUPERADMIN` voit tout). Seul endroit où les droits fins sont appliqués. |
| Gestion des privilèges | `front:adminPage/gestion-privilege/gestion-privilege.component.ts` | Écran de création et modification des comptes, cases à cocher par sous-rubrique. |
| Notifications | `front:adminPage/notification/notification.component.ts`, `adminPage/super-admin/super-admin.component.ts` | Affichage des messages STOMP reçus, son, en mémoire seulement. |

### 4. Règles codées en dur du socle

| Règle | Valeur | Preuve |
|---|---|---|
| Durée du JWT | 8 h, pas de refresh token | `back:security/JwtUtil.java` (`EXPIRATION_TIME`) |
| Algorithme JWT | HS256, secret d'au moins 32 caractères | `JwtUtil.init`, `generateToken` |
| Secret JWT | présent dans application-*.yml ; en clair dans `application.yml`, `application-dev.yml`, `application-test.yml` (commités) ; variable d'environnement en prod | `src/main/resources/application*.yml` |
| Clé Google Maps | présente dans application-*.yml, en clair (`application.yml`, `application-prod.yml`) | idem |
| URI MongoDB | présente dans application-*.yml (sans identifiants) | idem |
| Mot de passe initial du super-admin | codé en dur dans le code source (valeur non recopiée), `mustChangePassword=true` | `back:DataLoader/DataLoader.java` (`seedSuperadmin`) |
| Longueur minimale du mot de passe | 6 caractères | `LoginController.changePassword` (aucune règle à la création) |
| Code de réinitialisation | 6 chiffres, `Math.random()`, 10 min | `ResetPasswordService.sendResetPasswordEmail` |
| CORS | 7 origines en dur (pointic-cleanic.com, sous-domaines, localhost, `*.ngrok-free.dev`, un tunnel ngrok nommé), `allowCredentials=true`, préflight 1 h | `SecurityConfig.securityFilterChain` |
| CORS WebSocket | `*` | `WebSocketConfig.registerStompEndpoints` |
| Tentatives de connexion | aucune limite, aucun verrouillage | non trouvé |
| Session | stateless, CSRF désactivé | `SecurityConfig` |
| Routes publiques sensibles | photos employés (`/api/employe-complet/image/**`, `/api/dossier-employe/*/photo`), `/swagger-ui/**`, `/v3/api-docs/**`, `/ws/**` | `SecurityConfig` |
| Taille des fichiers | 10 Mo par fichier, 15 Mo par requête | `application.yml` (`spring.servlet.multipart`) |
| Fuseau | `Africa/Dakar`, format `dd/MM/yyyy` | `application.yml`, `configurations/TimeConfig.java` |
| Niveau de log sécurité | `org.springframework.security: DEBUG` dans le profil par défaut | `application.yml` |
| Expéditeur e-mail | adresse Gmail par défaut | `back:services/EmailService.java` |
| Rôle super-admin | chaîne `"SUPERADMIN"` répétée dans au moins 6 services | `HabilitationStock`, `CongeIdentiteService`, `ParametresPointageService`… |

### 5. Sujets transverses

#### 5.1 Authentification JWT, rôles et permissions — **Partiel**

- JWT présent : `back:security/JwtUtil.java`, `JwtRequestFilter.java`. Le jeton porte prenom, nom, role, email, poste, mustChangePassword et l'objet `modules` complet.
- Autorisation déclarative absente : `SecurityConfig` ne fait que `.authenticated()`, il n'y a ni `@EnableMethodSecurity` ni `@PreAuthorize` (dit en toutes lettres dans le commentaire de `SecurityConfig` et dans `services/stockv2/HabilitationStock.java`).
- Contrôles de rôle écrits à la main, service par service, avec `CurrentUserProvider.currentRole()` (`back:services/terrain/CurrentUserProvider.java`) : `HabilitationStock` (validation de bon et suppression définitive réservées au super-admin), `CongeIdentiteService`, `CongeWorkflowService`, `ParametresPointageService`, `AgenceQrAdminService`, `ReclamationService`, `RhAbsenceService`, `JourFerieService`, `PlanningService`, `ControleQualiteService`, `AlerteService`. La paie, les dossiers employés, la production chimie et la gestion des comptes n'en ont aucun.
- Granularité : module et sous-rubrique en booléens (`ModulesAutorises` et `SousModules/*`), sans niveau (lecture, saisie, validation…) ni action. **Le serveur ignore ce claim** (`HabilitationStock`, javadoc). Le front ne l'applique qu'au menu (`sidebar.component.ts`), pas aux routes.
- Le super-admin reçoit un jeton sans `modules` (`JwtUtil.generateToken2`).
- Masquage des champs sensibles : **absent**. Hash des mots de passe renvoyés par `GET /api/login` (public) et `GET /api/superadmin` ; salaire, RIB, numéros IPRES et CSS de `DossierEmploye` servis à tout compte connecté ; aucun `@JsonIgnore` sur `User` ou `Utilisateur`.
- Le front garde le jeton dans `localStorage` (`front:services/login.service.ts`).

#### 5.2 Journal d'audit et suppressions physiques — **Partiel** (historiques locaux) / suppressions **présentes partout**

- Journal d'audit global : non trouvé (pas de collection, pas d'`AuditorAware`, `@CreatedBy` absent ; un seul champ `@CreatedDate`/`@LastModifiedDate`, dans `entities/Pointage.java`, malgré `@EnableMongoAuditing` dans `MongoConfigurations`).
- Historiques locaux existants : `entities/rh/HistoriqueValidationConge.java`, `entities/stockv2/EntreeHistorique.java` (bons), `entities/productionchimie/HistoriqueStatutOf.java`, `entities/terrain/EscaladeEntry.java`, `entities/stockv2/SuppressionStockLog.java`, `ParametresPaie.modifieParId/modifieParNom/dateModification`.
- Routes `@DeleteMapping` (39 au total) et appels de suppression (`deleteById`, `delete(`, `deleteAll`, `deleteBy…`, `remove`) par module :

| Module | `@DeleteMapping` | Appels de suppression |
|---|---|---|
| RH (`controllers/rh`, `services/rh`) | 16 | 26 |
| Stock v2 | 7 | 18 |
| Terrain | 6 | 9 |
| Production chimie | 5 | 8 |
| Réclamation, pointage | 0 | 0 |
| Racine (login, utilisateurs, employé complet, produit legacy) | 5 | 8 |

- Pièces de gestion supprimables : bons d'entrée et de sortie (`BonEntreeController`, `BonSortieController`, brouillon seulement dans `BonEntreeService.supprimer`), bulletins (`BulletinPaieController`, brouillon seulement dans `BulletinPaieService.delete`), déclarations sociales (`DeclarationSocialeController`), inventaires (`InventaireController`), sanctions (`SanctionController`), contrats (`ContratController`), demandes de congé (`TempsPresencesCongeController`). Suppression définitive de documents validés par le super-admin, avec contre-passement et journal : `services/stockv2/SuppressionDefinitiveService.java`.
- Annulation tracée (statut ANNULÉ avec motif) comme mécanisme général : non trouvé.

#### 5.3 Numérotation des pièces — **Partiel**

- Compteurs atomiques `findAndModify($inc, upsert, returnNew)` : `back:services/stockv2/CompteurStockService.java`, `services/productionchimie/CompteurOfService.java`, `CompteurLotService.java`, `services/terrain/CompteurTerrainService.java`. Pas de risque de doublon.
- Risque de trou : le numéro est attribué à la création du brouillon (`BonEntreeService`, ligne `genererReference("BE")`, idem `BS`, `INV`), puis le brouillon peut être supprimé.
- Format journalier (`BE-yyyyMMdd-NNN`), pas par exercice ; seul `CH` est annuel. `genererReference(String)` utilise `LocalDate.now()` sans fuseau, alors que `genererReferenceAnnuelle` utilise `Africa/Dakar`.
- Quatre collections de compteurs différentes, sans service commun. Pas de compteur pour DEV, FAC, DD, BC, BCA, CHQ (modules absents).

#### 5.4 Transactions MongoDB — **Absent**

- `@Transactional`, `TransactionTemplate`, `withTransaction` : aucune occurrence. `MongoTransactionManager` est importé dans `configurations/MongoConfigurations.java` mais jamais déclaré en bean.
- Replica set : non configuré (`docker-compose.yml` : `mongo:6` sans `--replSet`). Le javadoc de `DossierEmployeService.importBulk` le confirme et assume l'absence de rollback (`BulkInsertPartialFailureException`).
- Les mises à jour de stock reposent sur des `$inc` unitaires (`OrdreFabricationService`, `MouvementStockChimieService`) : un bon multi-lignes n'est pas atomique.

#### 5.5 Paramètres datés — **Absent**

- Paramètres en document unique écrasé : `entities/rh/ParametresPaie.java` (taux IPRES, CSS, barème IR, TRIMF), `ParametresConges.java`, `entities/pointage/ParametresPointage.java`, `entities/terrain/ParametresEscalade.java`, `entities/productionchimie/ParametresProductionChimie.java`, `entities/stockv2/ParametrageValorisation.java`. Aucun champ de date d'effet (seul `entities/rh/Avenant.dateEffet` existe, hors paramètres).
- Valeurs dans `application.yml` : tolérance de retard 15 min, 2 jours de congé acquis par mois, import en masse limité à 1 000 lignes.

#### 5.6 États calculés ou stockés — **Stockés** (contraire au principe v2.2)

- Soldes de stock stockés et incrémentés : `stockv2_etats_stock` (`entities/stockv2/StockParSite.java`, `quantite`), `MatierePremiere.quantiteEnStock`.
- Statuts d'affectation réécrits chaque minute : `services/terrain/AffectationStatutScheduler.java`.
- Champ dérivé stocké : `DossierEmploye.siteAffecte` (dérivé des affectations), mot de passe dupliqué dans `login` et `utilisateur`.

#### 5.7 Circuits de validation — **Codés écran par écran**

- Congés : 3 niveaux fixes dans un enum (`Enum/rh/NiveauValidationConge.java` : SUPERIEUR, RH, DIRECTION_GENERALE), `services/rh/CongeWorkflowService.java`.
- Bons de stock : BROUILLON, SOUMIS, VALIDE ou REFUSE, validation réservée au super-admin (`BonEntreeService`, `BonSortieService`, `HabilitationStock.exigerSuperAdmin`, `controllers/stockv2/WorkflowStockController.java`).
- Paie : BROUILLON puis VALIDE (`BulletinPaieService`). Ordres de fabrication : statuts propres (`OrdreFabricationService`).
- Moteur paramétrable (type de pièce, seuil, délégation) : non trouvé.

#### 5.8 Notifications — **Partiel**

- WebSocket STOMP : `services/rh/CongeNotificationService.java`, `services/stockv2/StockNotificationService.java`, `services/terrain/TerrainNotificationService.java`. Les messages ciblés (`convertAndSendToUser`) ne peuvent pas arriver, faute de principal STOMP (aucun intercepteur d'authentification), et les topics sont diffusés à tout client connecté à `/ws` sans jeton.
- E-mail : `services/EmailService.java` (JavaMailSender), utilisé par `ResetPasswordService`, `services/rh/CongeMailNotificationService.java`, `services/stockv2/BonMailNotificationService.java`, `services/reclamation/ReclamationMailNotificationService.java`.
- SMS et WhatsApp : non trouvé (aucune dépendance, aucun service).
- Persistance in-app (lu / non lu) : non trouvé.

#### 5.9 Import et export Excel, PDF, Word — **Spécifiques**

- Backend : Apache POI 5.4.0 et OpenPDF 1.3.30 (`build.gradle`). Exports dans `services/PointageServices.java`, `services/rh/DeclarationSocialeService.java`, `RecapitulatifMensuelService.java`, `BulletinPaieService.java`, `services/terrain/InterventionPdfService.java`, `RegistrePhytoPdfService.java`. Routes : `/api/pointages/historique/export/{excel,pdf}`, `/api/.../declarations/export/{pdf,excel}`, récapitulatif, bulletin, intervention, registre phyto.
- Frontend : `xlsx` 0.18.5 (version de SheetJS sur npm, connue pour des vulnérabilités), `jspdf`, `jspdf-autotable`, `file-saver`. 12 fichiers importent `xlsx` et 12 `jspdf`, un service par module (`stock-v2-export.service.ts`, `terrain-export.service.ts`, `production-export.service.ts`, `pointage-export.service.ts`…).
- Imports : `EmployeCompletController` `/import-excel` (legacy), `DossierEmployeController` `/bulk` (stratégies TOUT_OU_RIEN ou IMPORTER_LIGNES_VALIDES), `ProduitStockController` `/bulk`, front `import-employe-excel.service.ts`, `stock-v2-import-excel.service.ts`, `terrain-import-excel.service.ts` (sites). Annulation d'un import en bloc : non trouvé.
- Word : non trouvé. Filtrage de l'export selon les droits : non trouvé.

#### 5.10 Pièces jointes et stockage — **Partiel** (en base, dans le document)

- GridFS, disque, S3 : non trouvé.
- Fichiers en `byte[]` dans le document Mongo (limite de 16 Mo par document) : `EmployeComplet.photo` et `.contrat`, `DossierEmploye.photo`, `Contrat.fichierContrat`, `DocumentEmploye.fichier`, `PieceJointeSanction.contenu`, `PieceJustificative.data`, `ProduitStock.photo` et `.ficheTechnique`, `MatierePremiere.ficheSecurite`, `PhotoControle`, `PhotoReclamation`, `PhotoControleFichier`, `PhotoInterventionFichier`, `SiteClient.cahierDesChargesFichier`, `Produit.image`. 13 contrôleurs reçoivent du `MultipartFile`.
- Composant Angular réutilisable : non trouvé.

#### 5.11 Tâches `@Scheduled` — liste complète

| Tâche | Fréquence | Preuve |
|---|---|---|
| Rafraîchir les statuts d'affectation | chaque minute, `Africa/Dakar` | `services/terrain/AffectationStatutScheduler.rafraichirStatuts` |
| Détecter retards, absences, départs prématurés et escalader | toutes les 5 min (délai initial 1 min) | `services/terrain/DetectionAlertesJob.detecter` |

`@EnableScheduling` dans `PointageCleanicApplication.java`. Pas de `@EnableAsync`. 12 `CommandLineRunner` de démarrage dans `DataLoader/` (seed et migrations, `@Order` 900 à 1300). Aucune purge, sauvegarde, récap 18 h ni acquisition mensuelle planifiée.

#### 5.12 Multi-entreprises — **Absent**

`tenantId`, `entrepriseId`, `societeId` : aucune occurrence. Valeurs Cleanic en dur (e-mail HTML de `ResetPasswordService`, grille « Grille générique Cleanic » dans `DataLoader`, CORS).

#### 5.13 Tests — **Partiel**

- Backend : 942 `@Test` ou `@ParameterizedTest` dans 123 fichiers (`src/test/java`) : services 620 (RH 362, Stock v2 93, Terrain 36, pointage 31, réclamation 23, production chimie 22), contrôleurs 239, `DataLoader` 27, repositories 26, util 25, sécurité 2 (`JwtRhClaimTest`, `JwtStockClaimTest`). Testcontainers MongoDB (`config/MongoTestContainer.java`).
- Sans test : les 10 contrôleurs de production chimie (0 test), 13 contrôleurs Stock v2 sur 18, 7 contrôleurs Terrain sur 10. Aucun test ne vérifie qu'une route est refusée à un rôle.
- Frontend : 43 fichiers `*.spec.ts`, 347 `it(`. Services 161, sidebar 29, gestion-privilege 12, super-admin 8, notification 6, guard 6, header 4. **Aucun spec** dans `adminPage/stock-v2` ni `adminPage/exploitation-v2/production-chimie` ; 1 seul dans `ressources-humaines`. Pas de tests e2e.
- Couverture : non mesurée (aucun rapport JaCoCo ni configuration de couverture trouvés).

## Module RH (gestion du personnel, temps et présences, paie, développement RH, réclamations, Mon espace)

Chemins de base : back `src/main/java/com/example/Pointage_Cleanic/` (abrégé `back:`), front `src/app/` (abrégé `front:`).
Volume : 19 contrôleurs RH (1 718 lignes), 41 entités, 20 repositories, 36 services (8 273 lignes), 53 DTO, 34 enums. Front : 66 fichiers TS dans `adminPage/ressources-humaines/`, une vingtaine de services dans `services/`.
Statut : module **v2** (aucun doublon legacy à l'intérieur du RH), mais il coexiste avec deux modèles d'employé legacy (voir « Données dupliquées »).

### Collections MongoDB

| Collection | Entité | Champs principaux | Références | Index déclarés |
|---|---|---|---|---|
| `dossiers_employes` | `entities/rh/DossierEmploye` | matricule, agentId (code 4 chiffres du pointage), identité, genre, enfants (liste), poste, departement, siteAffecte, affectations (liste `AffectationSite` incorporée), dateEmbauche, statut (ACTIF, EN_PERIODE_ESSAI, SUSPENDU, SORTI), supérieur, categorieCode, n° IPRES/CSS, RIB, photo (byte[]) | categorieCode → `categories_professionnelles.code` , superieurHierarchiqueId → dossier , site en texte libre | matricule unique, agentId unique sparse, email |
| `contrats` | `Contrat` | employeId + nom/prénom copiés, typeContrat (CDI, CDD, STAGE, PRESTATION), dateDebut/Fin, statut, clauses, joursAvantAlerte, renouvellements[], avenants[], fichierContrat (byte[]) | employeId | employeId |
| `documents_employes` | `DocumentEmploye` | employeId, nom, categorie, fichier (byte[]), dateExpiration, statut | employeId | employeId |
| `conges` | `DemandeConge` | employeId + identité copiée, type (`TypeConge`), dates, nombreJours, statut, decisionSuperieur/Rh/Dg, historique[] | employeId, superieurHierarchiqueId | employeId, superieurHierarchiqueId |
| `rh_absences` | `RhAbsence` | employeId + identité copiée, type (`TypeAbsence`), dates, statut, pieceJustificative | employeId | employeId |
| `heures_supplementaires` | `HeureSupplementaire` | employeId, date, heures, typeMajoration (T_15/40/60/100), tauxMajoration, statut | employeId | employeId |
| `rh_jours_feries` | `JourFerie` | date, libellé | — | date unique |
| `parametres_conges` | `ParametresConges` | joursAcquisParMois, suppléments enfants, paliers d'ancienneté, dateModification | singleton | — |
| `parametres_paie` | `ParametresPaie` | taux IPRES général/complémentaire, plafonds, taux CSS PF, plafond CSS, AT/MP défaut, TRIMF mensuel, barème IR (tranches), jours ouvrables | singleton | — |
| `categories_professionnelles` | `CategorieProfessionnelle` | code, salaireBase, primes[], indemnites[], prets[], avancesSurSalaire[], retenues[], regimeIpres, tauxAtMp, n° IPRES/CSS, RIB, banque | — | code unique |
| `bulletins_paie` | `BulletinPaie` | employeId + identité copiée, periode, jours, HS, lignes[], snapshots prêts/avances/retenues, brut, cotisations, IR, TRIMF, net, coût employeur, cumuls, statut (BROUILLON, VALIDE, PAYE, ANNULE) | employeId, categorieCode | employeId , (employeId, periode.annee, periode.mois) non unique |
| `declarations_sociales` | `DeclarationSociale` | type (IPRES/CSS mensuelle ou annuelle, INSPECTION_TRAVAIL), mois, année, lignes[], totaux, statut, référence externe | lignes → employés | aucun |
| `sanctions` | `Sanction` | employeId, type (AVERTISSEMENT, BLAME, MISE_A_PIED, LICENCIEMENT), dates (faits, convocation, entretien, notification), piecesJointes[], statut | employeId | employeId , (employeId, type, dateSanction) |
| `formations`, `sessions_formation`, `participations_formation`, `evaluations_formation`, `besoins_formation` | `Formation`, `SessionFormation`… | catalogue, sessions, présence, complétion, évaluation à chaud, besoins | formationId, sessionId, employeId | sessionId, employeId, (sessionId, employeId) |
| `evaluations_periodiques`, `grilles_evaluation` | `EvaluationPeriodique`, `GrilleEvaluation` | auto-évaluation, évaluation manager, validation, critères | employeId, grilleId | employeId , (employeId, période) |
| `reclamations` | `entities/reclamation/Reclamation` | réclamation client publique (QR de site) traitée côté RH | site | non vérifié |

Stockage des fichiers : tous en `byte[]` dans le document Mongo (photo, contrat, documents, justificatifs), pas de GridFS (`DossierEmploye.photo`, `Contrat.fichierContrat`, `DocumentEmploye.fichier`).

### API REST

**Sécurité commune** : aucun `@PreAuthorize` ; `security/SecurityConfig` applique `.anyRequest().authenticated()` (commentaire explicite lignes 127-130). Les contrôles de rôle existent **uniquement dans le sous-module congés et jours fériés**, codés dans les services (`services/rh/CongeIdentiteService.estRh()/estSuperAdmin()/niveauxValidables()`, `ParametresCongesService.updateParametres`, `JourFerieService`). Partout ailleurs : « authentifié » suffit (y compris paie et salaires). Le chemin public `/api/dossier-employe/*/photo` est en `permitAll`.

**gestionpersonnel/DossierEmployeController** — `/api/gestion-personnel/employes` (rôle : authentifié)
| Méthode | Chemin | Rôle métier |
|---|---|---|
| GET | `/` | Liste paginée filtrée des dossiers |
| GET | `/{id}` | Fiche employé |
| POST | `/` (multipart) | Création avec photo |
| PUT | `/{id}` (multipart) | Modification |
| DELETE | `/{id}` | **Suppression physique** du dossier |
| PUT | `/{id}/statut` | Changement de statut (dont SORTI) |
| GET | `/{id}/photo` | Photo |
| POST | `/bulk` | Import en masse (JSON issu d'Excel côté front, max 1 000) |

**ContratController** — `/api/gestion-personnel/contrats` : GET `/alertes` (échéances), GET `/`, GET `/{id}`, GET `/employe/{employeId}`, POST `/` (multipart), PUT `/{id}`, **DELETE `/{id}`**, GET/POST `/{id}/renouvellements|renouveler`, GET/POST `/{id}/avenants`, PUT `/{id}/resilier`, GET/DELETE `/{id}/fichier`.

**DocumentEmployeController** — `/api/gestion-personnel/documents` : GET `/`, GET `/employe/{id}`, GET `/{id}`, POST (upload), PUT `/{id}`, PUT `/{id}/valider`, **DELETE `/{id}`**, GET `/{id}/telecharger`.

**OrganigrammeController** — `/api/gestion-personnel/organigramme` : GET `/departements`, GET `/` (arbre hiérarchique).

**tempspresences/TempsPresencesPointageController** — `/api/temps-presences/pointages` : GET `/` (pointage centralisé paginé, statut calculé), GET `/resume` (compteurs du jour).

**TempsPresencesAbsenceController** — `/api/temps-presences/absences` : GET, GET `/{id}`, GET `/employe/{id}`, POST, PUT, **DELETE `/{id}`**, POST/GET `/{id}/justificatif`.

**TempsPresencesCongeController** — `/api/temps-presences/conges` (garde par service) : GET/PUT `/parametres` (PUT : RH ou super-admin), GET `/moi`, GET `/employes-selectionnables`, GET `/soldes`, `/soldes/{employeId}`, `/soldes/moi`, GET `/demandes`, `/demandes/mes-demandes`, `/demandes/a-valider`, `/demandes/a-valider/compteurs`, `/demandes/{id}`, `/demandes/employe/{id}`, POST `/demandes`, PUT `/demandes/{id}`, **DELETE `/demandes/{id}`**, POST `/demandes/{id}/valider|approuver|refuser` (niveau courant du circuit supérieur → RH → DG).

**TempsPresencesHeureSupController** — `/api/temps-presences/heures-supplementaires` : GET, GET `/{id}`, GET `/employe/{id}`, POST, PUT, **DELETE `/{id}`**, POST `/{id}/valider|refuser`.

**TempsPresencesJourFerieController** — `/api/temps-presences/jours-feries` : GET (?annee), POST, PUT, **DELETE `/{id}`** (RH/super-admin), POST `/dupliquer`.

**TempsPresencesRecapController** — `/api/temps-presences/recapitulatif` : GET (récap mensuel), GET `/export/excel`, GET `/export/pdf`.

**paie/BulletinPaieController** — `/api/paie/bulletins` : POST `/calculer` (un employé, un mois), GET `/historique`, GET `/`, GET `/{id}`, PUT `/{id}`, **DELETE `/{id}` (BROUILLON seulement)**, PUT `/{id}/valider`, `/{id}/payer`, `/{id}/annuler`, GET `/{id}/pdf` (PDF serveur OpenPDF).

**DeclarationSocialeController** — `/api/paie/declarations-sociales` : GET `/ipres`, GET `/css` (génération à partir des bulletins VALIDE/PAYE), POST (enregistrer), **DELETE `/{id}` (sans condition de statut)**, GET `/export/pdf`, `/export/excel`, GET `/`, GET `/{id}`, PUT `/{id}/transmettre`.

**GrilleSalarialeController** — `/api/paie/grille-salariale` : CRUD complet dont **DELETE `/{id}`**. **ParametresPaieController** — `/api/paie/parametres` : GET, PUT (écrase le singleton, sans contrôle de rôle).

**developpementrh/** — `/api/developpement-rh/` : `formations` (CRUD + sessions + participants + présence + complétion + évaluations, DELETE formation et session), `formations/besoins` (CRUD + statut, DELETE), `evaluations` (CRUD + auto-évaluer, évaluer-manager, valider, DELETE), `sanctions` (CRUD + statut + historique + alertes-recidive, DELETE). **TableauBordRhController** `/api/developpement-rh/tableau-de-bord` : GET KPI.

**reclamation/ReclamationController** — `/api/reclamations` : GET, GET `/compteurs`, GET `/{id}`, GET `/{id}/photos/{index}`, PATCH `/{id}/statut`. `ReclamationPubliqueController` `/api/reclamations-publiques` : GET `/site`, POST (public).

### Frontend

| Élément | Preuve |
|---|---|
| Routes : ~70 routes `admin/rh/...` en lazy (`loadComponent`), sous le parent `admin` protégé par `AuthGuard` seul | `front:app.routes.ts` lignes 39-123 |
| Guard : `AuthGuard` vérifie connexion et `mustChangePassword`, aucun contrôle de module ni de rôle par route | `front:guards/auth.guard.ts` |
| Gestion du personnel : liste, formulaire, fiche (onglets contrat, congés, affectations, documents), import Excel, contrats + avenants, affectations, organigramme, documents | `front:adminPage/ressources-humaines/gestion-du-personnel/` |
| Temps et présences : pointage centralisé (filtres date, département, site, statut ; pas de temps réel), historique, absences, congés (calendrier, demande, validation, mes demandes, paramètres, détail), heures sup, récap mensuel, jours fériés | `front:adminPage/ressources-humaines/temps-et-presences/` |
| Paie : grille salariale, calcul de bulletin + aperçu, PDF, historique, déclarations | `front:adminPage/ressources-humaines/paie/` |
| Développement RH : sanctions, plan de formation (sessions, besoins, budget), évaluations et grilles, tableau de bord | `front:adminPage/ressources-humaines/developpement-rh/` |
| Réclamations (liste, détail) | `front:adminPage/ressources-humaines/reclamations/` |
| Services : dossier-employe, contrat, document-employe, organigramme, absence, conge, conge-permissions, parametres-conges, heure-supplementaire, jour-ferie, pointage-centralise, recapitulatif-mensuel, bulletin-paie, bulletin-pdf, grille-salariale, declaration-sociale, sanction, formation, evaluation, import-employe-excel, reclamation | `front:services/` |
| Temps réel : WebSocket (STOMP) utilisé seulement pour les notifications de congés | `front:.../calendrier-conges/*.component.ts` , `back:services/rh/CongeNotificationService` |
| Mon espace agent : **absent**. Le code PIN (`/code-pin`) sert au pointage, pas à la connexion | `front:page-code-pin/` , `front:services/page-code.service.ts` |

### Règles métier codées en dur

| Règle | Fichier | Valeur | Remarque |
|---|---|---|---|
| IPRES général salarié / employeur, plafond | `back:DataLoader/ParametresPaieDataLoader` | 5,6 % / 8,4 %, 432 000 F | semé en base, modifiable, **non daté** |
| IPRES complémentaire salarié / employeur, plafond | idem | 2,4 % / 3,6 %, 1 296 000 F | idem |
| CSS prestations familiales employeur, plafond | idem | 7 %, 63 000 F | idem |
| AT/MP par défaut | idem | 1 % | surchargeable par catégorie |
| TRIMF | idem | 900 F/mois forfaitaire | pas de barème par tranche de salaire |
| Barème IR annuel | idem | 0 % ≤ 630 000 , 20 % , 30 % , 35 % , 37 % , 40 % > 13,5 M | **sans quotient familial ni abattement** (`CalculPaieService.calculIrMensuel`) |
| Jours ouvrables standard | idem + repli `CalculPaieService` l.215 | 22 | |
| Heures par jour | `back:services/rh/CalculPaieService` `HEURES_PAR_JOUR_STANDARD` | 8,0 | constante Java |
| Majorations HS | `back:services/rh/HeureSupplementaireService` map `TAUX` + `Enum/rh/TypeMajoration` | 15, 40, 60, 100 % | enum, non paramétrable |
| Tolérance de retard | `back:services/rh/PointageCentraliseService` `@Value rh.pointage.tolerance-retard-minutes` | 15 min | propriété Spring, non datée, non par entreprise |
| Acquisition congés | `back:services/rh/ParametresCongesService` `@Value app.conges.jours-acquis-par-mois` + `BaremeConges` | 2 j/mois (24 j/an), +1 j/enfant < 14 ans (mères), ancienneté +1/+2/+3/+6 j à 10/15/20/25 ans | valeur de semis puis paramètre en base, non daté |
| Circuit de congés | `back:Enum/rh/NiveauValidationConge` | Supérieur → RH → DG | figé dans un enum |
| Motif de refus minimum | `back:services/rh/CongeWorkflowService` `MOTIF_REFUS_MIN_LENGTH` | 10 caractères | |
| Mise à pied max / délai convocation-entretien | `back:services/rh/SanctionService` | 8 j / 3 j | constantes |
| Récidive disciplinaire | `back:services/rh/SanctionService` | 2 sanctions sur 12 mois | constantes |
| Alerte échéance de contrat | `back:services/rh/ContratService` l.59 et l.168 | 30 j par défaut | |
| Import en masse | `back:services/rh/DossierEmployeService` | 1 000 lignes, 20 enfants max | |
| SMIG, IPM, CFCE | — | **non trouvé** | aucune occurrence dans le code |

### Données dupliquées avec d'autres modules

- **Employé en trois exemplaires** : `employes` (`back:entities/Employe`, legacy pointage : codeSecret, agentId, site[]), `employes_complet` (`back:entities/EmployeComplet` : agentId, matricule, horaires, agence[]) et `dossiers_employes` (RH, agentId + matricule). Le lien se fait par `agentId` (migration `back:DataLoader/DossierAgentIdMigrationRunner`).
- **Identité copiée** (nom, prénom, matricule, département) dans `conges`, `rh_absences`, `heures_supplementaires`, `contrats`, `documents_employes`, `bulletins_paie`, `sanctions` : rien n'est relu après un changement de nom.
- **Site en texte libre** : `DossierEmploye.siteAffecte` et `AffectationSite.site` sont des chaînes, sans référence à un référentiel de sites ; le planning des affectations est porté par le dossier RH (`AffectationSite` incorporé) et non par l'Exploitation.
- **Données bancaires et n° IPRES/CSS** présents sur le dossier **et** sur la catégorie ; le bulletin les copie depuis la **catégorie** (`CalculPaieService.orchestrer` l.366-369), ce qui est probablement un défaut.
- **Utilisateur ↔ employé** : rapprochement par e-mail non unique (`CongeIdentiteService.employeCourant`, avertissement si plusieurs dossiers).

### Suppressions physiques

`repository.delete(...)` ou `deleteById` dans : `DossierEmployeService` l.205, `ContratService` l.116, `DocumentEmployeService` l.141, `RhAbsenceService` l.151, `HeureSupplementaireService` l.134, `JourFerieService` l.119, `BulletinPaieService` l.101 (BROUILLON seulement), `DeclarationSocialeService` l.106 (**sans condition**), `CategorieProfessionnelleService` l.68, `SanctionService` l.90, `FormationService` l.75 et l.102, `BesoinFormationService` l.73, `EvaluationPeriodiqueService` l.104, plus la suppression de demande de congé (`TempsPresencesCongeController` DELETE). Les bulletins ont en revanche une annulation tracée (`/annuler`, statut ANNULE).

### Transactions, planification, paramètres datés

- `@Transactional` : aucun dans `services/rh` ni `controllers/rh`. `@Scheduled` : aucun (alertes de contrat et de récidive calculées à la demande).
- Paramètres : singletons `parametres_paie` et `parametres_conges` écrasés (seuls `dateModification` et auteur). Aucune date d'effet. Le bulletin conserve ses lignes et des snapshots (prêts, avances, retenues), donc un bulletin émis ne change pas, mais un recalcul utilise les taux du jour.
- Statuts de présence **calculés** à la volée depuis affectations + pointages + congés + fériés (`PointageCentraliseService`, `PlanningAffectationResolver`) : conforme au principe « rien de stocké ».

### Tests

- Back : 46 classes de test RH (19 tests de contrôleurs, 25 de services, `TypeCongeTest`, `JwtRhClaimTest`, `DossierAgentIdMigrationRunnerIT`), **512 méthodes `@Test`/`@ParameterizedTest`**. Paie couverte par `CalculPaieServiceTest` et `CalculPaieOrchestrerTest` ; aucune comparaison à des bulletins de référence du cabinet trouvée.
- Front : 1 spec dans `ressources-humaines/` (`note-bareme-conges.component.spec.ts`, 9 cas) et 4 specs de services RH (`bulletin-paie` 7, `conge-permissions` 26, `jour-ferie` 7, `parametres-conges` 5). Aucun test de composant pour la paie, le personnel, les sanctions ou les formations.

## Module Exploitation (legacy + v2) — inventaire technique (phase 2)

Périmètre audité en lecture seule :
- back : `controllers/PointagesController`, `controllers/ParametresPointageController`, `controllers/terrain/*`, `controllers/reclamation/*`, `entities/Pointage`, `entities/Employe`, `entities/PointageRequest`, `entities/pointage/*`, `entities/terrain/*`, `entities/reclamation/*`, `services/PointageServices`, `services/pointage/*`, `services/terrain/*`, `services/reclamation/*` (racine `back:` = `Pointage-Cleanic-Backend/src/main/java/com/example/Pointage_Cleanic/`).
- front : `adminPage/exploitation-v2/` (hors `production-chimie`), `home-page`, `page-code-pin`, `final-page1`, `final-page2`, `reclamation-publique` (racine `front:` = `pointage-agents/src/app/`).
- Les deux dépôts sont présents. Les dossiers `*-fix-telephone` ont été ignorés.

Il n'existe **pas de dossier `exploitation/` legacy côté front** : le legacy se limite au kiosque (`home-page`, `page-code-pin`, `final-page1/2`) et à des services orphelins (`services/planification.service.ts`). Côté back, le legacy est la collection `pointages` et son flux kiosque, plus les modèles `Employe` / `PointageRequest`.

### 1. Collections MongoDB

Constat transverse : les annotations `@Indexed` / `@CompoundIndex` sont **inopérantes** (auto-index-creation non activé, cf. commentaire de `back:configurations/TerrainIndexesConfig`). Seuls les index posés par code existent (colonne « Index réels »).

| Collection | Classe | Champs principaux | Références | Index réels |
|---|---|---|---|---|
| `pointages` (legacy, **seule alimentée** par le kiosque) | `entities/Pointage` | codeSecret (= agentId 4 chiffres), prenom, nom, site[] (copie des noms), date, heureArrive, heureDepart, duree (texte « 1h5mn »), status (« EN COURS... »/« TERMINÉ »), deviceId, timestamp, adresse, agenceId, lat/long/precision/distance (entrée), agenceIdSortie + mêmes champs sortie, anomalies[] | agenceId → sites_clients ; codeSecret → dossiers_employes.agentId | agenceId, agenceIdSortie (`DataLoader/AgenceQrTokenBackfillRunner`). Les 3 `@CompoundIndex` (dont `pointage_flux_idx`) ne sont pas créés |
| `pointage_request` (legacy) | `entities/PointageRequest` | employeeCode, deviceId, timestamp | — | aucun ; aucune référence hors `repositories/` trouvée |
| `employes` (legacy) | `entities/Employe` | codeSecret, agentId, site[], horaires matin/après-midi, deplacement, remplacement, personneRemplacee… | site par nom | non trouvé ; plus lu par le pointage (commentaire `PointageServices`) |
| `parametres_pointage` (singleton) | `entities/pointage/ParametresPointage` | horsAffectation (BLOQUER/AVERTIR/AUTORISER), qrObligatoire, dateModification, modifieParId/Nom | — | — |
| `sites_clients` (= « agences ») | `entities/terrain/SiteClient` | code, nom, raisonSociale, adresse, ville, coordonnees{lat,lng}, rayonToleranceM, contacts, cahierDesCharges (texte + **PDF inline byte[]**), surfaceM2, frequencePassage, nombreMaxEmployes, actif, qrToken/qrVersion/qrGenereLe, reclamationToken/Version/GenereLe | — | qrToken, reclamationToken (backfill runner). `code` unique **non créé** |
| `terrain_affectations` | `entities/terrain/AffectationAgent` | employeId/Matricule/Nom, siteId/Code/Nom, dateDebut/dateFin (LocalDateTime), statut, remplaceAffectationId, motifRemplacement, motifAnnulation, annuleParNom | employeId → dossiers_employes ; siteId → sites_clients | statut_debut_fin, employe_id, site_statut (`TerrainIndexesConfig`) |
| `terrain_pointages` (v2, **non alimentée**) | `entities/terrain/PointageTerrain` | employeId, siteId, affectationId, type ENTREE/SORTIE, datePointage, positionAgent{lat,lng,precisionM,timestamp}, distanceM, statut SUR_SITE/HORS_ZONE/GPS_INDISPONIBLE/GPS_IMPRECIS | → affectations, sites | aucun |
| `terrain_alertes` | `entities/terrain/AlerteTerrain` | type RETARD/ABSENCE/POINTAGE_HORS_ZONE/DEPART_PREMATURE, niveauActuel, statut, employe*, site*, affectationId, pointageId, historiqueEscalade[], destinataireActuelId, resoluPar*, dateResolution | → affectations, terrain_pointages, utilisateur | aucun |
| `terrain_parametres_escalade` (singleton) | `ParametresEscalade` | delaiRetardMinutes, delaiAbsenceMinutes, delaiEscaladeNiveau1/2Minutes, superviseursIds[], responsablesOperationnelsIds[], directionGeneraleIds[] | → utilisateur | — |
| `terrain_interventions` | `FicheIntervention` | numero, site*, affectationId, employe*, dateDebut/Fin, duree, taches[], produits[], observations, photos[] (**byte[] inline**), signatureClient, statut | → sites, affectations | aucun |
| `terrain_grilles_evaluation` | `GrilleEvaluationTerrain` | nom, siteId, criteres[{libelle, poids, noteMin, noteMax, obligatoire}], noteSeuilConformite, actif | → sites | aucun |
| `terrain_controles` | `ControleQualiteTerrain` | site*, grilleId, dateControle, controleur*, notations[], noteGlobale, decision, commentaire, photos[] inline | → sites, grilles | aucun |
| `terrain_materiel` | `Materiel` | code, nom, type (10 types, aucun véhicule), marque, modele, numeroSerie, prixAcquisition, siteAffecte*, statut, intervalleMaintenanceJours, derniere/prochaineMaintenance | → sites | non vérifié (1 annotation, inopérante) |
| `terrain_evenements_materiel` | `EvenementMateriel` | materielId, type, date, siteAvantId/siteApresId, cout, technicien*, resolu | → materiel | aucun |
| `terrain_maintenances_programmees` | `MaintenanceProgrammee` | materielId, dateProgrammee, type, realisee | → materiel | aucun |
| `produits_phyto` | `ProduitPhytosanitaire` | nomCommercial, matiereActive, categorie, numeroHomologation, delaiReentreeHeures, delaiAvantNouvelleApplicationJours, fdsUrl | — | inopérant |
| `applications_phyto` | `ApplicationPhyto` | numero, site*, employe*, produit*, dose, zoneTraitee, dateApplication, meteo, statut, dateFinReentree, dateProchaineApplicationAutorisee | → sites, produits | aucun |
| `compteurs_terrain` | `CompteurTerrain` | id (préfixe+jour), compteur | — | — |
| `reclamations` | `entities/reclamation/Reclamation` | siteId, siteNom, texte, nom, trancheAge, photos[], dateReception, statut NOUVELLE/EN_COURS/TRAITEE, commentaireInterne, traiteParNom | → sites | siteId, statut, dateReception (backfill runner) |

### 2. API REST

Sécurité : aucune autorisation par rôle dans `SecurityConfig` (tout `/api/terrain/**` est simplement `authenticated()`). Les seules gardes de rôle sont posées **dans les services** : RH/SUPERADMIN pour les QR (`services/pointage/AgenceQrAdminService.exigerAdministrateur`), les paramètres de pointage (`ParametresPointageService`, `ROLES_ADMIN`) et les réclamations (`ReclamationService.exigerAdministrateur`). Les drapeaux de sous-module `Terrain` (`entities/GestionModules/SousModules/Terrain`) ne sont lus que par le menu front (`adminPage/sidebar/sidebar.component.ts`), jamais par le back.

#### PointagesController `/api/pointages` (legacy, kiosque)
| Méthode | Chemin | Rôle requis | Rôle métier |
|---|---|---|---|
| POST | `/api/pointages` | **public** | Pointe l'arrivée ou le départ par code PIN + deviceId + GPS + QR facultatif |
| GET | `/agence-qr?payload=` | public | Nom de l'agence d'un QR avant la saisie du PIN |
| GET | `/api/pointages` | authentifié | Tous les pointages (findAll, sans pagination, renvoie 201) |
| GET | `/today` | authentifié | Pointages du jour paginés |
| GET | `/historique/search` | authentifié | Historique filtré (texte, période, agence ou « AUCUNE ») |
| GET | `/historique/export/excel` et `/pdf` | authentifié | Export Excel (POI) / PDF (OpenPDF) de l'historique |
| GET | `/{codeSecret}` | **public** | Dernier pointage d'un agent : nom, prénom, GPS, adresse exposés à qui connaît un code à 4 chiffres |

#### ParametresPointageController `/api/pointages/parametres`
| GET | `/` | authentifié | Lit le mode hors affectation et l'interrupteur QR obligatoire |
|---|---|---|---|
| PUT | `/` | RH / SUPERADMIN (service) | Modifie ces paramètres (trace auteur et date, valeur écrasée) |

#### AgencesQrController `/api/terrain/sites-clients`
| GET | `/qr-codes` | RH / SUPERADMIN | Liste des QR de pointage par agence (payload, URL, version) |
|---|---|---|---|
| POST | `/{id}/qr/regenerer` | RH / SUPERADMIN | Nouveau jeton, l'ancien QR cesse immédiatement de fonctionner |
| GET | `/qr-reclamation` | RH / SUPERADMIN | Liste des QR de réclamation |
| POST | `/{id}/qr-reclamation/regenerer` | RH / SUPERADMIN | Régénère le QR de réclamation |

#### SitesClientsController `/api/terrain/sites-clients`
| GET | `/` , `/actifs`, `/{id}` | authentifié | Liste paginée, sites actifs, fiche |
|---|---|---|---|
| GET | `/{id}/effectif` | authentifié | Effectif RH ou terrain comparé au plafond `nombreMaxEmployes` |
| POST / PUT | `/`, `/{id}` (multipart) | authentifié | Création / modification avec PDF du cahier des charges |
| DELETE | `/{id}` | authentifié | **Suppression physique** du site |
| GET | `/{id}/cahier-charges` | authentifié | Télécharge le PDF |

#### PlanningController `/api/terrain/planning`
| GET | `/affectations`, `/periode`, `/stats`, `/{id}` | authentifié | Planning paginé, par période, compteurs par statut |
|---|---|---|---|
| GET | `/affectations/conflits` | authentifié | Chevauchements d'un même agent |
| POST / PUT | `/affectations`, `/{id}` | authentifié | Crée / modifie une affectation (409 si chevauchement) |
| POST | `/affectations/{id}/annuler` | authentifié | Annulation motivée (pas de DELETE, retiré le 2026-07-21) |

#### PointagesTerrainController `/api/terrain/pointages` (v2, sans appelant)
| POST | `/` | authentifié | Pointage GPS sur affectation, statut anti-spoof, alerte HORS_ZONE |
|---|---|---|---|
| GET | `/jour`, `/aujourd-hui`, `/historique`, `/{id}` | authentifié | Lectures de `terrain_pointages` |

#### AlertesController `/api/terrain/alertes`
| GET | `/`, `/courantes`, `/{id}` | authentifié | Alertes paginées / ouvertes |
|---|---|---|---|
| GET | `/recap-quotidien` | authentifié | Récapitulatif d'un jour (à la demande) |
| GET / PUT | `/parametres` | authentifié (aucune garde de rôle) | Délais et destinataires d'escalade |
| POST | `/{id}/traiter`, `/justifier`, `/escalader` | authentifié | Cycle de vie d'une alerte |

#### InterventionsController `/api/terrain/interventions`
| GET | `/`, `/{id}`, `/{id}/photos/{index}`, `/{id}/pdf` | authentifié | Liste, fiche, photo, PDF |
|---|---|---|---|
| POST / PUT | multipart | authentifié | Fiche avec tâches, produits, photos, signature |
| DELETE | `/{id}` | authentifié | **Suppression physique** |

#### ControlesTerrainController `/api/terrain/controles-terrain`
| GET/POST/PUT | `/grilles…` | authentifié | Grilles d'évaluation (par site ou génériques) |
|---|---|---|---|
| DELETE | `/grilles/{id}` | authentifié | **Suppression physique** |
| GET | `/`, `/{id}`, `/historique/{siteId}`, `/{id}/photos/{index}` | authentifié | Contrôles et évolution de note |
| POST | `/` multipart | authentifié | Saisie d'un contrôle noté, décision calculée |

#### MaterielController `/api/terrain/materiel`
| GET/POST/PUT | `/`, `/{id}` | authentifié | Parc de matériel |
|---|---|---|---|
| DELETE | `/{id}` | authentifié | **Suppression physique** |
| GET/POST | `/alertes`, `/maintenance-programmee`, `/{id}/affecter`, `/{id}/historique`, `/{id}/panne`, `/{id}/maintenance` | authentifié | Alertes de maintenance, affectation à un site, événements |

#### PhytosanitaireController `/api/terrain/phytosanitaire`
| GET/POST/PUT | `/produits…`, `/applications…`, `/applications/periode` | authentifié | Produits homologués, applications, calendrier |
|---|---|---|---|
| DELETE | `/produits/{id}`, `/applications/{id}` | authentifié | **Suppression physique** |
| GET | `/alertes-delais`, `/registre/pdf` | authentifié | Délais de réentrée / réapplication, registre PDF |

#### TableauBordTerrainController `/api/terrain/tableau-bord`
| GET | `/rapport`, `/kpis`, `/interventions-par-site`, `/evolution-couverture`, `/incidents-par-site`, `/evolution-satisfaction`, `/satisfaction-par-site`, `/comparaison-periodes` | authentifié | KPI calculés à la volée (couverture = interventions réalisées / affectations) |
|---|---|---|---|

#### ReclamationController `/api/reclamations` et ReclamationPubliqueController `/api/reclamations-publiques`
| GET | `/api/reclamations`, `/compteurs`, `/{id}`, `/{id}/photos/{i}` | RH / SUPERADMIN | Suivi des réclamations |
|---|---|---|---|
| PATCH | `/api/reclamations/{id}/statut` | RH / SUPERADMIN | Changement de statut + commentaire interne |
| GET | `/api/reclamations-publiques/site?payload=` | public | Nom du site d'un QR de réclamation |
| POST | `/api/reclamations-publiques` multipart | public, limité (5 / appareil / 10 min, 30 / QR / h) | Dépôt d'une réclamation client avec photos |

Legacy mort : `SecurityConfig` déclare `/api/planification/**`, `/api/dashboard_par_agence` et `/pointages/**`, **aucun contrôleur** correspondant trouvé ; `front:services/planification.service.ts` les appelle encore (utilisé par `adminPage/super-admin`, composant non routé).

### 3. Frontend

Guard unique : `front:guards/auth.guard.ts` (authentification seule) sur `/admin` et `/change-password`. Aucune garde par module ou par rôle sur les routes.

| Zone | Routes | Composants | Services |
|---|---|---|---|
| Kiosque public (legacy) | `/`, `/home?qr=`, `/code-pin`, `/pagefinal1/:codeSecret`, `/pagefinal2/:codeSecret` | home-page (connexion admin + lecture QR), page-code-pin (formulaire PIN réactif, GPS haute précision), final-page1/2 | pointage.service, pointage-qr-contexte.service, page-code.service |
| Réclamation publique | `/reclamation?qr=` | reclamation-publique | reclamation.service |
| Accueil après connexion | `/admin/exploitation-v2/dashboard` | dashboard (lit `terrain_pointages`, vide) | terrain-pointage, terrain-planning… |
| Sites | `terrain/sites-clients`, `/qr-codes`, `/qr-reclamation`, `/nouveau`, `/:id`, `/:id/modifier` | liste-sites, formulaire-site, fiche-site, import-sites-modal (Excel), qr-codes-agences (PDF des affiches), qr-codes-reclamation | terrain-site-client, terrain-qr-agence, qr-agence-pdf, terrain-import-excel, terrain-google-maps |
| Planning | `terrain/planning/calendrier`, `/conflits` (+ affectations sous `rh/gestion-du-personnel/affectations`) | calendrier-planning, detection-conflits | terrain-planning |
| Pointage | `terrain/pointage/aujourd-hui`, `/historique-pin` (onglets) ; `/historique`, `/historique/:id` **hors menu** | suivi-pointages et historique-pointages-pin (legacy `pointages`), historique-pointages et fiche-pointage (`terrain_pointages`) | pointage, pointage-export, terrain-pointage |
| Alertes | `terrain/alertes`, `/recap`, `/parametres` | tableau-alertes, recapitulatif-quotidien, parametres-escalade | terrain-alerte, websocket |
| Interventions | `terrain/interventions…` | liste, formulaire (photos, signature-pad), détail | terrain-intervention, terrain-pdf |
| Contrôle qualité | `terrain/controles…` | liste, grilles, formulaire, historique-site, fiche | terrain-controle-qualite |
| Matériel | `terrain/materiel…` | liste, formulaire, suivi-maintenance, historique, 3 dialogues | terrain-materiel |
| Phytosanitaire | `terrain/phytosanitaire…` | calendrier, produits, registre, alertes-delais, formulaire-application | terrain-phytosanitaire |
| Tableau de bord | `terrain/tableau-bord` | tableau-bord-terrain | terrain-tableau-bord, terrain-export |

### 4. Règles métier codées en dur

| Règle | Valeur | Fichier |
|---|---|---|
| Rayon de tolérance par défaut (CDC : 200 m) | **100 m** | `back:services/terrain/TerrainConstantes.RAYON_TOLERANCE_DEFAUT_M` ; `front:constants/terrain.constants.ts` |
| Précision GPS jugée imprécise (pointage v2) | 50 m | `TerrainConstantes.PRECISION_GPS_MAX_M` |
| Anomalie « GPS imprécis » (kiosque) | 100 m | `TerrainConstantes.PRECISION_GPS_ANOMALIE_M` |
| Verrou anti-réutilisation d'un téléphone | 20 min | `back:controllers/PointagesController.pointer` (`canPoint(deviceId, 20)`) |
| Validité du QR scanné avant le PIN | 10 min | `front:constants/pointage.constants.ts` (`TTL_QR_POINTAGE_MS`) |
| Timeout GPS navigateur | 20 s | `front:page-code-pin/page-code-pin.component.ts` |
| Département pilotable par le terrain | « Opération » | `TerrainConstantes.DEPARTEMENT_EXPLOITATION` |
| Seuil de conformité d'un contrôle | 3,5 / 5 | `TerrainConstantes.NOTE_SEUIL_CONFORMITE_DEFAUT` (surchargeable par grille) |
| Retard / absence / escalade N1 / N2 (défauts) | 15 / 60 / 30 / 120 min | `services/terrain/AlerteService.getParametresEntity`, `DataLoader/TerrainDataLoader` (modifiables, non datés) |
| Fréquence du job d'alertes | 5 min | `services/terrain/DetectionAlertesJob` |
| Bascule des statuts d'affectation | chaque minute | `services/terrain/AffectationStatutScheduler` |
| Alertes de maintenance | ≤ 30 j info, ≤ 7 j attention, < 0 critique | `services/terrain/MaterielService` (l. 193-201) |
| Numérotation intervention / phyto | `PREFIXE-yyyyMMdd-%03d` (compteur journalier atomique) | `services/terrain/CompteurTerrainService` |
| Réclamation : texte, photos, débit | 10-2000 car., 5 photos de 5 Mo, 5 / appareil / 10 min, 30 / QR / h | `services/reclamation/ReclamationService`, `ReclamationLimiteurDebit` |
| Horaires par défaut du planning | 08:00 - 17:00 | `front:constants/terrain.constants.ts` |
| Mode hors affectation par défaut | AVERTIR ; QR obligatoire = faux | `services/pointage/ParametresPointageService` |
| Sites de démonstration semés en base (sans profil) | 2 sites, 1 grille, 1 produit | `DataLoader/TerrainDataLoader` (`@Component` sans `@Profile`) |

### 5. Recouvrement legacy / v2 et données dupliquées

1. **Deux collections de pointage.** Le kiosque écrit dans `pointages` (`PointageServices.pointer`). `terrain_pointages` n'a aucun appelant front (`front:services/terrain-pointage.service.ts` `create` jamais appelé). Or **le job d'alertes lit `terrain_pointages`** (`DetectionAlertesJob.pointagesJour`) : toute affectation terrain non couverte par un pointage v2 génère une alerte RETARD puis ABSENCE, même si l'agent a pointé au kiosque. Le tableau de bord d'accueil (`exploitation-v2/dashboard`) et l'écran `pointage/historique` lisent aussi cette collection vide. La RH (`services/rh/PointageCentraliseService`, `RecapitulatifMensuelService`) lit `pointages`.
2. **Trois représentations de l'affectation.** `dossiers_employes.affectations[]` (`entities/rh/AffectationSite`, site **par nom**, horaires, jours) et sa chaîne dérivée `siteAffecte` ; `terrain_affectations` (site **par id**, créneau daté) ; legacy `employes.site[]`. Le contrôle hors affectation du kiosque compare des **noms** (`RattachementSiteUtils.estAffecteAuSite(agent, agence.getNom(), …)`) : renommer un site casse le rattachement.
3. **Site.** `sites_clients` est l'unique référentiel, mais les noms sont recopiés partout (`Pointage.site[]`, `employeNom/siteNom` dans toutes les collections terrain, `EmployeComplet.agence[]/codeSite`). Le plafond `nombreMaxEmployes` est compté sur deux périmètres différents (`EffectifSiteService` : RH par nom, terrain par id).
4. **Rôle utilisateur** lu dans deux collections (`utilisateur` puis `login`, `CurrentUserProvider.currentRole`).
5. Écrans legacy encore routés : tout le kiosque ; `historique-pin` et `aujourd-hui` (v2 dans le menu) lisent la collection legacy. Écrans v2 sans données : `pointage/historique`, `fiche-pointage`, bloc présence du dashboard.

### 6. Suppressions physiques

`repository.delete` sans contrôle de référence : `SiteClientService.delete` (site pourtant référencé par affectations, pointages, réclamations), `InterventionService.delete`, `MaterielService.delete`, `ControleQualiteService` (grilles), `PhytosanitaireService` (produits et applications). Seule l'affectation est protégée (annulation tracée, `PlanningService.annuler`). Aucune route DELETE sur `pointages` (pas de correction tracée non plus).

### 7. Tests

- Back (`src/test/java/...`) : environ **152 tests** dans le périmètre (pointage kiosque et QR : `PointageScanServiceTest` 18, `PointagesControllerTest` 17, `PointageServicesQrTest` 9, `PointageServicesTest` 6, `PointageConcurrenceIT`, `PointageHistoriqueAgenceIT`, `AgenceQr*` ; réclamation 30 ; planning, effectif et scheduler 45 ; `PointageTerrainServiceTest` 2 ; `SitesClientsControllerTest` 2).
- **Sans aucun test** : `DetectionAlertesJob`, `AlerteService`, `ControleQualiteService`, `InterventionService`, `MaterielService`, `PhytosanitaireService`, `TableauBordTerrainService`.
- Front : **69 tests** (`home-page` 13, `planification.service` 11 sur un back inexistant, `pointage-qr-contexte` 7, `historique-pointages-pin` 6, `pointage.service` 6, `reclamation.service` 6, `qr-codes-agences` 5, `page-code-pin` 5, `reclamation-publique` 4, divers 6). Aucun test sur les écrans terrain hors QR et historique PIN.

### 8. Pointage QR (sujet transverse)

| Point | Statut | Preuve |
|---|---|---|
| QR par agence | **Présent** | jeton aléatoire 24 octets par `SiteClient` (`services/pointage/AgenceQrService.genererToken`), payload `POINTIC:AG:<token>`, URL `/home?qr=` ; affiches PDF (`front:services/qr-agence-pdf.service.ts`) |
| QR par local | **Absent** | aucune entité « local » ou zone dans `SiteClient` |
| Régénération qui révoque l'ancien | **Présent** | `AgenceQrService.regenerer` (nouveau jeton, version +1) |
| QR d'appréciation | **Partiel** | QR de réclamation distinct (`ReclamationQrService`, `POINTIC:RC:`) mais texte libre, sans note |
| GPS + contrôle de rayon | **Partiel** | Haversine contre le site du QR, refus 422 au-delà du rayon (`PointageScanService.evaluer`) ; défaut 100 m au lieu de 200 ; **sans QR (mode par défaut) aucune géofence**, seule l'adresse est géocodée |
| Multi-passages | **Présent** | plusieurs couples entrée/sortie par jour, clôture atomique `findAndModify` (`PointageServices.pointer`), test `PointageConcurrenceIT` |
| Heure serveur | **Présent (fragile)** | heure prise côté serveur, mais via `LocalDate.now()`/`LocalTime.now()` (fuseau JVM) et non le `Clock` Dakar de `configurations/TimeConfig` ; le Dockerfile ne fixe pas `TZ` |
| Liaison au téléphone | **Absent** | deviceId = UUID en `localStorage` (`front:services/pointage.service.ts`) servant seulement d'anti-réutilisation 20 min ; aucun appairage agent-téléphone ; effacer le stockage contourne le contrôle |
| Photo aléatoire / photo sans GPS | **Absent** | aucune photo dans le flux de pointage |
| Mode hors ligne | **Absent** | appel HTTP direct, aucune file locale (`page-code-pin.component.ts.pointer`) |
| Pointage par chef d'équipe / superviseur | **Absent** | le verrou de 20 min par téléphone empêche même plusieurs agents sur un même téléphone |
| Hors affectation | **Présent** | BLOQUER / AVERTIR / AUTORISER (`ParametresPointage`), anomalies tracées |
| Confidentialité | **Risque** | `GET /api/pointages/{codeSecret}` public renvoie nom, GPS et adresse ; le PIN de 4 chiffres est aussi l'identifiant |

### 9. PWA

| Point | Statut | Preuve |
|---|---|---|
| Service worker | **Absent** | aucun `serviceWorker` / `ngsw` dans `angular.json`, `app.config.ts`, `main.ts` ; `@angular/service-worker` absent de `package.json` |
| Manifest | **Absent** | aucun `manifest.webmanifest`, aucun lien dans `src/index.html` |
| IndexedDB | **Absent** | aucune occurrence de `indexedDB`, `idb`, `dexie`, `localforage` dans `src/` |
| Stockage local utilisé | localStorage (deviceId), sessionStorage (QR 10 min) | `services/pointage.service.ts`, `services/pointage-qr-contexte.service.ts` |
| Lecteur QR intégré | **Absent** | lecture via l'appareil photo natif qui ouvre l'URL ; seules des bibliothèques de génération (`qrcode`, `angularx-qrcode`) |

## Module Stock (legacy `stock/` et `stockv2`)

Préfixes : `back:` = `Pointage-Cleanic-Backend/src/main/java/com/example/Pointage_Cleanic/`, `front:` = `pointage-agents/src/app/`. Les dossiers `*-fix-telephone` sont ignorés.

### Recouvrement legacy / v2

| Élément | Legacy (`stock/`) | v2 (`stockv2`) | Constat |
|---|---|---|---|
| Article | `back:entities/stock/Produit.java` (collection `produits`, image en `byte[]`, `prixDeVente`, `seuilMinimum`, `quantiteSnapshot`) | `back:entities/stockv2/ProduitStock.java` (collection `stockv2_produits`) | Deux catalogues d'articles sans lien ni migration trouvée entre eux |
| API | `back:controllers/ProduitController.java` (`/api/produits`, CRUD + `DELETE /{id}`) via `back:services/ProduitService.java`, `back:repositories/ProduitRepository*.java` | `/api/stock/**` (19 contrôleurs) | L'API legacy est toujours exposée |
| Front | Aucun écran, aucune route, aucun appel à `/api/produits` (recherche dans `front:services/` et `front:app.routes.ts`). Seul reste : `front:models/entreeStock.model.ts`, importé nulle part | `front:adminPage/stock-v2/**`, routes `stock-v2/...` (`front:app.routes.ts`, lignes 233-308), menu `front:adminPage/sidebar/sidebar.component.html` | Seule la v2 est routée et présente dans le menu |
| Autres usages du legacy | `back:controllers/EmployeCompletController.java` importe `Produit` et `ProduitDto` sans les utiliser (imports seulement) | — | Code mort |

Conclusion : le legacy est un **backend orphelin** (collection `produits` + API ouverte) sans écran. Aucun double flux actif, mais une **collection en double** à archiver ou migrer, et une route DELETE encore ouverte.

Autres « stocks » parallèles hors périmètre Stock, qui recoupent le catalogue :
- `production_chimie_matieres_premieres` + `production_chimie_mouvements_stock` (`back:entities/productionchimie/MatierePremiere.java`, `MouvementStockChimie.java`) : second stock de matières, avec son propre écran `front:adminPage/exploitation-v2/production-chimie/matieres-premieres/mouvements-stock-chimie/` ;
- `produits_phyto` (`back:entities/terrain/ProduitPhytosanitaire.java`) ;
- `terrain_materiel` (`back:entities/terrain/Materiel.java`).

Aucun de ces modules ne lit `stockv2_produits` : la recherche de `ProduitStockRepository` ou de `stockv2` hors des dossiers stockv2 ne renvoie rien.

### Collections MongoDB

| Collection | Classe | Champs principaux | Références | Index |
|---|---|---|---|---|
| `produits` (legacy) | `entities/stock/Produit` | codeProduit, nomProduit, categorie (texte), destination[], image, prixDeVente, seuilMinimum, statut, quantiteSnapshot | aucune | `search_idx` composé (text nom/code + categorie + destination) |
| `stockv2_produits` | `ProduitStock` | code, libelle, typeProduit, categorieId, sousCategorie, unite, fournisseurPrincipal (texte), seuilAlerte, prixUnitaire (coût courant), methodeValorisation, prixVente, photo et ficheTechnique en `byte[]`, actif | categorieId → `stockv2_categories` | code unique, categorieId, actif |
| `stockv2_categories` | `CategorieStock` | libelle, parentId, niveau | parentId (arbre) | parentId |
| `stockv2_etats_stock` | `StockParSite` | produitId, siteId (null = entrepôt), quantite, seuilAlerteOverride, dateMaj | produit, site | `produit_site_unique` (produitId, siteId) unique |
| `stockv2_mouvements` | `MouvementStock` | reference, produitId (+code, libellé, unité recopiés), type, motif, quantite, siteSource/Destination (+noms), date, bonId, inventaireId, chantierId, natureDon, coutUnitaireSnapshot, valeurMouvement, origine | produit, bon, inventaire, chantier, site | reference unique, produitId, siteSourceId, siteDestinationId, date, bonId, inventaireId, chantierId |
| `stockv2_bons_entree` | `BonEntree` | reference, type, date, siteDestinationId/Nom, fournisseur (texte), referenceCommande (texte), lignes[] (`LigneBon`), statut, demandeur, creePar*, validateur, motifRefus, historique[], montantTotal | site, produits | reference unique sparse |
| `stockv2_bons_sortie` | `BonSortie` | idem + siteSource, destinataire (`DestinataireBon` : SITE / AGENT / CLIENT texte), motif, natureDon, beneficiaireDon, chantierId | site, employé, chantier | reference unique sparse |
| `stockv2_inventaires` | `Inventaire` | reference, datePlanifiee, siteId, perimetre, categorieId, seuilEcartJustification, statut, lignes[] (qteTheorique, qtePhysique, ecart, justification), responsable, dateCloture | site, catégorie | reference unique, siteId, statut |
| `stockv2_chantiers` | `Chantier` | reference, nom, siteId/Nom, client (texte), dates, statut, coutTotal, nbMouvements | site | reference unique |
| `stockv2_plafonds` | `Plafond` | siteId, granularite, cibleId, plafondMensuel, actif | site | siteId |
| `stockv2_historique_cout` | `HistoriquePointCout` | produitId, date, cout, methode, referenceMouvement | produit | produitId |
| `stockv2_parametrage_valorisation` | `ParametrageValorisation` | methodeDefaut, updatedBy | — | — |
| `stockv2_compteurs` | `CompteurStock` | _id = `PREFIXE-yyyyMMdd` ou `PREFIXE-yyyy`, compteur | — | _id |
| `stockv2_suppressions` | `SuppressionStockLog` | typeDocument, documentId, reference, statutAvant, motif, auteur, date, lignes contre-passées | document supprimé | documentId |

Aucun `@Version` (verrou optimiste) dans `entities/stockv2`.

### Données dupliquées

- **Article** : `produits` (legacy), `stockv2_produits`, `production_chimie_matieres_premieres`, `produits_phyto`, `terrain_materiel`. Le code, le libellé et l'unité sont aussi recopiés dans `LigneBon`, `LigneInventaire` et `MouvementStock`. C'est un instantané voulu, mais il n'est jamais resynchronisé.
- **Fournisseur** : aucune entité Fournisseur. C'est du texte libre dans `ProduitStock.fournisseurPrincipal` et `BonEntree.fournisseur`.
- **Site** : référence `SiteClient` via `back:services/stockv2/ReferentielSiteService.java` (`siteClientRepository.findById`), avec le nom recopié (`siteSourceNom`, `siteDestinationNom`, `siteNom`).
- **Employé** : `DossierEmploye` via `back:services/stockv2/ReferentielEmployeStockService.java`, avec le nom recopié (`agentNom`, `demandeurNom`).
- **Client** : du texte libre (`DestinataireBon.clientNom`, `Chantier.client`), sans lien vers le référentiel client.
- **États stockés** : `Chantier.coutTotal` et `nbMouvements`, `BonX.montantTotal`, `ProduitStock.prixUnitaire` (coût courant écrasé), `StockParSite.quantite` (solde stocké, pas recalculé depuis les mouvements).

### API REST (`/api/stock/**`)

Rôle requis : la sécurité HTTP se limite à `.authenticated()`, sans aucun `@PreAuthorize` (javadoc de `back:services/stockv2/HabilitationStock.java`). Les contrôles se font dans les services : `exigerSuperAdmin` (SUPERADMIN), `exigerControleurOuSuperAdmin` (CONTROLEUR_STOCK ou SUPERADMIN) et `exigerCreateurOuControleur`. Le claim JWT `modules.stock.*` (`back:entities/GestionModules/SousModules/Stock.java`) n'est **lu que par le front**. « Auth » ci-dessous veut dire : tout compte authentifié.

**ProduitStockController** `/api/stock/produits`
| Méthode | Chemin | Rôle | Rôle métier |
|---|---|---|---|
| GET | `/`, `/actifs`, `/{id}`, `/{id}/photo`, `/{id}/fiche-technique` | Auth | Catalogue, fiche, pièces jointes |
| POST | `/` (multipart) | Auth | Créer un article (avec stock initial) |
| PUT | `/{id}` | Auth | Modifier un article |
| DELETE | `/{id}` | Contrôleur/SA | **Suppression physique** de l'article (`ProduitStockService.delete`) |
| PATCH | `/{id}/valorisation`, `/{id}/prix-vente` | Auth | Méthode de valorisation, prix de vente |
| POST | `/bulk` | Auth | Import en masse (avec compensation manuelle) |

**BonEntreeController** `/api/stock/bons-entree` et **BonSortieController** `/api/stock/bons-sortie`
| Méthode | Chemin | Rôle | Rôle métier |
|---|---|---|---|
| GET | `/`, `/{id}` | Auth (aucun filtre par auteur côté serveur) | Liste et fiche |
| POST / PUT | `/`, `/{id}` | Auth / créateur ou contrôleur | Brouillon : création, modification |
| DELETE | `/{id}` | Créateur ou contrôleur, en BROUILLON | **Suppression physique** (`repository.deleteById`) |
| POST | `/{id}/soumettre` | Créateur ou contrôleur | BROUILLON → SOUMIS |
| POST | `/{id}/reprendre` (sortie seulement) | Créateur ou contrôleur | REFUSE → BROUILLON |
| POST | `/{id}/valider` | SUPERADMIN | SOUMIS → EFFECTIF + mouvements |
| POST | `/{id}/refuser` | SUPERADMIN | SOUMIS → REFUSE (motif obligatoire) |
| POST | `/{id}/suppression-definitive` | SUPERADMIN | Contre-passe, journalise, puis **supprime le bon et ses mouvements** |

**InventaireController** `/api/stock/inventaires` : GET `/`, `/{id}` ; POST/PUT `/` ; DELETE `/{id}` (BROUILLON, physique) ; POST `/{id}/suppression-definitive` (SA) ; POST `/{id}/comptage` (BROUILLON → COMPTAGE, fige le théorique) ; PUT `/{id}/comptage` (saisie) ; POST `/{id}/validation` (écarts au-delà du seuil justifiés) ; POST `/{id}/cloture` (Contrôleur/SA, ajustements).

**CategorieStockController** `/api/stock/categories` : GET `/racines`, `/enfants`, `/`, `/{id}` ; POST ; PUT ; DELETE `/{id}` (Contrôleur/SA, physique).
**ChantierController** `/api/stock/chantiers` : GET `/`, `/actifs`, `/prochaine-reference`, `/{id}` ; POST ; PUT ; DELETE `/{id}` (EN_COURS, physique, Auth) ; POST `/{id}/cloture`.
**PlafondController** `/api/stock/plafonds` : GET `/`, `/consommation`, `/{id}` ; POST ; PUT ; DELETE `/{id}` (physique, Auth).
**EtatStockController** `/api/stock/etat-stock` : GET `/` (filtres, `parSite`) ; GET `/produit/{produitId}` ; PUT `/seuils` (Auth).
**MouvementStockController** `/api/stock/mouvements` : GET `/`, `/{id}` (lecture seule).
**ValorisationController** `/api/stock/valorisation` : GET/PUT `/parametrage` (méthode globale, Auth) ; GET `/couts-produits`, `/couts-produits/{id}/historique`, `/mouvements`, `/valeur-stock`, `/cout-site`, `/chantiers`, `/chantiers/{id}`, `/marges`, `/tableau-bord`.
**Analyses (lecture, Auth)** : `AnalyseStockController` `/api/stock/analyse` (`/mensuel`, `/dons`, `/comparatif`, `/croise`) ; `ApprovisionnementController` `/api/stock/approvisionnement/suggestions` ; `CategorisationStockController` `/categorisation/stats` ; `ConsommationController` `/consommation/par-destinataire`, `/rapport` ; `DotationController` `/dotation/comparatif` ; `SyntheseMensuelleController` `/synthese-mensuelle` ; `TableauBordStockController` `/tableau-bord` ; `WorkflowStockController` `/workflow/bons` (Kanban, `findAll()` non paginé).

**Legacy ProduitController** `/api/produits` : GET `/`, `/categorie`, `/all`, `/getAllNomProduits`, `/getByNomProduit`, `/destination`, `/code`, `/nom`, `/{id}`, `/image/{codeProduit}` ; POST ; PUT ; **DELETE `/{id}`** (Auth).

### Frontend

- Routes : `stock-v2/stocks-approvisionnement/{produits, produits/categories, produits/nouveau, produits/:id(/modifier), inventaires(/nouveau|/:id|/:id/modifier), synthese, approvisionnement, tableau-bord}`, `stock-v2/controle-mouvements/{categorisation/entrees|sorties, bons-entree(...), bons-sortie(...), workflow, historique-destinataire, plafonds(...), dotation, rapports}`, `stock-v2/analyse-consommations/{mensuel, chantiers(...), dons, comparatif, filtres-croises}`, `stock-v2/valorisation-financiere/{cout-unitaire, mouvements, valeur-stock, cout-site, cout-chantier(/:id), marges, tableau-bord}` (`front:app.routes.ts`, lignes 233-308).
- Guards : seulement `AuthGuard` sur le parent `admin` (`front:guards/auth.guard.ts`). Aucun guard par sous-module stock : le filtrage des flags `modules.stock.*` se fait dans le menu (`front:adminPage/sidebar/`).
- Services : 31 `front:services/stock-v2-*.service.ts` (un par domaine), dont `stock-v2-bon-permissions.service.ts` (habilitations d'affichage), `stock-v2-cump.service.ts` (**recalcul du CUMP côté client**, pour la prévisualisation), `stock-v2-export.service.ts`, `stock-v2-pdf.service.ts` et `stock-v2-import-excel.service.ts`.
- Formulaires clés : `formulaire-bon-entree`, `formulaire-bon-sortie` et l'éditeur de lignes partagé `controle-mouvements/shared/editeur-lignes-bon` (affiche le stock disponible par ligne, avec le message « Stock indisponible pour ce site »), `formulaire-produit`, `import-produits-modal`, `planification-inventaire`, `saisie-inventaire` (la colonne **Théorique est affichée pendant le comptage**), `formulaire-plafond`, `formulaire-chantier`. Composants partagés : `timeline-workflow`, `selecteur-employe`, `shared/dialogs/suppression-definitive-dialog.component.ts`.

### Règles métier codées en dur

| Valeur | Fichier | Remarque |
|---|---|---|
| `SEUIL_DERIVE = 20.0`, `SEUIL_DERIVE_CRITIQUE = 40.0`, `MARGE_MINI = 15.0`, `ECART_COUT_ANORMAL = 50.0` | `back:services/stockv2/ValorisationSupport.java` | Dupliqués côté front |
| `seuilDeriveBudgetPct 20`, `seuilMargeMinPct 15`, `ecartCoutAnormalPct 50` | `front:constants/stock-v2-valorisation.constants.ts` | Copie front des seuils serveur |
| `DEFAULT_N_MOIS = 3` (horizon de réappro) | `back:services/stockv2/ApprovisionnementService.java` | |
| `DEFAULT_MOIS_DORMANCE = 3` | `back:services/stockv2/TableauBordStockService.java` | **Incohérent** avec `moisDormanceDefaut: 6` côté front |
| `seuilEcartJustificationDefaut: 5`, `moisDormanceDefaut: 6`, `seuilAlertePlafondPct: 90`, `seuilDepassementPlafondPct: 100`, `seuilSurconsommationPct: 30` | `front:constants/stock.constants.ts` | 30 % côté front contre 20 % pour la dérive serveur |
| `MOTIF_MIN = 10` caractères | `back:services/stockv2/SuppressionDefinitiveService.java` | |
| Rôles `SUPERADMIN`, `CONTROLEUR_STOCK` | `back:services/stockv2/HabilitationStock.java`, `back:services/stockv2/BonMailNotificationService.java` | Circuit figé |
| Poste `"Responsable Achats"` (destinataire des notifications) | `back:services/stockv2/ReferentielEmployeStockService.java` | Recherche par libellé de poste |
| Préfixes `BE`, `BS`, `INV`, `MVT` (journaliers), `CH` (annuel) | `BonEntreeService`, `BonSortieService`, `InventaireService`, `MouvementBonGenerator`, `ChantierService` | |
| Zone `Africa/Dakar` | `back:services/stockv2/CompteurStockService.java` | Seulement pour la référence annuelle ; la journalière utilise `LocalDate.now()` du serveur |
| Topics `/topic/stock-validations`, `/queue/notifications-stock` | `back:services/stockv2/StockNotificationService.java` | WebSocket |

Aucun de ces paramètres n'est daté. Seule la méthode de valorisation globale est paramétrable (`stockv2_parametrage_valorisation`), et elle est écrasée en place.

### Points vérifiés spécialement

- **Transactions** : aucune. `MongoTransactionManager` est importé dans `back:configurations/MongoConfigurations.java` mais aucun bean n'est déclaré, et il n'y a aucun `@Transactional` dans tout `src/main`. L'atomicité d'un bon repose sur une **compensation manuelle** (`back:services/stockv2/MouvementBonGenerator.java`, blocs `catch` : suppression des mouvements, `compenserEntree`, deltas inverses), de même pour `InventaireService.cloturer` et `ProduitStockService` (bulk). Un arrêt du processus en plein bon laisse un état partiel.
- **Concurrence** : `StockBalanceService.appliquerDelta` fait lecture, addition puis `save`, sans `$inc` atomique ni `@Version`, donc des mises à jour peuvent être perdues. `BonSortieService.valider` et `BonEntreeService.valider` font une lecture, un test `statut == SOUMIS` puis une écriture non conditionnelle : deux validations simultanées peuvent générer les mouvements deux fois.
- **Disponible / réservé** : le disponible est égal au solde du site plus celui de l'entrepôt (`StockBalanceService.disponiblePourSortie`). Il n'existe **aucune notion de réservation** (aucun champ ni service trouvé). Les bons SOUMIS ne réservent rien.
- **Stock négatif** : contrôle à la validation de la sortie (`verifierDisponibiliteAvecConsolide`, cumul par produit sur tout le bon, 422 sinon). Il n'est pas garanti en concurrence (test puis débit sans verrou). `appliquerDelta` ne contrôle rien : la clôture d'inventaire et les contre-passations peuvent rendre un solde négatif (constat documenté dans la javadoc de `back:DataLoader/StockEntrepotUniqueMigrationRunner.java`).
- **CUMP** : `ValorisationSupport.recalculerCout` calcule (stock × coût + q × prix) / (stock + q) sur le stock total avant l'entrée. Le résultat écrase `ProduitStock.prixUnitaire`, avec un historique dans `stockv2_historique_cout`. Les sorties sont valorisées au prix de la ligne **figé à la saisie du brouillon** (`BonSupportService.denormaliserLignes`), pas au CUMP du jour de validation. La suppression définitive d'un bon d'entrée **ne restaure pas le CUMP** (limite écrite dans la javadoc de `SuppressionDefinitiveService`). La valeur à date passée prend la quantité rejouée × le **coût courant** (`ValeurStockService.valeurAuPasse`), et non le coût historique.
- **Numérotation** : `CompteurStockService.genererReference` utilise `findAndModify` + `$inc` avec upsert, ce qui est atomique et sans doublon (plus un index unique sur `reference`). Mais la séquence est **journalière** (`BE-yyyyMMdd-NNN`), pas par exercice. La référence est attribuée **dès le brouillon**, et un brouillon supprimé physiquement (`DELETE`) laisse un **trou**. Les échecs compensés consomment aussi des numéros `MVT`. Le format `%03d` dépasse trois chiffres au-delà de 999 par jour, sans plantage.
- **Circuit de validation** : codé en dur dans chaque service. Un seul niveau (SUPERADMIN valide tous les bons, quel que soit le montant ou le type). Pas de seuil, pas de délégation, pas de moteur générique. Un refus renvoie au créateur via `reprendre` (bon de sortie seulement : `BonEntreeController` n'a pas de `/reprendre`).
- **Masquage des prix** : aucun. `prixUnitaire`, `montant` et `montantTotal` sont renvoyés à tout compte authentifié (DTO et mappers de `back:Mapper/stockv2/`). Aucune occurrence de masquage trouvée dans le front.

### Suppressions physiques

`ProduitStockService.delete`, `CategorieStockService` (`repository.delete`), `ChantierService.supprimer`, `PlafondService` (`delete`), `InventaireService` (brouillon), `BonEntreeService` et `BonSortieService` (brouillon, `deleteById`), `SuppressionDefinitiveService` (bons EFFECTIFS, inventaires clôturés, leurs mouvements et les points de coût, avec un journal dans `stockv2_suppressions`), plus le legacy `ProduitController.delete`. **Des pièces validées et des mouvements peuvent donc être effacés**, même si c'est tracé.

### Tests

- Backend : 23 classes, 116 `@Test`, dans `src/test/java/.../controllers/stockv2/` (5), `services/stockv2/` (16, dont `BonWorkflowServiceIT` 13, `SuppressionDefinitiveServiceIT` 9, `EtatStockServiceIT` 10, `ValorisationSupportTest` 10), `DataLoader/StockEntrepotUniqueMigrationRunnerIT` et `security/JwtStockClaimTest`. Aucun test de concurrence sur les compteurs ou les soldes n'a été trouvé.
- Frontend : 3 specs, 46 `it(` (`stock-v2-bon-permissions.service.spec.ts` 20, `stock-v2-cump.service.spec.ts` 15, `stock-v2-marge.service.spec.ts` 11). Aucun test de composant.
- Legacy : aucun test.

## Module Industrie / Production Chimie (v2)

Chemins de base :
- back : `src/main/java/com/example/Pointage_Cleanic/` (sous-dossiers `controllers|entities|services|repositories|Dto|Mapper/productionchimie`), `production-chimie.http`, `.claude/docs/module-production-chimie.md`
- front : `src/app/adminPage/exploitation-v2/production-chimie/`, `src/app/services/production-*.service.ts`, `src/app/services/stock-chimie.service.ts`, `src/app/constants/production-chimie.constants.ts`

Statut : module **v2**, autonome. Volume : 9 contrôleurs, 11 services, 11 repositories, 10 collections, 22 écrans Angular (≈ 5 600 lignes back, ≈ 8 500 lignes front).

**Constat principal : un second stock existe.** Les matières premières chimie vivent dans `production_chimie_matieres_premieres` avec un champ `quantiteEnStock` dénormalisé, sans aucun lien avec le Stock v2 (`entities/stockv2`). La doc du module l'assume : « ne partage aucune collection avec le module Stock » (`.claude/docs/module-production-chimie.md`). Côté Stock v2, seule trace : `Enum/stockv2/TypeEntree.RETOUR_PRODUCTION` et `Enum/stockv2/MotifMouvement.PRODUCTION`, sans code qui relie les deux.

### Collections MongoDB

| Collection | Entité | Champs principaux | Références | Index |
| --- | --- | --- | --- | --- |
| `production_chimie_matieres_premieres` | `entities/productionchimie/MatierePremiere` | code, nom, unite, seuilCritique, fournisseur (texte), matiereActivePct, compterDansMa, ficheSecurite (byte[] inline), quantiteEnStock, actif | aucune (fournisseur en texte libre) | code unique, actif |
| `production_chimie_mouvements_stock` | `MouvementStockChimie` | type (ENTREE/SORTIE/AJUSTEMENT), quantite, date, lotFournisseur, fournisseur, datePeremption, commentaire, auteurId/auteurNom | matierePremiereId (+ code, nom copiés), ordreFabricationId (+ numéro) | (matierePremiereId, date), ordreFabricationId, (type, date) |
| `production_chimie_formulations` | `FicheFormulation` | code, nom, versionCourante, ingredients[], etapes[], dureePeremptionJours, quantiteRef, uniteProduction, statut (BROUILLON/VALIDEE/ARCHIVEE), versions[] (instantanés embarqués), createdBy/updatedBy | ingredients[].matierePremiereId (+ nom copié) | code unique, statut |
| `production_chimie_ordres_fabrication` | `OrdreFabrication` | numero, produitNom, formulationVersion, quantiteCible, quantiteReelle, dates, operateurResponsableId/Nom, statut, historiqueStatuts[], consommationMp[], lotGenereId | formulationId, operateurResponsableId (RH, non résolu), lotGenereId | numero unique, statut, dateLancementPrevue, operateurResponsableId |
| `production_chimie_lots` | `Lot` | numero, produitNom, formulationVersion, dateFabrication, datePeremption, quantiteProduite, statutControle, statutStock | ordreFabricationId, formulationId, controleQualiteId | numero unique, (produitNom, dateFabrication), statutControle, statutStock, formulationId |
| `production_chimie_grilles_controle` | `GrilleControle` | produitNom, tests[] (parametre, valeurCible, toleranceMin/Max, obligatoire, type) | formulationId | produitNom, formulationId |
| `production_chimie_controles_qualite` | `ControleQualite` | dateControle, controleurId/Nom, mesures[], decision (VALIDE/REJET), commentaire, photos[] (byte[] inline) | lotId (+ numéro copié), grilleId | lotId, (produitNom, dateControle), decision |
| `production_chimie_formats_conditionnement` | `FormatConditionnement` | code, libelle, volume, uniteVolume, typeContenant, dimensions, poidsVide, actif | aucune (pas d'article emballage) | code unique, actif |
| `production_chimie_compteurs_of` / `_compteurs_lots` | `CompteurOf`, `CompteurLot` | _id = AAAAMMJJ, compteur | — | _id |
| `production_chimie_parametres` | `ParametresProductionChimie` | toleranceTotalPct, updatedAt, updatedBy (singleton, valeur écrasée, non datée) | — | — |

Index créés au démarrage par `configurations/ProductionChimieIndexesConfig.initIndexes`.

Données dupliquées avec les autres modules :
- **Article / matière première** : `MatierePremiere` double le catalogue d'articles du Stock v2 (code, nom, unité, seuil, quantité en stock).
- **Fournisseur** : `MatierePremiere.fournisseur` et `MouvementStockChimie.fournisseur` en texte libre, pas de référence au référentiel fournisseurs.
- **Employé** : `operateurResponsableNom`, `controleurNom` dénormalisés, l'id RH n'est pas résolu (doc du module).
- **Produit fini** : `produitNom` en texte libre sur OF, lot, grille, contrôle. Pas de référence à un article vendable du Stock ou du Commercial.

### API REST

Aucune annotation de rôle (`@PreAuthorize` absent, `@EnableMethodSecurity` non activé). `security/SecurityConfig` : `/api/production-chimie/**` tombe sous `anyRequest().authenticated()`. **Rôle requis = « JWT valide » pour toutes les routes ci-dessous.** Le rôle `RoleAdmin.RESPONSABLE_CHIMIE` existe mais n'est utilisé nulle part dans le module.

`MatieresPremieresController` (`/api/production-chimie/stock-chimie/matieres-premieres`)

| Méthode | Chemin | Rôle métier |
| --- | --- | --- |
| GET | `` | Liste paginée (recherche, sous seuil, actif) |
| GET | `/actives`, `/sous-seuil`, `/{id}` | Listes simples et fiche |
| POST / PUT | `` / `/{id}` (multipart) | Création / modification avec fiche de sécurité |
| DELETE | `/{id}` | **Suppression physique** si aucune formulation ne la référence |
| GET / DELETE | `/{id}/fiche-securite` | Télécharger / supprimer la FDS |

`MouvementsStockChimieController` (`/api/production-chimie/stock-chimie/mouvements`)

| Méthode | Chemin | Rôle métier |
| --- | --- | --- |
| GET | `` | Historique filtré (MP, type, OF, dates) |
| POST | `/reception` | Entrée avec lot fournisseur et péremption, incrémente quantiteEnStock |
| POST | `/ajustement` | Fixe la nouvelle quantité (écrase), sans validation ni motif codifié |

`FormulationsController` (`/api/production-chimie/formulations`)

| Méthode | Chemin | Rôle métier |
| --- | --- | --- |
| GET | ``, `/validees`, `/{id}` | Liste, formulations validées, fiche (avec synthèse calculée et **tout l'historique des versions**) |
| POST / PUT | `` / `/{id}` | Création ; modification = instantané de la version courante puis versionCourante+1, **même si VALIDEE** |
| DELETE | `/{id}` | **Suppression physique** si aucun OF ne la référence |
| POST | `/{id}/versions/{numero}/restaurer` | Restaure une ancienne version (nouvelle version) |
| GET | `/{id}/versions/comparer` | Diff ingrédients / étapes / péremption |

`OrdresFabricationController` (`/api/production-chimie/ordres`)

| Méthode | Chemin | Rôle métier |
| --- | --- | --- |
| GET | ``, `/kanban`, `/{id}` | Liste, kanban (200 derniers), détail |
| GET | `/disponibilite-mp`, `/{id}/disponibilite-mp` | Contrôle de disponibilité des MP |
| POST / PUT | `` / `/{id}` | Création (numéro OF-AAAAMMJJ-NNN) ; modification seulement EN_ATTENTE |
| POST | `/{id}/lancer` | Sorties MP théoriques + décrément stock (compensation manuelle) |
| POST | `/{id}/terminer` | Saisie quantité réelle, création du lot |
| POST | `/{id}/annuler` | Annulation motivée, entrées inverses si EN_COURS |

`LotsController` (`/api/production-chimie/lots`) : GET liste, GET `/{id}`, GET `/{id}/tracabilite` (lot → OF → version de formulation → consommations).

`GrillesControleController` (`/api/production-chimie/controle-qualite/grilles`) : GET liste, GET `/{id}`, GET `/pour-lot/{lotId}`, POST, PUT, **DELETE physique**.

`ControlesQualiteController` (`/api/production-chimie/controle-qualite`) : GET `/controles`, GET `/controles/{id}`, POST `/controles` (multipart, met à jour le lot), GET `/controles/{id}/photos/{index}`, GET `/tendances`.

`FormatsConditionnementController` (`/api/production-chimie/formats-conditionnement`) : GET liste, `/actifs`, `/{id}`, POST, PUT, **DELETE physique sans contrôle de référence**.

`ParametresProductionChimieController` (`/api/production-chimie/parametres`) : GET, PUT (tolérance du total, écrasée).

`TableauBordProductionController` (`/api/production-chimie/tableau-bord`) : GET `/rapport`, `/kpis`, `/volumes-par-produit`, `/evolution-mensuelle`, `/rendements`, `/repartition-cq`, `/comparaison-periodes`.

### Frontend

- Routes : `src/app/app.routes.ts` lignes 130 à 169, sous `admin` protégé par `guards/auth.guard.AuthGuard` seulement. **Aucun guard par sous-rubrique** : le filtrage se fait dans `adminPage/sidebar/sidebar.component.html` (`hasAccess('productionChimie.xxx')`), une URL tapée directement reste accessible.
- Droits : 7 booléens `ModulesAutorises.productionChimie` (`entities/GestionModules/SousModules/ProductionChimie`), édités dans `adminPage/gestion-privilege`. Pas de niveau d'action (lecture / écriture / validation).
- Composants (22) : matières premières (liste, formulaire, réception, mouvements), formulations (liste, formulaire, historique des versions, comparaison), ordres (liste, kanban, formulaire, détail), lots (liste, fiche, traçabilité), contrôle qualité (liste, fiche, formulaire, grilles, historique), conditionnement (formats, formulaire, étiquettes), tableau de bord.
- Services : `production-formulation`, `-ordre-fabrication`, `-lot`, `-controle-qualite`, `-format-conditionnement`, `-parametres`, `-tableau-bord`, `-export` (Excel/PDF du rapport), `-fiche-pdf` (fiche de formulation), `-etiquette-pdf` (jsPDF + qrcode), `stock-chimie.service`, `production-formulation-calcul.ts` (calcul MA / qsp en double côté client).
- Formulaires clés : `formulaire-formulation` (statut choisi librement dans la liste BROUILLON/VALIDEE/ARCHIVEE, pas de verrou), `reception-mp` (lot fournisseur, péremption), `detail-ordre.terminer` (saisie de la quantité réelle par `prompt`), `formulaire-controle` (mesures + photos).
- Étiquettes : `services/production-etiquette-pdf.service.urlTracabilite` encode l'URL `/admin/.../lots/{id}/tracabilite`, donc une page **authentifiée**, pas une page publique de traçabilité.

### Règles métier codées en dur

| Règle | Fichier | Valeur |
| --- | --- | --- |
| Format du n° de lot | back `services/productionchimie/CompteurLotService.genererNumero` | `AAAAMMJJ-NNN` (compteur journalier `%03d`) — CDC : `JAV-AAMMJJ-01` |
| Format du n° d'OF | back `CompteurOfService.genererNumero` | `OF-AAAAMMJJ-NNN`, date serveur `LocalDate.now()` (fuseau non fixé) |
| Tolérance du total de formule | back `FormulationCalculService.TOLERANCE_TOTAL_DEFAUT_PCT`, `ParametresProductionChimieService.TOLERANCE_TOTAL_DEFAUT_PCT`, front `constants/production-chimie.constants.ts` | 0,1 % (paramétrable, mais non daté) |
| Conversion en litres | back `TableauBordProductionService.toLitres` | L = 1, ML = 0,001, KG/G/PIECE pris bruts (densité 1 implicite) |
| Taux de perte | back `TableauBordProductionService.tauxPerteOf` | max(0, (cible − réel) / cible) — aucun seuil d'alerte |
| Rendement | back `TableauBordProductionService.rendements` | (réel − théorique) / théorique × 100, affiché, jamais bloquant |
| Seuil « sous seuil » | back `MatierePremiereService.listSousSeuil` | quantiteEnStock ≤ seuilCritique |
| Points de tendance CQ | back `ControleQualiteService.tendances` | 20 par défaut (front : 10) |
| Kanban | back `OrdreFabricationService.kanban` | 200 OF max |
| Péremption par défaut | front `constants/production-chimie.constants.ts` | 365 jours |
| Unité d'un format | back `FormatConditionnementService.validateUniteVolume` | L ou ML uniquement |
| Rejet CQ | back `ControleQualiteService.create` | commentaire obligatoire ; REJET → lot BLOQUE, VALIDE → EN_STOCK |
| Écart de production 5 % | — | **non trouvé** |

### Suppressions physiques

`MatierePremiereService.delete`, `FormulationService.delete` (protégées par un comptage de références), `FormatConditionnementService.delete`, `GrilleControleService.delete` (sans contrôle), `MatierePremiereService.deleteFicheSecurite`. Aucune route DELETE sur OF, lot, mouvement ni contrôle qualité (OF : annulation motivée et historisée dans `historiqueStatuts`, mais `auteurId`/`auteurNom` jamais renseignés par `OrdreFabricationService.addHistorique`).

### Transactions et cohérence

- Aucun `@Transactional` dans `services/productionchimie`. `MongoTransactionManager` est importé dans `configurations/MongoConfigurations` mais aucun bean n'est déclaré.
- `OrdreFabricationService.lancer` : vérification préalable des stocks, puis écritures une par une avec compensation manuelle (entrées inverses) en cas d'échec. `terminer`, `annuler`, `MouvementStockChimieService.createReception/createAjustement` et `ControleQualiteService.create` (contrôle puis lot) écrivent plusieurs documents sans transaction ni compensation.
- `quantiteEnStock` est stocké et dénormalisé, pas recalculé depuis les mouvements ; aucune réconciliation (dette signalée dans la doc du module).
- Le contrôle de stock de `lancer` n'est pas atomique : deux lancements concurrents peuvent passer le contrôle et rendre le stock négatif (pas de `$inc` conditionnel).
- Numérotation : `findAndModify` + `$inc` + upsert, atomique (`CompteurLotServiceIT`, 100 appels concurrents). Un OF annulé garde son numéro (pas de trou supprimé).

### Tests

- Back : 6 classes, 22 tests (`CompteurLotServiceIT` 1, `CompteurLotServiceTest` 3, `ControleQualiteServiceTest` 3, `FormulationCalculServiceTest` 8, `FormulationServiceTest` 2, `OrdreFabricationServiceTest` 5). Sans test : `MatierePremiereService`, `MouvementStockChimieService`, `LotService`, `GrilleControleService`, `FormatConditionnementService`, `TableauBordProductionService`, contrôleurs.
- Front : 1 spec (`services/production-formulation-calcul.spec.ts`, 8 tests). Aucun test de composant du module.
- Manuel : `production-chimie.http` (14 étapes + 4 cas limites).

## Direction, Commercial, QHSE, Finances et Contrôle de gestion (F024 à F031, F082 à F111)

Recherche large dans `Pointage-Cleanic-Backend/src/main/java` et `pointage-agents/src/app` (grep insensible à la casse). Aucun résultat pour : devis (mot entier), prospect, chèque, trésorerie, TVA, SYSCOHADA ou plan comptable, appel d'offres, non-conformité, mobile money. Aucun package, route, drapeau `ModulesAutorises` ni menu ne porte les noms Direction, Commercial, QHSE, Finances ou Comptabilité (`back:entities/GestionModules/ModulesAutorises.java` : dashboard, admin, rh, productionChimie, terrain, stock).

### Direction (F024 à F031)

- Aucun écran Direction. Le tableau de bord après connexion (`front:adminPage/exploitation-v2/dashboard/`) n'agrège que les pointages GPS, les alertes, les affectations et l'effectif.
- Briques réutilisables : quatre tableaux de bord de module avec indicateurs et comparaison de périodes (`back:controllers/terrain/TableauBordTerrainController.java` `/comparaison-periodes`, `back:controllers/productionchimie/TableauBordProductionController.java`, `back:controllers/rh/TableauBordRhController.java`, `back:controllers/stockv2/TableauBordStockController.java`), plus le tableau de bord financier du stock (`back:controllers/stockv2/ValorisationController.java` `/tableau-bord`).
- Les indicateurs sont codés en dur dans chaque service, sans catalogue paramétrable. Aucune notion de budget, forecast ni N-1 versionné.
- Escalade à 3 niveaux déjà présente pour les alertes terrain (`back:Enum/terrain/NiveauEscalade.java` : SUPERVISEUR, RESPONSABLE_OPERATIONNEL, DIRECTION_GENERALE ; `back:services/terrain/AlerteService.java`). La Direction générale est aussi le dernier niveau du circuit des congés (`back:Enum/rh/NiveauValidationConge.java`), portée par le rôle SUPERADMIN.
- Validations codées écran par écran : bons de stock (`back:controllers/stockv2/WorkflowStockController.java`, Kanban), congés (`front:adminPage/ressources-humaines/temps-et-presences/calendrier-conges/validation-conges/`). Pas de vue agrégée.

### Commercial (F082 à F090)

- Aucun prospect, devis, contrat client, facture, avoir ni relance. « Client » n'existe que comme site : `back:entities/terrain/SiteClient.java` (raison sociale, contacts, cahier des charges, GPS, rayon, `nombreMaxEmployes`, QR de pointage et de réclamation). Pas d'entité Client distincte du site, pas de prix ni d'heures vendues.
- Vente de produits : `TypeSortie.VENTE_PRODUIT` (`back:Enum/stockv2/TypeSortie.java`), `prixVente` sur `ProduitStock` et calcul de marge (`back:controllers/stockv2/ValorisationController.java` `/marges`, `front:services/stock-v2-marge.service.ts`). Pas de commande, de client acheteur ni de facture.
- Satisfaction : la « satisfaction par site » du tableau de bord terrain est la moyenne des notes de contrôle qualité interne (`back:services/terrain/TableauBordTerrainService.java` `satisfactionParSite`), pas un avis client. Les réclamations publiques par QR (`back:controllers/reclamation/ReclamationPubliqueController.java`) sont la seule voix des usagers.
- Le coût de revient d'un chantier existe côté stock (`back:controllers/stockv2/ValorisationController.java` `/chantiers/{id}`), commenté « facturation client » côté front, mais limité aux consommables.

### QHSE (F091 à F096)

- Le rôle `Responsable_QHSE` existe dans `back:Enum/RoleAdmin.java` mais n'est utilisé nulle part ailleurs (grep).
- Non-conformité : un contrôle terrain sous le seuil prend la décision NON_CONFORME avec commentaire obligatoire (`back:services/terrain/ControleQualiteService.java` l. 157 à 163). Aucun registre de NC, plan d'actions ni suivi jusqu'à clôture. Le contrôle qualité des lots de production (`back:controllers/productionchimie/ControlesQualiteController.java`) relève de l'Industrie.
- HSE : EPI comme type de produit de stock (`back:Enum/stockv2/TypeProduit.java`) et de matériel (`back:Enum/terrain/TypeMateriel.java`), plafonds de dotation par destinataire (`back:controllers/stockv2/PlafondController.java`). FDS : fichier sur la matière première (`back:entities/productionchimie/MatierePremiere.java` `ficheSecurite`) et URL sur le produit phyto (`back:entities/terrain/ProduitPhytosanitaire.java` `fdsUrl`), sans accès par scan. Accidents du travail : seulement la cotisation CSS AT/MP en paie (`back:services/rh/CalculPaieService.java`). Déchets : un critère de grille de démonstration (`back:DataLoader/TerrainDataLoader.java`).
- Procédures, audits, ISO 9001, veille réglementaire : non trouvé.
- Remontées terrain : réclamations publiques avec photos et statut (`back:entities/reclamation/Reclamation.java`, `front:adminPage/ressources-humaines/reclamations/`), registre phytosanitaire PDF (`back:services/terrain/RegistrePhytoPdfService.java`). Pas de calcul de récurrence ni de postes d'appâtage.

### Finances et Contrôle de gestion (F097 à F111)

- Aucune caisse, chèque, banque, trésorerie, écriture comptable, journal, TVA, immobilisation comptable, budget ni export Sage. Les occurrences de « banque » et « rib » concernent la paie des agents (`back:entities/rh/CategorieProfessionnelle.java`) ; « journal » désigne des journaux techniques de suppression (`back:services/stockv2/SuppressionDefinitiveService.java`).
- Fournisseur : simple texte libre sur `BonEntree.fournisseur`, `ProduitStock.fournisseurPrincipal`, `MatierePremiere` (`back:entities/stockv2/BonEntree.java`). Pas de référentiel fournisseur, de bon de commande ni de facture fournisseur.
- Analytique : coûts de consommation par site, par chantier et marges par produit (`back:controllers/stockv2/ValorisationController.java` `/cout-site`, `/chantiers`, `/marges`), valorisation CUMP / dernier prix / fixe. Seul axe analytique existant, limité au stock. La paie n'est pas ventilée par site.
- « Dérive budgétaire » du tableau de bord financier : écart de consommation par rapport à une période de référence (seuil 20 % codé en dur, `front:constants/stock-v2-valorisation.constants.ts`), pas un budget. Le seul budget saisi est celui des formations (`back:Dto/rh/RecapBudgetFormationDto.java` `budgetPrevu`, `budgetConsomme`).
- Matériel : `prixAcquisition` sur `back:entities/terrain/Materiel.java`, base possible d'un registre d'immobilisations, sans amortissement.

### Bilan du périmètre B

| Bloc | IDs | Fait | Partiel | À faire |
|---|---|---|---|---|
| Direction | F024 à F031 | 0 | 4 | 4 |
| Commercial | F082 à F090 | 0 | 3 | 6 |
| QHSE | F091 à F096 | 0 | 3 | 3 |
| Finances et Contrôle de gestion | F097 à F111 | 0 | 4 | 11 |
| **Total** | 38 lignes | **0** | **14** | **24** |

# Phase 3 — Confrontation au CDC (synthèse)

Le détail des 111 lignes (statut, preuves, écart, complexité restante) est dans `checklist-v3.csv`. Aucune ligne n'est « Fait » : là où une fonction existe, il manque au moins la règle clé de la checklist (droits serveur, transaction, paramètre daté, pièce numérotée par exercice…).

| Lot | Modules | Lignes | Fait | Partiel | À faire |
|---|---|---|---|---|---|
| 1 | Socle, Paramètres et accès, Mon espace | 21 | 0 | 12 | 9 |
| 2 | Exploitation | 13 | 0 | 11 | 2 |
| 3 | Stock | 10 | 0 | 8 | 2 |
| 4 | Finances | 6 | 0 | 1 | 5 |
| 5 | Commercial | 9 | 0 | 3 | 6 |
| 6 | Mon espace, RH | 20 | 0 | 17 | 3 |
| 7 | Finances | 7 | 0 | 2 | 5 |
| 8 | Industrie | 8 | 0 | 6 | 2 |
| 9 | QHSE | 6 | 0 | 3 | 3 |
| 10 | Contrôle de gestion | 2 | 0 | 1 | 1 |
| 11 | Socle, Direction | 9 | 0 | 4 | 5 |
| **Total** | | **111** | **0** | **68** | **43** |

Complexité restante : S = 1, M = 31, L = 64, XL = 15.

# Phase 4 — Synthèse pour démarrer

## 4.1 Sécurité : à traiter avant toute autre chose

Ces points ne relèvent pas de la refonte V3 : ils exposent l'application actuelle.

| # | Constat | Preuve | Gravité |
|---|---|---|---|
| 1 | `/api/login/**` est public. `GET /api/login` renvoie tous les comptes (`findAll()`), mot de passe chiffré compris (aucun `@JsonIgnore`), et `DELETE /api/login/{id}` supprime un compte sans jeton. Revérifié à la lecture. | back:security/SecurityConfig.java (l. 100, `permitAll`) · back:controllers/LoginController.java (`get`, `deleteEmployee`) · back:entities/User.java | Critique |
| 2 | `/api/superadmin` est ouvert à tout compte connecté : création de compte, changement de rôle et de modules, suppression. Le mot de passe saisi est écrit dans les journaux (`System.out.println`). | back:controllers/UtilisateurController.java | Critique |
| 3 | Secrets en clair dans des fichiers suivis par git : mot de passe SMTP, clé JWT et clé Google Maps dans le `docker-compose.yml` du backend, clé JWT et clé Google Maps dans les `application*.yml`, mot de passe initial du super-admin dans le `DataLoader`. Ils sont à changer, pas seulement à retirer du dépôt. | back:docker-compose.yml · src/main/resources/application*.yml · back:DataLoader/ | Critique |
| 4 | Aucun contrôle de droits côté serveur (`anyRequest().authenticated()`, aucun `@PreAuthorize`) : salaires, paramètres de paie, RIB, `/api/terrain/**`, paramètres du stock sont lisibles et modifiables par tout compte connecté. | back:security/SecurityConfig.java (l. 125) | Haute |
| 5 | `GET /api/pointages/{codeSecret}` est public et renvoie nom, position GPS et adresse pour un code à 4 chiffres (10 000 combinaisons). | back:security/SecurityConfig.java (l. 90) | Haute |
| 6 | Réinitialisation du mot de passe : code à 6 chiffres issu de `Math.random()`, sans limite d'essais. WebSocket sans authentification, toutes origines. Photos des employés et Swagger publics. | voir phase 2, socle | Moyenne |

## 4.2 Les 10 écarts les plus importants avec les principes du CDC

| # | Principe du CDC | Écart constaté | Lignes |
|---|---|---|---|
| 1 | Droits par sous-rubrique et droits sensibles | Les droits (`ModulesAutorises`) sont de simples cases oui/non, lus seulement par le menu du front. Le serveur ne les applique pas, et les routes du front ne sont pas gardées par module. | F011, F016, F079 |
| 2 | Propriétaire unique de la donnée | Employé en 3 collections (`employes`, `employes_complet`, `dossiers_employes`) et identité recopiée dans 7 autres. Articles en 5 listes (stock legacy, stock v2, matières chimie, phyto, matériel). Sites en texte libre côté RH, par identifiant côté terrain. Ni référentiel client ni référentiel fournisseur. | F001, F072 |
| 3 | Rien ne se supprime | 39 routes DELETE, dont dossiers employés, contrats, sanctions, déclarations sociales, sites, interventions. Les bons de stock validés peuvent être supprimés définitivement (tracé, mais le CUMP n'est pas rétabli). | F003 |
| 4 | Pièce validée cohérente | MongoDB en standalone, sans replica set ni `MongoTransactionManager`. Aucun `@Transactional`. Bons et OF rendus « tout ou rien » par des annulations écrites à la main, soldes en lecture-modification-écriture : stock négatif et double validation possibles. | F002, F074 |
| 5 | Pièce numérotée sans trou ni doublon | Compteurs atomiques (`findAndModify`), donc sans doublon, mais remis à zéro chaque jour (`BE-yyyyMMdd-NNN`) et attribués dès le brouillon : un brouillon supprimé laisse un trou. Pas de numérotation par exercice. | F002 |
| 6 | Paramètres datés | Paramètres de paie, congés, seuils de stock, tolérance de retard, escalade : tous écrasés sans date d'effet. Certains seuils sont même dupliqués et différents entre front et back (dormance 3 contre 6 mois, dérive 20 contre 30 %). | F006, F045 |
| 7 | Journal d'audit et des accès | Ni journal d'audit général ni journal des connexions. Seul le Stock trace ses suppressions (`SuppressionStockLog`). | F004, F012 |
| 8 | Circuits de validation paramétrables | Chaque module a son circuit codé : congés Supérieur → RH → DG en enum, bons de stock validés par le seul SUPERADMIN, sans seuil ni délégation. | F005, F030, F079 |
| 9 | Écriture générée par la pièce, site comme axe analytique | Aucune comptabilité ni écriture. L'analytique par site n'existe que pour les consommations de stock : ni paie ni achats imputés. | F078, F103, F104, F110 |
| 10 | Une seule source par fait métier | Deux pointages (`pointages` écrit par le kiosque, `terrain_pointages` lu par les alertes et le tableau de bord, que rien n'alimente) : alertes d'absence fausses et indicateurs de présence vides. Second stock pour la chimie, sans lien avec le Stock v2. | F056, F058, F064 |

## 4.3 Dette technique qui bloque la V3, par gravité

**Bloquante**
- Sécurité (section 4.1) : sans contrôle serveur, aucun droit sensible du CDC (salaires, prix d'achat, CA) n'est tenable.
- Pas de transactions : passer MongoDB en replica set et déclarer un `MongoTransactionManager` est un préalable du lot 1 (pièces) et du lot 4 (caisse).
- Double modèle de pointage : décider quelle collection fait foi avant de toucher aux alertes.
- Plantage probable au lancement d'un OF dès qu'une formule contient une ligne d'eau qsp ou « quantité suffisante » (`dosage` mis à `null` puis lu sans filtre, `OrdreFabricationService.lancer`), et calcul de disponibilité incohérent avec le lancement dès que `quantiteRef ≠ 1`. Non exécuté : à confirmer par un test.

**Haute**
- Référentiels dupliqués (employé, article, site) et absence de référentiels client et fournisseur.
- Fichiers stockés en `byte[]` dans les documents (limite de 16 Mo, pas de métadonnées communes) : à sortir vers GridFS ou un stockage objet.
- Suppressions physiques généralisées.
- Index déclarés mais non créés (dont le code unique de site).
- Types de congés, circuits et majorations des heures supplémentaires en enums, valeurs Cleanic dans le code.
- `LocalDate.now()` appelé hors de l'horloge injectée dans le pointage et les alertes ; fuseau du conteneur non fixé.

**Moyenne**
- API legacy encore exposée sans écran (`/api/produits`, avec une route DELETE).
- Seed de sites fictifs actif en production.
- `GlobalExceptionHandler` complet mais sans `ProblemDetail` (RFC 9457).
- Tests front faibles : 1 spec de composant et 4 de services pour tout le RH, aucun test pour Production Chimie et l'écran Stock v2, aucun test qui vérifie un refus de droits.
- `CLAUDE.md` du backend qui cite des contrôleurs disparus ; pas de `docs/claude/` (l'équivalent est `.claude/docs/` côté backend).
- Node 18 dans le Dockerfile du front (fin de support).

## 4.4 Risques de migration des données existantes

Aucun volume n'a pu être mesuré : il n'existe ni script de volumétrie ni seed représentatif, et la base n'a pas été consultée.

| Données | Risque |
|---|---|
| Employés | Rapprocher 3 collections par `agentId` et par e-mail, alors que l'e-mail n'est unique nulle part. |
| Sites et affectations | Les rattachements RH portent le nom du site : à convertir en identifiants. « Agence » et « site client » sont confondus. |
| Pointages | `pointages` stocke heures et durées en texte (« 08:05 », « 1h5mn ») et le site par son nom. Les anciens pointages n'ont pas d'`agenceId`. |
| Stock | Soldes historiques négatifs possibles. Les mouvements antérieurs à la valorisation n'ont pas de coût figé (`coutUnitaireSnapshot`). L'entrepôt unique a déjà remplacé les soldes par site (`StockEntrepotUniqueMigrationRunner`). |
| Production chimie | Matières premières et leurs soldes à fusionner dans le catalogue unique. Lots sans lot fournisseur. Formulations à éclater en un document par version. |
| Paie | Bulletins avec identité figée, RIB et numéros IPRES et CSS issus de la catégorie et non de l'agent. Index (employé, période) non unique : doublons possibles. |
| Tiers | Fournisseurs et clients en texte libre, à rapprocher des futurs référentiels. |
| Fichiers | Photos, fiches techniques, PDF en `byte[]` à extraire. |

## 4.5 Réutilisable tel quel pour le socle (lot 1)

| Élément | Preuve | Usage V3 |
|---|---|---|
| Compteur atomique `findAndModify` | back:services/stockv2/CompteurStockService.java · back:services/terrain/CompteurTerrainService.java | Base du compteur de pièces, à passer par type et par exercice, attribué à la validation. |
| Journal de suppression tracée | back:entities/stockv2/SuppressionStockLog.java · SuppressionDefinitiveService | Modèle du journal d'audit et de l'annulation tracée. |
| Horloge injectée `Africa/Dakar` et utilisateur courant | back:configurations/TimeConfig.java · CurrentUserProvider | Tel quel. |
| Gestion centralisée des erreurs | back:exception/GlobalExceptionHandler.java | À faire produire du `ProblemDetail`. |
| Import en masse à deux stratégies | DossierEmployeService.importBulk | Base du composant d'import Excel générique (F015). |
| Découpe des sous-rubriques | ModulesAutorises | Point de départ du catalogue de droits, à transformer en 5 niveaux appliqués par le serveur. |
| Calculs purs testés | CongeAcquisCalculator, BaremeConges, CalendrierTravailService, ValorisationSupport.recalculerCout, FormulationCalculService | Tels quels, avec des paramètres datés en entrée. |
| Circuit des congés | CongeWorkflowService | Premier cas à porter sur le moteur de circuits. |
| QR d'agence et scan | AgenceQrService (jeton révocable), PointageScanService, HaversineService | Base du pointage QR + GPS (F052, F056). |
| Notifications temps réel | TerrainNotificationService (STOMP) | À authentifier, puis à généraliser. |
| Inventaire | workflow BROUILLON → COMPTAGE → VALIDATION → CLÔTURE avec justification des écarts | Base de F080. |

## 4.6 Questions que la lecture du code ne tranche pas

1. Quelle collection de pointage fait foi : `pointages` (kiosque) ou `terrain_pointages` (v2) ?
2. Les collections legacy `employes`, `employes_complet` et `produits` sont-elles encore alimentées en production ?
3. Un compte `utilisateur` est-il lié à son `DossierEmploye` par l'e-mail, comme le fait `CongeIdentiteService` ?
4. Le SUPERADMIN est-il la « Direction » du CDC ? Qui tient le rôle `CONTROLEUR_STOCK` ?
5. L'entrepôt unique du Stock v2 doit-il revenir à plusieurs magasins avec transferts, comme le prévoit le CDC ?
6. Quelle correspondance entre les 6 familles du CDC et les 5 `TypeProduit` actuels ?
7. Le CDC distingue-t-il agence, site et local ? Le code confond agence et site client.
8. Le mode `qrObligatoire` est-il activé en production (valeur en base) ? Quel est le fuseau réel du conteneur ?
9. La copie du RIB et des numéros IPRES et CSS depuis la catégorie vers le bulletin est-elle voulue ?
10. La production en 3 temps du CDC remplace-t-elle les statuts actuels des OF (EN_ATTENTE, EN_COURS, TERMINE) ?
11. Existe-t-il une sauvegarde MongoDB hors dépôt (cron sur le serveur) ?
12. Le front écoute `/topic/annulationRequests` : le backend qui l'alimentait a-t-il été retiré volontairement ?
