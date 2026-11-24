# Lot 1 — Sous-branche 1a : socle technique

Branche `refonte/v3-1a-socle-technique` (backend et frontend), créée depuis `refonte/v3` le 24/11/2026. Objet : décisions **D1** (transactions), **D2** (migrations versionnées) et **D3** (index) de `docs/refonte/lot-1-conception.md`. Rien n'a été poussé.

Ce document est complété à chaque étape ; il est livré complet avec le dernier commit de 1a.

---

## 1. Spike Mongock (D2, Q-C)

### 1.1 Question posée

Mongock 5 fonctionne-t-il avec la pile actuelle :
- Spring Boot 3.3.6 ;
- Spring Data MongoDB 4.3.6 et pilote 5.0.1 ;
- Java 21 ;
- notre `MongoMappingContext` personnalisé ;
- les transactions sur replica set ?

Si oui, avec quelles limites ? Sinon, repli sur l'exécuteur maison prévu par D2.

### 1.2 Montage

- **Dépendances** : BOM `io.mongock:mongock-bom:5.5.1` (dernière version publiée), `mongock-springboot-v3`, `mongodb-springdata-v4-driver`. Aucun conflit de version : Mongock reprend le Spring Data et le pilote de la BOM Spring Boot. Licence : **Apache 2.0**, en édition communautaire.
- **Test `spikemongock/MongockSpikeIT`**. Il démarre une vraie application `PointageCleanicApplication` par scénario, sur une base dédiée du conteneur de test (`mongo:7.0`, replica set).
- **ChangeUnits du spike** : dans le paquet de test `spikemongock`, placé hors de `com.example.Pointage_Cleanic` pour qu'aucun autre test ne le scanne.
- **Réglages** :
  - `mongock.runner-type=initializingbean` ;
  - `mongock.transactional=true`, qui remplace `transaction-enabled`, déclaré obsolète par Mongock 5.5 ;
  - `@EnableMongock` est obligatoire : le lanceur n'a pas d'autoconfiguration, seul le pilote en a une.

### 1.3 Résultats

| # | Point vérifié | Résultat | Preuve |
|---|---|---|---|
| 1 | Démarrage avec Boot 3.3.6 / Spring Data 4.3.6 / Java 21 et notre `MongoMappingContext` | ✅ | tous les scénarios |
| 2 | Exécution **avant le trafic** | ✅ La ChangeUnit tourne pendant l'initialisation des beans, avant `ApplicationReadyEvent`. Un échec fait échouer `SpringApplication.run` : le serveur n'écoute jamais. | `execute_avant_le_trafic_avec_registre_et_verrou` |
| 3 | Registre `mongockChangeLog` et verrou `mongockLock` | ✅ Une entrée `EXECUTED` par ChangeUnit, plus deux entrées système de Mongock. | idem |
| 4 | Pas de rejeu d'une ChangeUnit déjà appliquée | ✅ Second démarrage : 0 exécution. | `une_change_unit_appliquee_n_est_pas_rejouee` |
| 5 | Transaction : échec à mi-parcours ⇒ rien d'écrit | ✅ Les 2 insertions avant l'exception sont annulées. | `echec_a_mi_parcours_n_ecrit_rien_et_refuse_le_demarrage` |
| 6 | Échec ⇒ démarrage refusé | ✅ `BeanCreationException` portant le message de la ChangeUnit. | idem |
| 7 | Deux instances qui démarrent en même temps | ✅ La seconde démarre pendant que la première exécute sa ChangeUnit (5 s, verrou tenu). Elle attend le verrou, constate que tout est appliqué, puis démarre : 1 seule exécution. | `deux_demarrages_simultanes_n_executent_qu_une_fois` |
| 8 | Index créé par une ChangeUnit `transactional = false` | ✅ | point 2 (ChangeUnit `S002`) |
| 9 | « Baseline » qui marque d'autres ChangeUnits comme appliquées | ❌ Mongock lit le registre **une seule fois, avant la première ChangeUnit**. Une entrée `EXECUTED` écrite par une ChangeUnit `0000` n'empêche pas la suivante de s'exécuter. | `une_baseline_ecrite_dans_le_registre_n_empeche_pas_l_execution` |

