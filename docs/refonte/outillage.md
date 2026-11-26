# Lot 1 — Sous-branche outillage : essais de bout en bout, secrets, CI, ESLint

Branche `refonte/v3-outillage`, créée le 26/11/2026 depuis `refonte/v3` dans les deux dépôts. `refonte/v3-1b-pieces` y est bien fusionnée : `git merge-base --is-ancestor` est vrai dans les deux dépôts (commits de fusion `4d57587` backend, `3168523` front). **Ni poussée, ni fusionnée** : en attente de votre feu vert. Conception : `docs/refonte/lot-1-conception.md` §4 ; cette sous-branche n'y figurait pas, voir §9.

### Résumé

- **E2E (Playwright)** : 7 parcours sur l'application complète (backend profil `e2e` + front), base `cleanic_e2e` remise à zéro à chaque lancement, captures 360 px de chaque écran parcouru. **3 passages de suite sans échec** (§2.5).
- **gitleaks** dans les deux dépôts : `.gitleaks.toml`, hook pre-commit, étape de CI. L'historique du backend contient 6 fuites anciennes, déjà remplacées (§4.3). Elles sont neutralisées par empreinte, sans réécrire l'historique.
- **CI GitHub Actions** sur `refonte/v3` et `refonte/v3-**` dans les deux dépôts. L'étape invalide de `backend.yml` est corrigée, le déploiement de `main` reste inchangé. Les workflows n'ont **pas encore tourné** (aucun push).
- **ESLint** (angular-eslint 19) dans le front : 896 avertissements de départ, 0 erreur. Les fichiers modifiés par une sous-branche passent en mode strict.
- **Tests** : backend `./gradlew test` 1 111 / 0 échec (1b : 1 107 ; +4 `E2eSeedIT`), sur le code final. Front : build e2e du code final vert (3 passages e2e) ; `npm run test:ci` 294 / 294, suite complète 299 / 348 (les 49 échecs connus, inchangés) et `lint:modifies` vert, **mesurés avant les deux derniers ajouts d'attributs** (accueil, zone de contenu). **La vérification finale du front (`ng build` de production, `test:ci`, `lint:modifies`) a été interrompue faute de mémoire sur le poste : à rejouer avant la fusion** (§8).
- **Deux défauts trouvés** au passage, non corrigés ici (§8) : chaque écran admin est rendu deux fois, et `/super-admin-login` ne charge pas les droits des modules.

## 1. Installation (une fois par poste)

| Quoi | Commande |
|---|---|
| Dépendances front, navigateur | `npm ci` puis `npx playwright install chromium` (dépôt front) |
| Mots de passe des comptes d'essai | `e2e/.env.e2e`, copie de `e2e/.env.e2e.example` (non suivi par git) : `E2E_SUPERADMIN_PASSWORD`, `E2E_STOCK_PASSWORD` |
| MongoDB | replica set `rs0` sur `localhost:27017` : service Windows MongoDB, ou `docker compose -f docker-compose.dev.yml up -d` (backend) |
| gitleaks | `winget install Gitleaks.Gitleaks` (8.30.1), puis dans **chaque** dépôt : `git config core.hooksPath .githooks` |

Mode d'emploi complet des e2e (lancement, ajout d'un parcours, lecture d'une trace) : **`docs/tests/e2e.md` du dépôt front**.

## 2. Essais de bout en bout

### 2.1 Lancement

`npm run e2e` (dépôt front, `e2e/lancer.mjs`) :
1. lit `e2e/.env.e2e` ;
2. refuse si le port 8089 est pris ;
3. **supprime la base `cleanic_e2e`** (garde-fou sur le nom) ;
4. construit le jar du backend (`gradlew bootJar --no-daemon`) ;
5. construit le front `ng build --configuration e2e`, seulement si les sources ont changé ;
6. lance `playwright test`, qui :
   - démarre le front (serveur statique, `http://127.0.0.1:4300`, ou réutilise un `npm run e2e:serve` déjà lancé) ;
   - démarre le backend (`java -Xmx640m -jar`, profil `e2e`, port 8089) ;
   - attend que les deux répondent, puis joue les parcours.

