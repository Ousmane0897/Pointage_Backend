# Lot 1 — Sous-branche 1a : socle technique

Branche `refonte/v3-1a-socle-technique` (backend et frontend), créée depuis `refonte/v3` le 24/11/2026. Objet : décisions **D1** (transactions), **D2** (migrations versionnées) et **D3** (index) de `docs/refonte/lot-1-conception.md`. Rien n'a été poussé.

### Résumé

| | |
|---|---|
| **Statut** | Développement terminé, fusionné dans `refonte/v3`. Mise en production **non faite** : procédure écrite pour l'administrateur du serveur, à jouer au basculement final de la V3 (§7, pas d'accès au VPS). |
| **Spike Mongock** | **Retenu** : Mongock 5.5.1, Apache 2.0 (§1). Seule limite : une « baseline » ne peut pas marquer d'autres ChangeUnits, d'où des gardes d'idempotence à la place (§3.4). |
| **D1 Transactions** | Replica set `mongo:7.0` en dev et en prod, authentification et port non publié en prod, `MongoTransactionManager` et `ExecuteurTransactionnel` (rejeu des conflits), `@Version` sur bons, inventaires et produits, soldes atomiques avec débit gardé, bons de stock validés en transaction **sans compensation** (§2, §5, §6). |
| **D2 Migrations** | 12 runners → 10 ChangeUnits, 2 seeds `demo` et un amorçage hors Mongock, plus `0011` (versions) ; 2 en `runAlways` pour ne rien changer (§3). |
| **D3 Index** | 83 index, dont 22 uniques, dans une liste unique ; 63 annotations retirées ; contrôle des doublons qui refuse le démarrage (§4). |
| **Tests** | **1 025 → 1 062**, 0 échec (§8). |
| **Front** | Branche créée, **aucune modification** (§9). |

Commits backend sur `refonte/v3-1a-socle-technique` :

| Commit | Objet |
|---|---|
| `f92c638` | Spike Mongock |
| `73fc90e` | Replica set et transactions |
| `803bcce` | Migrations Mongock |
| `0876ff9` | Index |
| `3bbe8de` | Verrou optimiste |
| `31677ad` | Soldes atomiques et bons transactionnels |
| dernier commit | Documentation |

Sur `refonte/v3`, avant la branche : `a691422`, réponses Q-D et Q16 dans la conception.

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

---

## 4. Index versionnés (D3)

### 4.1 Changements

- **`migrations/index/IndexesAttendus`** : **la seule liste** des index, soit **83 index, dont 22 uniques**.

  | Module | Index | dont uniques |
  |---|---|---|
  | socle | 8 | 2 |
  | rh | 24 | 5 |
  | stock | 22 | 7 |
  | terrain | 6 | 3 |
  | production chimie | 23 | 5 |

- **Annotations retirées** : les **63 annotations** `@Indexed` / `@CompoundIndex(es)` ont été retirées de **37 entités**. Elles ne servaient à rien, `auto-index-creation` étant désactivé, et laissaient croire à des index qui n'existaient pas.
- **Classes supprimées** : `ProductionChimieIndexesConfig` et `TerrainIndexesConfig`. Leurs **26 index** sont repris **à l'identique** (noms, clés, aucun filtre), car ils existent déjà en production : leur recréation est sans effet.
- **ChangeUnits `0101` à `0105`** (une par module, `transactional = false`) appellent `migrations/index/CreateurIndex` :
  - index **présent sous le même nom et identique** : rien à faire, ce qui rend la ChangeUnit rejouable ;
  - **même nom mais autre définition**, ou **mêmes clés sous un autre nom** : conflit (`DEFINITION_DIFFERENTE`, `MEMES_CLES_AUTRE_NOM`) ;
  - **index unique** : recherche préalable des doublons (`$group` puis `count > 1`) dans le périmètre du filtre partiel ; un **champ absent et un champ `null` sont regroupés** (`$ifNull`), car un index unique les tient pour la même valeur ;
  - en cas de doublon ou de conflit : rapport dans **`migration_rapports`** (motif, valeurs, 10 identifiants par valeur, 20 valeurs au plus), journal d'erreur limité aux **nombres**, puis **échec**. La ChangeUnit échoue et le démarrage est refusé ; **rien n'est dédoublonné ni supprimé** ;
  - les index valides du même module sont créés avant l'échec. C'est sans risque : la création est idempotente, et une ChangeUnit en échec repasse au démarrage suivant.