### 1.4 Constats à connaître

1. **Mongock crée son propre `MongoTransactionManager`.** Le bean `PlatformTransactionManager` de l'application n'est qu'un **interrupteur** : sans lui, `transactional=true` refuse de démarrer (« transactionManager not provided »). Le gestionnaire réellement utilisé est construit par le pilote sur la même `MongoDatabaseFactory`.
2. **Mongock passe le `MongoTemplate` partagé en `SessionSynchronization.ALWAYS`**, durablement. Conséquence attendue : le template rejoint toute transaction ouverte sur le fil. C'est sans effet tant que le seul gestionnaire de transactions est Mongo (cas de l'application).
3. **Une ChangeUnit en échec ne laisse aucune trace dans le registre.** L'entrée `FAILED` est écrite dans la transaction annulée. L'échec n'est visible que dans les journaux, et la ChangeUnit sera retentée au démarrage suivant. C'est le comportement voulu par D2 (refus de démarrer, message explicite).
4. **Les transactions exigent un replica set.** Sur un `mongod` standalone, le commit échoue (« Transaction numbers are only allowed on a replica set member or mongos »). Mongock masque alors l'erreur d'origine derrière un `IllegalTransactionStateException: Transaction is already completed`, dû à un second `rollback` dans son bloc `catch`. **Ce message signifie donc presque toujours « pas de replica set ».** Il apparaîtra au premier démarrage d'un poste de développement resté en standalone : voir §2 (`docker-compose.dev.yml`).
5. **Artefact de test, pas de Mongock.** Deux `SpringApplication` lancées au même instant dans une même JVM se disputent l'initialisation de logback (`ConcurrentModificationException`). Le test décale donc la seconde jusqu'au début de la ChangeUnit de la première. En production, les instances sont des processus distincts : la question ne se pose pas.
6. **Incident du spike.** Les premiers lancements passaient l'URI de test en « propriétés par défaut ». Or cette source a la plus basse priorité : `application.yml` (`localhost:27017`) l'emportait, et les lancements ont visé la base locale de développement `cleanic`, en standalone. Ils y ont créé `mongockChangeLog` (8 entrées `ROLLED_BACK`) et `mongockLock` (vide). Ces deux collections ont été supprimées aussitôt (vérifié). Aucune ChangeUnit du spike n'y a tourné ; les 12 runners actuels y ont tourné comme lors d'un `bootRun`, ce qui est sans effet puisqu'ils sont idempotents. **Correctif** : le test passe ses réglages en arguments de ligne de commande, la source la plus prioritaire, et vérifie le nom de la base effective.

### 1.5 Décision

**Mongock 5.5.1 est retenu.** Le repli sur l'exécuteur maison n'est pas nécessaire.

Conséquences pour la suite de 1a :
- **Baseline (D2)** : la ChangeUnit `0000-baseline` prévue par la conception n'est **pas réalisable** comme décrit (point 9). Chaque ChangeUnit issue d'un runner garde sa garde d'idempotence actuelle. Sur une base existante, elle s'exécute une fois, ne modifie rien et s'inscrit au registre. L'effet recherché est le même, « ne pas rejouer les 12 runners », et `MigrationsIT` le vérifie sur un jeu « base existante ». **Écart de forme avec D2, pas de fond.**
- **ChangeUnits d'index** : `transactional = false`. MongoDB n'autorise pas la création d'index dans une transaction sur une collection existante.
- **ChangeUnits de données** : transactionnelles. Un échec n'écrit rien et refuse le démarrage.
- **Développement** : le poste doit tourner en replica set (constat 4).

---

## 2. Replica set et transactions (D1.1 à D1.3)

### 2.1 Changements