Le dev (8080 / 4200 / base `cleanic`) n'est jamais touché.

### 2.2 Jeu de départ (backend, profil `e2e`)

- **Configuration** : `application-e2e.yml` (port 8089, base `cleanic_e2e`, e-mails neutralisés).
- **ChangeUnits** : `C9101E2eComptes` et `C9102E2eStock`, en `@Profile("e2e")`, sur le modèle de `demo`. Elles appellent `migrations/E2eComptesSeed` et `E2eStockSeed`.
- **Garde-fou** : `E2eGardeFou` refuse toute base dont le nom ne contient pas `e2e` et exige les deux mots de passe.
- **Comptes** :
  - `superadmin@e2e.pointic.test` (SUPERADMIN, `login`) ;
  - `stock@e2e.pointic.test` (`CONTROLEUR_STOCK`, `login` + `utilisateur`, les 27 drapeaux `stock.*` et rien d'autre).
- **Données** : un site « Site d'essai E2E », une catégorie, et un article par parcours. Stock initial dans l'entrepôt unique : `E2E-SORTIE` 50, `E2E-ANNULATION` 30, `E2E-INVENTAIRE` 20.
- **Tests** : `E2eSeedIT` (4 tests) vérifie le seed, le refus hors base d'essai, le refus sans mot de passe et l'absence hors profil.
- **Exploitation** : rien à faire, le profil n'est jamais actif en prod. Pas d'annexe à `basculement-v3.md`.

### 2.3 Parcours couverts (`e2e/parcours/`, front)

| Parcours | Vérifie |
|---|---|
| Connexion | connexion super-admin par l'accueil, tableau de bord ; déconnexion, jeton effacé, espace admin refusé sans jeton |
| Bon d'entrée | création `BROUILLON-…`, soumission, validation, puis **`BE-2026-0001`** ; stock 0 → 25 dans la liste des produits |
| Bon de sortie | création, validation (`BS-2026-…`), stock 50 → 40 ; annulation avec un motif de 10 caractères ou plus, puis bandeau « annulé » avec le motif, stock rétabli à 50 |
| Abandon | brouillon abandonné, puis bandeau « abandonné », libellé toujours `BROUILLON-…`, compteur `compteurs_pieces` du type BE inchangé (lecture Mongo) |
| Motif trop court | 4 caractères utiles, puis 9 : message d'erreur, bouton « Annuler la pièce » désactivé ; la pièce reste effective, stock inchangé |
| Inventaire | planification (sélection d'un article), comptage 17 pour 20 justifié, validation, clôture (`INV-2026-…`) ; stock 20 → 17 |
| Droits | le compte Stock arrive sur la page par défaut, ne voit que le menu Stock (plus « Congés », l'espace personnel de tout compte) ; `/admin/rh/gestion-du-personnel/dossier-employe` tapée à la main le renvoie à la page par défaut ; le Stock reste accessible |

Captures 360 px (CDC §14) : 15 écrans, dans `e2e-resultats/captures-360/` (ignoré par git).

### 2.4 Sélecteurs

`data-testid` ajoutés dans 15 écrans ou composants :
- accueil (formulaire de connexion), sidebar (menus, déconnexion, zone de contenu) ;
- fiches, formulaires et éditeur de lignes des bons ;
- sélecteurs site et produit du Stock ;
- boîte de confirmation, boîte d'annulation ;
- liste des produits, planification et saisie d'inventaire.

Aucun parcours ne dépend d'un texte ni d'une classe CSS, sauf les libellés de statut affichés (« Effectif », « Annulé »…), qui sont l'objet même de la vérification.

### 2.5 Fiabilité et durée

- Chaque parcours a son article et sa propre connexion. Aucune pause fixe : uniquement des attentes sur l'écran (`expect`, `expect.poll`, `toPass`). `workers: 1`, `retries: 0`.
- **3 passages de suite, sans échec** :

| Passage | Parcours | Durée des parcours | Durée totale (build du jar, démarrage des serveurs compris) |
|---|---|---|---|
| 1 | 7 / 7 | 3,2 min | 238 s |
| 2 | 7 / 7 | 2,6 min | 191 s |
| 3 | 7 / 7 | 3,2 min | 235 s |

Front déjà construit (`dist/e2e` à jour) ; un build du front ajoute 2 à 3 min. La remise à zéro de la base et le jeu de départ sont refaits à chaque passage.

Une première série avait montré **une instabilité** : la première connexion après le démarrage du backend (JVM froide) dépassait l'attente de 10 s. `seConnecter` attend désormais la réponse de `POST /api/login` elle-même (jusqu'à 30 s) et vérifie son statut. La série ci-dessus a été rejouée entièrement après ce correctif.

### 2.6 Choix imposés par la mémoire du poste

Le poste a 7,8 Go de RAM, dont 0,5 à 0,7 Go libre avec Docker Desktop, l'IDE et deux conteneurs actifs (`springboot-backend`, `angular-nginx`).

Pendant la mise en place :
- la JVM du démon Gradle, puis celle du backend, sont tombées (« insufficient memory for the Java Runtime ») ;
- **le service Windows MongoDB s'est arrêté** (événement 7034 à 16:23). Faute de droits administrateur pour le relancer, les passages ont été joués sur le Mongo 7.0 rs0 de `docker-compose.dev.yml`, même port, base `cleanic_e2e`. Votre base `cleanic` n'a pas été touchée.

D'où trois écarts avec la demande (« bootRun », « ng serve ») :
- le jar est construit d'abord, puis Gradle se termine ;
- le backend tourne avec `-Xmx640m` ;
- le front est un build statique, et `ng serve` n'est utilisé que s'il tourne déjà (`npm run e2e:serve`).

## 3. CI GitHub Actions

| Dépôt | Workflow | Déclencheur | Jobs |
|---|---|---|---|
| backend | `ci-refonte.yml` (nouveau) | push et PR vers `refonte/v3`, `refonte/v3-**` | `./gradlew test` (JDK 21, cache Gradle, Testcontainers `mongo:7.0` en replica set) ; gitleaks |
| backend | `backend.yml` | push `main` (inchangé) | **étape checkout réparée** : `- name:` et `- uses:` formaient deux étapes, la première sans action (workflow invalide). Rien d'autre n'a changé ; le déploiement reste commenté |
| backend | `main.yml` | — | **supprimé** : fichier vide (une fin de ligne), workflow invalide |
| front | `ci-refonte.yml` (nouveau) | idem | `ng build`, `npm run test:ci`, `npm run lint`, `npm run lint:modifies` (PR), gitleaks |

- **Pas de service Mongo** (décision du 26/11) : les tests utilisent déjà Testcontainers, qui fonctionne sur les runners Linux. `src/test/resources/testcontainers.properties` force la stratégie Docker Windows (npipe) du poste. En CI, elle est remplacée par `TESTCONTAINERS_DOCKER_CLIENT_STRATEGY=…UnixSocketClientProviderStrategy`.
- **Vérifications faites** : `actionlint` (dans Docker) ne signale rien sur les workflows du backend. Ceux du front ont la même structure.
- **Non vérifié** : aucun workflow n'a encore tourné. Le premier push dira si la surcharge Testcontainers suffit (sinon, rendre `testcontainers.properties` propre au poste).
- Les e2e restent locaux.

## 4. gitleaks

### 4.1 Configuration

- **`.gitleaks.toml`**, dans les deux dépôts : règles par défaut (`[extend] useDefault = true`). Côté backend, une liste d'exceptions ciblée y est ajoutée : les trois secrets JWT factices des tests (`test_secret_at_least_32_characters_long_xyz`, `valeur_factice_de_test_au_moins_32_caracteres`, `test-secret-tres-long-de-plus-de-32-caracteres`), qui provoquaient 52 faux positifs.
- **Hook local** `.githooks/pre-commit` : `gitleaks git --pre-commit --staged --redact`. Il refuse le commit si gitleaks est absent. Activation, une fois par clone : `git config core.hooksPath .githooks`.
- **CI** : binaire 8.30.1 téléchargé, empreinte SHA-256 vérifiée, analyse de **tout l'historique** (`fetch-depth: 0`). Toute fuite non listée fait échouer l'étape.

### 4.2 Fuites anciennes : référence sans réécriture

Les fuites connues sont neutralisées par leur **empreinte** dans `.gitleaksignore` (`commit:fichier:règle:ligne`, **sans la valeur**). Ce fichier tient lieu de rapport de référence, et l'historique n'est pas réécrit. Toute nouvelle fuite, dans n'importe quel nouveau commit, fait échouer le hook et la CI. Règle : ne jamais ajouter une nouvelle fuite à ce fichier ; retirer le secret et le changer.

### 4.3 Ce que contient l'historique

**Backend** : 58 détections brutes, dont 52 faux positifs (secrets JWT factices de 50 fichiers de test, voir §4.1). Il reste **6 fuites réelles, toutes anciennes** :

| Commit | Date | Fichier | Règle gitleaks | Nature (d'après `docs/securite/rotation-secrets.md`) |
|---|---|---|---|---|
| `3f7a921` | 19/08/2025 | `docker-compose.yml` l. 21 | generic-api-key | `JWT_SECRET` |
| `3f7a921` | 19/08/2025 | `src/main/resources/application.yml` l. 29 | generic-api-key | secret JWT |
| `fa246dd` | 17/12/2025 | `docker-compose.yml` l. 21 | generic-api-key | `JWT_SECRET` |
| `18f4be2` | 17/02/2026 | `docker-compose.yml` l. 22 | gcp-api-key | clé Google Maps |
| `18f4be2` | 17/02/2026 | `src/main/resources/application-prod.yml` l. 22 | gcp-api-key | clé Google Maps |
| `18f4be2` | 17/02/2026 | `src/main/resources/application.yml` l. 25 | gcp-api-key | clé Google Maps |

Les valeurs ont été retirées du code au lot 0 (`9586398`, C3) ; leur rotation suit `docs/securite/rotation-secrets.md`.

⚠ Les règles par défaut de gitleaks **ne détectent pas** les autres secrets que cite `rotation-secrets.md` : mot de passe d'application Gmail (`MAIL_PASSWORD`), mot de passe initial du super-admin (`DataLoader.java`, `utils/BcryptGen.java`). Ils sont pourtant dans l'historique. Leur rotation et la purge facultative de l'historique restent celles de `rotation-secrets.md`.

**Front** : aucune fuite détectée dans l'historique.

Le fichier `.env` local du backend, ignoré par git, contient des valeurs réelles : c'est attendu (il n'est jamais commité, et le hook n'analyse que l'index).

## 5. ESLint (front)

- **Configuration** : `ng add @angular-eslint/schematics@19`, configuration plate `eslint.config.js` (TypeScript et templates, recommandé + accessibilité).
- **`npm run lint`** (`ng lint`) : toutes les règles passent en **avertissement**. Seule une erreur d'analyse fait échouer.
- **`npm run lint:modifies [-- <réf>]`** (`scripts/lint-modifies.mjs`) : fichiers `.ts` et `.html` ajoutés ou modifiés depuis la base de la sous-branche (merge-base avec `origin/refonte/v3`), règles à leur vraie sévérité (`ESLINT_STRICT=1`), `--max-warnings 0`. Un fichier touché doit être **entièrement propre** (décision du 26/11).
- **Point de départ** : **896 avertissements, 0 erreur**, dans 229 fichiers sur 596. Principales règles en cause :

  | Règle | Avertissements |
  |---|---|
  | `template/label-has-associated-control` | 589 |
  | `no-explicit-any` | 104 |
  | `template/eqeqeq` | 44 |
  | `no-unused-vars` | 30 |
  | `no-empty-function` | 25 |
  | `click-events-have-key-events`, `interactive-supports-focus` | 19 + 19 |

- **Conséquence pour cette branche** : les 6 écrans touchés pour les `data-testid` ont été rendus propres.
  - 25 labels sont reliés à leur champ (`for` / `id`).
  - La fenêtre de refus des deux fiches de bon reçoit `role="dialog"`, et se ferme avec Échap.
  - `lint:modifies` est vert sur les fichiers de la branche.

## 6. Tests unitaires du front

- La suite complète reste à **299 / 348** : les 49 échecs sont déjà connus et répartis sur 7 suites.
- Ces 7 suites sont écartées de la configuration `ci` (`angular.json`, `test.configurations.ci.exclude`) :
  - `admin.component` ;
  - `shared/confirm-dialog` (sans fournisseur `MatDialogRef`) ;
  - `gestion-privilege.component` (12 tests) ;
  - `header.component` ;
  - `sidebar.component` (29) ;
  - `super-admin-login-page.component` ;
  - `websocket.service` (4).
- `npm run test:ci` donne **294 / 294**.
- **Dette** : réparer ces suites puis les retirer de la liste d'exclusion. Une suite réparée qu'on oublie de réintégrer n'est plus jouée en CI.

## 7. Règles ajoutées aux deux `CLAUDE.md`

- « Toute sous-branche qui modifie un écran ajoute ou met à jour ses parcours e2e ; `npm run e2e` est vert avant de déclarer une sous-branche terminée. »
- « `npm run lint` sans erreur sur les fichiers modifiés » (`npm run lint:modifies`).
- « gitleaks actif : aucun secret dans un commit. »

## 8. Limites et défauts relevés

1. **Écrans rendus deux fois** : `admin.component.html` contient un `<router-outlet>` en plus de celui de la sidebar. Ce bug existe depuis le premier commit. Chaque écran admin est donc rendu deux fois, avec ses appels d'API en double. Les parcours cherchent les éléments dans la zone de contenu (`data-testid="contenu"`). **Non corrigé** (hors périmètre) ; à traiter dans une sous-branche, avec une vérification e2e.
2. **`/super-admin-login` ne charge pas les droits** : seule la connexion par l'accueil appelle `notifyPermissionsChanged()`. Un compte non super-admin qui passe par cette page voit une sidebar vide. Les parcours se connectent par l'accueil. **Non corrigé.**
3. Les e2e sont **locaux seulement**, et lents (≈ 5 min, plus 2 à 3 min quand le front doit être reconstruit). Ils couvrent le Stock (bons, inventaires) et les droits ; les autres modules n'ont pas encore de parcours.
4. Les sélecteurs de site et de produit ne déclenchent aucun signal « liste chargée ». `choisirDansSelecteur` retape donc la recherche jusqu'à voir l'option.
5. Le poste a une **mémoire insuffisante** pour faire tourner ensemble le dev et les e2e (§2.6).
6. La CI n'a pas encore tourné (§3).
7. **À rejouer avant la fusion**, poste déchargé : `ng build` (production), `npm run test:ci` et `npm run lint:modifies` sur l'état final du front, interrompus faute de mémoire le 26/11 au soir. Ces derniers changements ne sont que des attributs `data-testid` (formulaire de l'accueil, `<main>` de la sidebar).

## 9. Écart avec la conception, à reporter

La conception (§4) ne prévoyait pas de sous-branche d'outillage. Elle s'insère **entre 1b et 1c**, dans les deux dépôts, sous le même nom (règle du §4). Report fait dans `lot-1-conception.md` §4. Les critères d'acceptation des sous-branches suivantes gagnent les trois règles du §7.

## 10. Commits

Un commit par étape (§0 du plan), dans chaque dépôt ; liste en fin de branche, `git log refonte/v3..refonte/v3-outillage`.