### 4.2 Choix de définition

| Choix | Raison |
|---|---|
| Unique sur un champ facultatif : **filtre partiel `{champ: {$type: "string"}}`** (codes, références, matricule, `agentId`), à la place de `sparse` | Un index `sparse` indexe quand même un champ présent mais `null`, et un unique sans filtre traite tous les « sans valeur » comme des doublons. Une **chaîne vide** `""` reste, elle, soumise à l'unicité. |
| **Sans filtre** : `stockv2_etats_stock (produitId, siteId)`, `rh_jours_feries.date`, `participations_formation (sessionId, employeId)`, les uniques de Production Chimie | Soldes : le couple (produit, entrepôt = `null`) doit lui aussi être unique. Production Chimie : identique à l'existant en production. |
| **`employes_complet.nomComplet` unique : non créé** | Les homonymes sont légitimes, et la collection est legacy (archivée en 1d). Index consigné ici comme « non créé ». |
| `produits.search_idx` (texte + `categorie`, `destination`) créé | Reprise fidèle de l'annotation. Collection legacy, sans écran ; aucune requête `$text` n'existe dans le code (un import `TextCriteria` inutilisé dans `PointageServices`). |
| `pointages.site` (tableau) et `deviceId` créés | Repris des annotations. Les pointages relèvent du lot 2. |

### 4.3 Script d'exploitation `ops/mongo/detecter-doublons.js`

Il est en **lecture seule**. L'administrateur du serveur le joue en production avant la bascule (procédure `docs/exploitation/basculement-v3.md`, étape 2) et en renvoie le résultat. Pour chacun des 22 index uniques, il affiche les valeurs en double, avec un échantillon d'identifiants ; puis il liste les index déjà présents en base, pour repérer un conflit de nom ou de définition.

`DetecterDoublonsScriptTest` vérifie que le script couvre **exactement** les index uniques d'`IndexesAttendus`, avec le même filtre partiel ou sans filtre.

**Vérifications** :
- sur les bases locales de développement (`cleanic`, `Cleanicpointage`, presque vides) : aucune erreur, aucun doublon ;
- dans un conteneur jetable :
  - un matricule en double ⇒ « BLOQUANT » avec les deux identifiants ;
  - un ancien index `code` sous un autre nom ⇒ listé ;
  - un solde `(p, siteId: null)` plus `(p, siteId absent)` ⇒ « BLOQUANT ». Ce cas a révélé le défaut `null` / absent, corrigé dans le script et dans `CreateurIndex`.

### 4.4 Tests

| Test | Vérifie |
|---|---|
| `IndexesAttendusIT` (critère 1a) | Base vide puis toutes les migrations ⇒ chaque index attendu existe avec sa définition (clés, unicité, filtre). **Aucun index non déclaré** dans aucune collection, hors `mongock*` et `migration_rapports`. Taille de la liste : 83. |
| `CreateurIndexIT` (critère 1a, « jeu piégé ») | Doublons ⇒ échec, rapport `DOUBLONS` avec la valeur et le nombre, **index non créé, 3 documents conservés**. Absent / `null` ⇒ doublon rapporté. Filtre partiel : les documents sans valeur ne sont pas des doublons. Sans filtre : les absents comptent. Index déjà présent à l'identique ⇒ rien à faire, rejouable. Même nom, autre définition ⇒ conflit `DEFINITION_DIFFERENTE`. |
| `DetecterDoublonsScriptTest` | Alignement du script sur `IndexesAttendus`. |

**Aucun test existant ne violait les nouveaux index uniques** : la suite complète est passée sans modification de test.