| Fichier | Changement |
|---|---|
| `docker-compose.yml` | **`mongo:7.0` en replica set `rs0`** avec `--keyFile` (clé générée une fois dans le volume `mongo_cle`, propriétaire 999, mode 400) et **authentification** (compte root créé par l'image depuis `MONGO_ROOT_*`). **Port non publié** (D15). Healthcheck `ping`. Nouveau volume `mongo7_data` : l'ancien `mongo_data` (mongo:6) n'est plus monté mais reste sur le disque pour le retour arrière. Le conteneur Mongo ne reçoit que ses deux variables, pas tout le `.env`. |
| `docker-compose.yml`, service `mongo-init` | Joue `ops/mongo/initialiser-replica-set.js` à chaque `up` : `rs.initiate()` si besoin, attente du PRIMARY, compte applicatif `MONGO_APP_USERNAME` avec le seul rôle `readWrite` sur `pointage_cleanic` (créé ou réaligné). L'application démarre après sa fin réussie (`service_completed_successfully`). |
| `docker-compose.dev.yml` (nouveau) | Mongo de développement en replica set, sans authentification, sur `127.0.0.1:27017`. Le healthcheck initialise le replica set au premier passage. |
| `application.yml` | URI de dev `mongodb://localhost:27017/cleanic?directConnection=true`. |
| `application-prod.yml` | URI `mongodb://${MONGO_APP_USERNAME}:${MONGO_APP_PASSWORD}@mongo:27017/pointage_cleanic?replicaSet=rs0&authSource=admin`. Les identifiants viennent du `.env`, le même que celui que lit `mongo-init` : une seule source. |
| `SecretsRequisVerificateur`, `.env.example` | `MONGO_APP_USERNAME` et `MONGO_APP_PASSWORD` obligatoires en prod. `MONGO_ROOT_*` documentés. Les mots de passe sont en `[A-Za-z0-9]`, car insérés tels quels dans l'URI (`openssl rand -hex 32`). |
| `AucunSecretLitteralTest` | Nouveau test : aucune URI `mongodb://utilisateur:motdepasse@` en clair dans `application*.yml` et les deux `docker-compose*.yml`. |
| `MongoConfigurations` | Beans `MongoTransactionManager` (`readConcern` et `writeConcern` `majority`) et `TransactionTemplate`. |
| `configurations/ExecuteurTransactionnel` (nouveau) | Voir §2.2. |
| `GlobalExceptionHandler` | `OptimisticLockingFailureException` → **409 `CONFLIT_VERSION`** ; `ConflitConcurrentException` → **409 `CONFLIT_CONCURRENT`** ; `DuplicateKeyException` → **409 `DOUBLON`**, sans renvoyer le message du pilote, qui contient la valeur en double. Toutes trois tombaient jusqu'ici en 500. Le `TerrainExceptionHandler` (prioritaire sur `controllers.terrain`) n'est pas modifié : le module Terrain n'est pas touché par 1a. |
| `spikemongock/SpikeConfiguration` | Ne déclare plus son propre gestionnaire de transactions : celui de l'application suffit à Mongock. |

**Écart avec le plan : `MONGODB_URI`.** Le plan prévoyait une variable `MONGODB_URI` complète. J'ai préféré construire l'URI dans `application-prod.yml` à partir de `MONGO_APP_USERNAME` et `MONGO_APP_PASSWORD`. Ainsi, le mot de passe du compte applicatif n'existe qu'une fois dans le `.env`, pour `mongo-init` (qui crée le compte) comme pour l'application (qui s'y connecte) ; il ne peut plus y avoir deux valeurs divergentes.

### 2.2 `ExecuteurTransactionnel` et conventions

**Pourquoi un exécuteur.** Deux transactions qui écrivent le même document ne s'attendent pas : la seconde reçoit aussitôt un `WriteConflict` étiqueté `TransientTransactionError`, et `@Transactional` ne rejoue rien. L'exécuteur prend le relais :
- il rejoue **le traitement entier**, jusqu'à 5 essais, avec une attente aléatoire de 5 à 25 ms multipliée par le numéro d'essai ;
- au-delà, il renvoie `ConflitConcurrentException` (409), sans rien avoir écrit ;
- rejoué, le traitement **relit** l'état : la seconde validation d'un même bon voit le bon déjà `EFFECTIF` et renvoie le 409 métier existant.

**Règles** (reprises dans `CLAUDE.md` à la fin de 1a) :
- **Rejeu** : l'exécuteur est le moyen recommandé ; `@Transactional` reste permis pour une méthode qui ne risque pas de conflit (D1.3), mais une méthode exposée à la concurrence passe par l'exécuteur.
- **Aucun appel externe dans le traitement** : il peut être rejoué ou annulé. Les notifications passent par `ExecuteurTransactionnel.apresCommit(...)`, qui ne s'exécute qu'après validation, et jamais en cas d'annulation, en attendant l'outbox de 1b.
- **Transaction déjà ouverte** : l'exécuteur s'y joint sans rejouer ; la transaction englobante décide.
- **`UnknownTransactionCommitResult` n'est pas rejoué** : le commit a pu réussir, et rejouer le traitement l'appliquerait deux fois.
- **Création de collection et d'index** : interdite dans une transaction sur une collection existante. Les index passent par des ChangeUnits non transactionnelles (§4).

### 2.3 Tests

| Test | Vérifie |
|---|---|
| `TransactionIT` (critère 1a) | Deux écritures puis exception ⇒ 0 document, par `TransactionTemplate` comme par l'exécuteur. Une erreur métier n'est pas rejouée (1 essai). `apresCommit` n'est appelé qu'après validation, jamais sur annulation. **Conflit d'écriture** : deux transactions lisent 0 puis écrivent « lu + 1 » ⇒ valeur finale 2, au moins un rejeu. **Conflit persistant** (document tenu par une transaction ouverte) ⇒ 5 essais, `ConflitConcurrentException`, document inchangé. |
| `GlobalExceptionHandlerConflitsTest` | Les trois 409, et l'absence de la valeur en double dans le message de `DOUBLON`. |
| `AucunSecretLitteralTest`, `SecretsRequisVerificateurTest`, `ProfilProdSecuriteIT` | Adaptés aux deux nouvelles variables. |

**Vérifications dans Docker** (hors suite Gradle) :
- **Pile prod** : copie jetable, projet isolé, `.env` factice.
  - `mongo` passe *healthy*, `mongo-init` se termine en 0 ;
  - un second `mongo-init` répond « déjà initialisé », compte réaligné ;
  - transaction annulée ⇒ 0 document, validée ⇒ 1, avec le compte applicatif ;
  - écriture dans une autre base ⇒ `Unauthorized` ;
  - redémarrage du conteneur ⇒ *healthy* et PRIMARY ;
  - port 27017 non publié ;
  - **application construite et démarrée** (`docker compose up --build`, profil prod) : connexion au replica set authentifié par l'URI de `application-prod.yml`, démarrage en 27 s, `GET /api/stock/produits` sans jeton ⇒ 401, runners actuels joués avec le compte applicatif (16 collections créées). Conteneurs renommés dans la copie jetable : un conteneur `springboot-backend` d'un ancien déploiement local existe déjà sur le poste ; il n'a pas été touché.
- **Mongo de dev** : même commande et même healthcheck dans un conteneur jetable sur le port 27019, le 27017 du poste étant pris par le service MongoDB local, qui n'a pas été touché. Replica set initialisé, transaction depuis le poste avec `directConnection=true`.

### 2.4 Poste de développement