### 4.5 Index en échec éventuels en production

**Inconnus à ce jour** : seul l'administrateur du serveur peut jouer `detecter-doublons.js` sur la base de production (pas d'accès au VPS), à l'étape 2 de `docs/exploitation/basculement-v3.md`. Le développement n'en dépend pas : la détection est testée sur des jeux piégés (§4.4). Les candidats probables sont les mêmes que ceux de la conception (D3) :
- `dossiers_employes.matricule` et `agentId` (index jamais créés, saisie libre, import Excel) ;
- `stockv2_etats_stock (produitId, siteId)` (`appliquerDelta` pouvait créer deux soldes en concurrence) ;
- `stockv2_produits.code`.

Résultat à reporter ici quand l'administrateur l'aura renvoyé. Chaque doublon se tranche avec le métier (fusion ou correction), puis l'administrateur rejoue le script.

---

## 5. Verrou optimiste (D1.4)

- **`@Version Long version`** sur `BonEntree`, `BonSortie`, `Inventaire` et `ProduitStock`, le périmètre retenu au plan. Les autres pièces le recevront avec `PieceBase` en 1b ; les référentiels en 1d, 1e et 1f.
- **ChangeUnit `0011-version-initiale-stock`** (transactionnelle) : `version = 0` sur les documents existants de ces quatre collections, seulement là où le champ manque. Sans elle, Spring Data prendrait un document existant sans version pour un nouveau : son réenregistrement tenterait un insert, d'où un doublon d'`_id`.
- **Aucun contrat d'API changé** : la version n'est ni exposée ni acceptée. `ProduitStockMapper` l'ignore explicitement dans `toEntity` et `updateEntityFromDto`, comme les autres champs serveur. Un conflit entre la lecture et l'écriture d'une même requête donne **409 `CONFLIT_VERSION`** (§2).
- **Vérifié** : aucun code ne reconstruit l'une de ces entités avec un `id` existant (toutes les mises à jour relisent l'entité). Les seuls `builder()` sont des créations.

**Tests** :
- `VerrouOptimisteStockIT` : deux lectures du même produit ; la première écriture passe, la seconde lève `OptimisticLockingFailureException`, et la base garde la première valeur, en version 1.
- `MigrationsIT`, scénario « formats anciens » : un bon sans version passe à 0, un bon déjà en version 4 est conservé.

---

## 6. Soldes atomiques et bons transactionnels (D1.5, D1.6)

### 6.1 Changements

| Fichier | Changement |
|---|---|
| `StockBalanceService` | **Plus aucune lecture-addition-`save`.** `appliquerDelta` fait un `$inc` atomique en upsert : plus de mise à jour perdue ; une collision de premier upsert est réessayée hors transaction, ou fait rejouer la transaction dedans. **Nouveau `debiter`** : `findAndModify` gardé par `quantite >= q`, puis `$inc: -q`. Sans document correspondant, **422 `STOCK_INSUFFISANT`**, et rien n'est écrit. `debiterAvecRepli` (site, puis entrepôt) fait deux débits gardés et **exige une transaction**. `recrediterRepli`, devenu inutile, est supprimé. |
| `MouvementBonGenerator` | **Compensations retirées** (`genererPourEntree`, `genererPourSortie`). Les deux méthodes **exigent une transaction** (`IllegalStateException` sinon). Mouvements, soldes, coût courant, historique de coût et **compteur de références** sont validés ensemble ou pas du tout. La pré-vérification cumulée par produit reste : elle donne le message clair ; le débit gardé, lui, donne la garantie. |
| `ValorisationSupport` | `compenserEntree` supprimée : le recalcul CUMP / dernier prix s'écrit dans la transaction du bon. |
| `BonEntreeService`, `BonSortieService` | `soumettre`, `valider`, `refuser` et `reprendre` passent par **`ExecuteurTransactionnel`** : en concurrence, la perdante est rejouée, relit le bon déjà `EFFECTIF` et reçoit le 409 existant. Les **notifications WebSocket partent après le commit** (`apresCommit`), jamais sur une annulation. `modifier` (un seul document) est protégé par `@Version` (§5). `creer` est inchangé : son e-mail part après l'enregistrement, et l'outbox arrive en 1b. |
| `EtatStockService.majSeuil` | Surcharge de seuil par site par un **`$set` ciblé en upsert**. Avant, l'enregistrement du document entier pouvait écraser une quantité modifiée au même moment. |
| `StockInsuffisantException` (nouvelle) + `GlobalExceptionHandler` | Sous-classe de `StockOperationException` : **même 422**, code `STOCK_INSUFFISANT` au lieu de `STOCK_OPERATION_ERROR`. Le front ne lit pas ce code : vérifié par recherche dans `pointage-agents`, où il ne réagit qu'au statut HTTP. |