Depuis 1a, l'application exige un replica set : un `mongod` standalone fait échouer le premier commit. Deux possibilités :
1. **Docker** : `docker compose -f docker-compose.dev.yml up -d` (port 27017 du poste). Il faut d'abord arrêter le service MongoDB Windows s'il occupe ce port ; ses données ne sont pas reprises.
2. **Garder le service MongoDB local** et le passer en replica set :
   - ajouter `replication:` puis `  replSetName: rs0` dans `mongod.cfg` ;
   - redémarrer le service ;
   - une seule fois : `mongosh --eval "rs.initiate({_id:'rs0', members:[{_id:0, host:'localhost:27017'}]})"`.
   
   Les données existantes (base `cleanic`) sont conservées.

---

## 3. Migrations versionnées (D2)

### 3.1 Organisation

- **`DataLoader/` devient `migrations/`** (`git mv`, historique conservé). Plus aucun `CommandLineRunner` de migration, plus aucun `@Order`.
- **La logique** reste dans un bean Spring par migration (`migrations/*Migration`, méthode `executer()`), testable directement comme avant.
- **Une ChangeUnit** par migration (`migrations/changeunits/C<ordre>…`) : une enveloppe mince qui reçoit le bean par son constructeur et l'appelle. Mongock fixe l'ordre, tient le registre et prend le verrou.
- **Activation** : `configurations/MigrationsConfig` (`@EnableMongock`), réglages `mongock.*` dans `application.yml` (paquet scanné, `runner-type: initializingbean`, `transactional: true`). Le spike n'active plus Mongock lui-même ; il change seulement le paquet scanné.

### 3.2 Correspondance

| ChangeUnit | Ancien runner | Mode | Pourquoi |
|---|---|---|---|
| `0001-modules-autorises-nettoyage` | `ModulesAutorisesCleanupRunner` (900) | ponctuelle | — |
| `0002-rh-modules-booleen-vers-objet` | `RhModuleMigrationRunner` (901) | ponctuelle | — |
| `0003-conges-circuit-trois-niveaux` | `CongeStatutMigrationRunner` (902) | ponctuelle | Voir §3.3, constat 1. Le `try/catch` qui avalait l'erreur est retiré : un échec annule et refuse le démarrage (D2). |
| `0004-dossiers-agentid-renommage` | `DossierAgentIdMigrationRunner` (950) | ponctuelle | — |
| `0005-dossiers-date-code-agent` | `AgentIdDateBackfillRunner` (960) | ponctuelle | `DossierEmployeService.horodaterCode` date déjà tout code posé à la création, à l'import ou au changement. |
| `0006-contrats-alternance-vers-prestation` | `ContratAlternanceMigrationRunner` (1000) | ponctuelle | — |
| `0007-stock-entrepot-unique` | `StockEntrepotUniqueMigrationRunner` (1010) | **`runAlways`** | Voir §3.3, constat 2. |
| `0008-affectations-sites-complement` | `AffectationSiteBackfillRunner` (1200) | **`runAlways`** | Voir §3.3, constat 3. |
| `0009-periode-essai-suppression` | `PeriodeEssaiDropRunner` (1200) | ponctuelle, **non transactionnelle** | La suppression de collection est interdite dans une transaction. |
| `0010-parametres-paie-par-defaut` | `ParametresPaieDataLoader` | ponctuelle | Données de référence : `ParametresPaieService` répond 404 sans elles. |
| `9001-demo-rh` | `DataLoader` (grille générique, 3 formations) | ponctuelle, **profil `demo`** | Démonstration (D2). |
| `9002-demo-terrain` | `TerrainDataLoader` | ponctuelle, **profil `demo`** | Sites fictifs actifs en prod (audit §4.3). Les paramètres d'escalade se créent déjà à la demande (`AlerteService.getParametresEntity`). |
| — | `DataLoader.seedSuperadmin` | **`AmorcageSuperadmin`**, `CommandLineRunner` hors Mongock | Écart validé au plan : il dépend d'une variable d'environnement. En ChangeUnit, il serait inscrit « appliqué » même sauté faute de mot de passe. Disparaît avec `comptes` (1g). |

Les `System.out.println` des seeds sont remplacés par des journaux (règle du lot 0).

### 3.3 Constats faits en convertissant

1. **Bogue arrêté : `nombreJours` des congés.**
   - **Ce que faisait le runner** : `CongeStatutMigrationRunner` recalculait à **chaque démarrage** le `nombreJours` de **toutes** les demandes, avec `CongeCalendrier.joursOuvres(debut, fin)`, c'est-à-dire **sans les jours fériés**.
   - **Ce que font les services** : `DemandeCongeService` (l. 396) et `CongeWorkflowService` (l. 117) les déduisent.
   - **Conséquence** : chaque redémarrage réécrasait la bonne valeur des demandes contenant un férié, et faussait les soldes.
   - **Désormais** : la ChangeUnit `0003` est ponctuelle. Elle tourne encore une fois, avec le même calcul, puis plus jamais.
   - **À trancher** : les demandes déjà réécrasées gardent leur valeur sans fériés. Une ChangeUnit corrective, qui recalculerait avec `JourFerieService`, est possible mais change des soldes. C'est à décider avec la RH ; ce n'est pas fait en 1a.
2. **Soldes de site.** `InventaireService.cloturer` applique l'écart d'un inventaire **par site** au solde de ce site. L'ancien runner refusionnait ces soldes dans l'entrepôt à chaque démarrage. Pour ne rien changer, `0007` est `runAlways`. Ce comportement disparaîtra avec le référentiel des magasins (lot 3).
3. **Compléments d'affectation.**
   - **Ce que faisait le runner** : `AffectationSiteBackfillRunner` complétait à chaque démarrage le rythme (`joursTravail`) et la date d'entrée des affectations. Les dossiers créés ou modifiés depuis le dernier démarrage les recevaient ainsi ; le service ne les pose pas à l'écriture.
   - **Désormais** : `0008` est `runAlways`, pour garder ce comportement.
   - **À faire en 1d** : calculer ces compléments à l'écriture (`DossierEmployeService.appliquerAffectations` et l'import), puis rendre `0008` ponctuelle.
4. **`runAlways` et registre.** Mongock met à jour l'entrée existante au lieu d'en ajouter une à chaque passage. Le test vérifie donc l'effet, et non le nombre d'entrées.

### 3.4 « Baseline » : traitement retenu

Comme le spike l'a montré (§1.3, point 9), une ChangeUnit ne peut pas marquer les autres comme appliquées.

**Ce qui se passe sur la production** (runners déjà passés, registre absent) au premier démarrage :
- chaque ChangeUnit s'exécute une fois ;
- ses gardes d'idempotence, celles des anciens runners, ne trouvent rien à faire ;
- elle s'inscrit au registre.

`MigrationsIT.base_existante_deja_migree_aucune_ecriture` le prouve : toutes les collections sont photographiées avant et après le démarrage, et elles sont **identiques document pour document**.

### 3.5 Tests

| Test | Vérifie |
|---|---|
| `MigrationsIT` (critère 1a) | **Base vide** : les 10 ChangeUnits s'appliquent ; pas de site fictif ni de formation hors `demo` ; un redémarrage ne rejoue aucune ponctuelle et ne change rien. **Base existante** (registre supprimé, données au format courant : utilisateur, dossier avec affectation complète, congé, solde, contrat) : aucun document modifié, ChangeUnits inscrites. **Formats anciens** : `rh` booléen → objet, drapeau mort retiré, `AgentId` → `agentId` daté, `ALTERNANCE` → `PRESTATION`, `periodes_essai` supprimée, solde de site fusionné, congé `EN_ATTENTE` → `EN_ATTENTE_RH` avec 5 jours ouvrés. **`runAlways`** : un solde de site créé entre deux démarrages est refusionné. **Profil `demo`** : 2 sites, 3 formations. |
| `*MigrationIT` (4 classes, ex-`*RunnerIT`) et `AmorcageSuperadminTest` | Renommés et adaptés (`executer()` au lieu de `run()`). Mêmes cas qu'avant. |