**Compensations conservées**, chacune dans sa sous-branche (D1.6) :
- **`InventaireService.cloturer`** et l'**import en masse** de `ProduitStockService` : ils utilisent toujours `appliquerDelta`, désormais atomique mais **non gardé**. La clôture d'un inventaire global peut aujourd'hui porter l'entrepôt en négatif (migration `0007`), et la garder changerait ce comportement.
- **`SuppressionDefinitiveService`** : remplacé par l'annulation tracée en 1b.
- **`OrdreFabricationService`** : Production Chimie.

### 6.2 Tests

| Test | Vérifie |
|---|---|
| `ValidationConcurrenteIT` (critère 1a) | **Deux validations simultanées** d'un même bon SOUMIS, en entrée comme en sortie : 1 réussite, 1 refus en 409 (`StockConflitException`, ou `ConflitConcurrentException` si les rejeux s'épuisent) ; **mouvements générés une fois**, solde crédité ou débité une fois, bon `EFFECTIF`. **Deux bons de 8** validés en même temps sur un stock de 10 : un seul passe, l'autre reçoit 422 `STOCK_INSUFFISANT` ; solde 2, jamais négatif ; un seul mouvement. |
| `SoldeConcurrentIT` (critère 1a) | **50 sorties concurrentes sur un stock de 10.** Débit gardé hors transaction : **exactement 10 réussites**, 40 refus `STOCK_INSUFFISANT`, solde 0. Débit en transaction par l'exécuteur : solde jamais négatif, et **solde = 10 − réussites**. |
| `ValidationEchecMiParcoursIT` (D1.6, « échec à mi-parcours ⇒ rien d'écrit ») | Panne simulée à l'enregistrement du **2ᵉ mouvement** d'un bon de deux lignes. **Entrée** : aucun mouvement, soldes à 0, coût CUMP inchangé, aucun point d'historique, **compteur de références inchangé** (pas de trou), bon toujours `SOUMIS` ; la même validation repasse ensuite normalement. **Sortie** : aucun produit débité, aucun mouvement de plus, bon `SOUMIS`. |
| Tests existants du Stock (125) | Inchangés et verts, dont `BonWorkflowServiceIT` (422 sans effet, transitions 409), `ValorisationServiceIT` (CUMP), `InventaireWorkflowServiceIT`, `ProduitStockBulkServiceIT`. |

---

## 7. Mise en production (procédure pour l'administrateur du serveur)

**Pas d'accès au VPS** (contrainte permanente, conception Q16, corrigée le 24/11). La mise en production de 1a n'est donc pas une étape de développement : c'est une **procédure écrite pour l'administrateur du serveur**, à jouer une seule fois, **au basculement final de la V3**, et non à la fusion de 1a.

Procédure complète : [`docs/exploitation/basculement-v3.md`](../exploitation/basculement-v3.md). Elle couvre, dans l'ordre :
1. la sauvegarde de la base `mongo:6` en service ;
2. la détection des doublons (`ops/mongo/detecter-doublons.js`, lecture seule), avec point d'arrêt si un cas est bloquant ;
3. les comptages de référence ;
4. les secrets `MONGO_ROOT_*` et `MONGO_APP_*` dans le `.env` du serveur ;
5. la bascule vers `mongo:7.0` en replica set authentifié, port Mongo non publié, restauration et contrôle des comptages ;
6. le premier démarrage (migrations et index), les vérifications, le retour arrière.

L'administrateur renvoie les résultats attendus listés en fin de procédure : rapport de doublons, comptages, registre et rapports des migrations, constats de vérification. **Aucun critère d'acceptation de 1a n'en dépend** : tous ont été vérifiés par les tests et dans des conteneurs locaux (§2.3, §8).

### 7.1 Poste de développement

Voir §2.4 : `docker-compose.dev.yml`, ou service MongoDB local passé en replica set.

**Constaté pendant la vérification** : le `.env` local de ce poste ne définit pas `JWT_SECRET`, exigé depuis le lot 0. `bootRun` échoue donc sans lui, indépendamment de 1a. Pour la vérification, il a été passé en argument avec une valeur factice ; le `.env` n'a été ni lu ni modifié.

---

## 8. Bilan des tests

Toujours `./gradlew test` complet, seul sur la machine, avant chaque commit :

| Étape | Commit | Tests | Échecs | Durée |
|---|---|---|---|---|
| Référence `refonte/v3` (lot 0) | — | 1 025 | 0 | — |
| 1. Spike Mongock | `f92c638` | 1 030 | 0 | 19 min (première mesure, machine chargée) |
| 2. Replica set et transactions | `73fc90e` | 1 040 | 0 | 5 min 17 |
| 3. Migrations | `803bcce` | 1 045 | 0 | 4 min 47 |
| 4. Index | `0876ff9` | 1 054 | 0 | 5 min 38 |
| 5. Verrou optimiste | `3bbe8de` | 1 055 | 0 | 7 min 37 |
| 6. Soldes et bons transactionnels | `31677ad` | 1 062 | 0 | 8 min 48 |
| 7. Documentation | dernier | 1 062 | 0 | 8 min 18 (réexécution forcée `--rerun`) |

**37 tests ajoutés** :
- `MongockSpikeIT` (5) ;
- `TransactionIT` (6) ;
- `GlobalExceptionHandlerConflitsTest` (3) ;
- `MigrationsIT` (5) ;
- `IndexesAttendusIT` (1), `CreateurIndexIT` (6), `DetecterDoublonsScriptTest` (2) ;
- `VerrouOptimisteStockIT` (1) ;
- `ValidationConcurrenteIT` (3), `SoldeConcurrentIT` (2), `ValidationEchecMiParcoursIT` (2) ;
- et 1 dans `AucunSecretLitteralTest`.

**Tests existants** : 5 classes renommées et adaptées (§3.5) et 3 adaptées aux nouvelles variables (§2.3). **Aucun test existant n'a dû être assoupli.**

**Critères d'acceptation de 1a** (conception, §4) :

| Critère | Statut | Où |
|---|---|---|
| `TransactionIT` | ✅ | §2.3 |
| `ValidationConcurrenteIT` | ✅ | §6.2 |
| `SoldeConcurrentIT` | ✅ | §6.2 |
| `MigrationsIT` | ✅ | §3.5. La baseline est réalisée par les gardes d'idempotence (§3.4). |
| `IndexesAttendusIT` et jeu piégé | ✅ | §4.4 |
| `docker-compose up` démarre un replica set initialisé | ✅ | Vérifié en copie isolée, pile prod complète (§2.3) et dev. |
| Compte rendu du spike | ✅ | §1 |

**Vérifications hors Gradle** :
- **pile prod complète** en copie isolée (§2.3) ;
- **dev** : `bootRun` deux fois sur un replica set jetable. Les deux démarrages réussissent et répondent 401 sans jeton ; le registre contient les 16 ChangeUnits, une entrée chacune ; 43 collections portent leurs index ;
- **script de doublons** : sur les bases locales et sur un jeu piégé (§4.3).

---

## 9. Frontend

Branche `refonte/v3-1a-socle-technique` créée dans `pointage-agents` depuis `refonte/v3` (`8540054`). **Aucune modification, aucun commit** :
- aucun contrat d'API ne change : chemins, champs, statuts HTTP ;
- le seul code d'erreur modifié (`STOCK_OPERATION_ERROR` → `STOCK_INSUFFISANT`, toujours en 422) n'est lu nulle part dans le front ;
- les nouveaux 409 (`CONFLIT_VERSION`, `CONFLIT_CONCURRENT`, `DOUBLON`) tombent dans la gestion des 409 déjà en place sur les bons (« Le statut de ce bon a changé entre-temps »), et ailleurs dans l'affichage générique des erreurs.

`ng build` n'a pas été relancé, aucun fichier n'ayant changé.

---

## 10. Ce qui reste

**Pour mettre 1a en production**
1. Fusion dans `refonte/v3` : faite.
2. Au basculement final de la V3, l'administrateur du serveur joue `docs/exploitation/basculement-v3.md` (§7), en commençant par `detecter-doublons.js`, et renvoie les résultats ; les doublons sont reportés au §4.5. Pas d'accès au VPS : aucune étape de développement n'en dépend.
3. **Risque à arbitrer** : aucune copie hors du serveur (Q16, écart avec le CDC §14). La procédure recommande à l'administrateur de rapatrier la sauvegarde d'avant bascule sur un poste sûr.

**Décisions métier relevées en 1a**
4. **Congés** : les demandes dont `nombreJours` a été réécrasé sans les jours fériés par l'ancien runner gardent cette valeur (§3.3, constat 1). Une ChangeUnit corrective est possible mais modifie des soldes : à décider avec la RH.

**Repris par les sous-branches suivantes**
5. **1b** :
   - `PieceBase` (avec `@Version`) sur les autres pièces ;
   - outbox, qui remplacera `apresCommit` et l'e-mail synchrone de création de bon ;
   - fin de `SuppressionDefinitiveService` ;
   - un test ArchUnit des appels interdits.
6. **1d** :
   - compléments d'affectation calculés à l'écriture, puis `0008` rendue ponctuelle ;
   - archivage de `employes_complet`, ce qui clôt la question de l'index `nomComplet` ;
   - `@Version` sur les référentiels employé et site. Les référentiels article, client, fournisseur et contrat le reçoivent en 1e et 1f.
7. **Compensations encore en place (D1.6)** :
   - clôture d'inventaire et import en masse : sous-branche des articles ou lot 3 ;
   - `OrdreFabricationService` : Production Chimie.

   Pour chacune, le retrait s'accompagne d'un test « échec à mi-parcours ⇒ rien d'écrit » sur le modèle de `ValidationEchecMiParcoursIT`.
8. **Lot 3 (magasins)** : soldes par site et inventaire par site, puis `0007` rendue ponctuelle ; décider si la clôture d'un inventaire global peut porter un solde en négatif. Aujourd'hui elle le peut, et `appliquerDelta` reste non gardé pour cette raison.
9. **1l** : scripts de sauvegarde quotidienne et de test de restauration (D15). L'authentification Mongo et le port non publié sont faits en 1a.

**Points techniques notés, non traités**
10. `TerrainExceptionHandler`, prioritaire sur `controllers.terrain`, ne traduit pas encore `CONFLIT_VERSION`, `CONFLIT_CONCURRENT` ni `DOUBLON`. C'est sans effet aujourd'hui : aucune entité Terrain n'a de `@Version`. En revanche, un doublon sur `sites_clients.code`, désormais indexé, y sortirait en erreur générique : à reprendre en 1d.
11. Versions de Testcontainers dépareillées (`junit-jupiter` 1.20.2, `mongodb` 1.19.2) : sans incidence constatée.
12. Les tests du spike (`spikemongock`, 5 tests, environ 1 minute) sont gardés comme tests de non-régression du comportement de Mongock (verrou, transaction, registre). Ils peuvent être retirés si leur durée gêne.
