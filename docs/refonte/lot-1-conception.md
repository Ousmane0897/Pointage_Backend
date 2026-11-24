# Lot 1 V3 — Conception du socle

Document de conception, rédigé le 23/11/2026 sur la branche `refonte/v3` (backend et frontend). **Aucun code n'a été modifié.** Conception validée le 23/11 avec corrections, intégrées dans la révision ci-dessous. **Révision validée le 24/11 ; la sous-branche 1a est lancée** (`refonte/v3-1a-socle-technique`, compte rendu dans `docs/refonte/1a-socle-technique.md`). **Correction du 24/11, au lancement de 1b : pas d'accès au VPS** (Q16, R.1) ; toute opération serveur est livrée comme procédure écrite pour l'administrateur du serveur (`docs/exploitation/`).

## Révision du 23/11

Le CDC est désormais versionné (`docs/cdc/PointIC_CDC.pdf`, version texte `docs/cdc/cdc-pointic-v1.4.txt`). Ses réponses remplacent les hypothèses « à confirmer » correspondantes. Les références « CDC §x » renvoient à ses sections.

### R.1 Questions tranchées par le CDC ou par vous

| Question | Réponse retenue | Source | Effet dans ce document |
|---|---|---|---|
| Q5 Magasins | Plusieurs magasins : central, atelier de production, stock déporté sur site si besoin ; transfert entre magasins par bon. Un seul stock dans PointIC, le magasin de l'Industrie en est une vue. | CDC §8.1 « Magasins », §8 intro | Nouveau référentiel `magasins` (D8). Un magasin n'est pas un site : un stock déporté est un magasin de type `DEPORTE` rattaché à un `siteId`. Bascule des soldes sur `magasinId` et bons de transfert : lot 3. |
| Q6 Sites | `SiteClient` = le « local » du CDC : unité servie, porteuse d'un QR de pointage et d'un QR d'appréciation, effectif prévu par local et par jour, rattachée à un client (et à un contrat). Pas de niveau « agence » supplémentaire. | CDC §6.1 « QR code », §6.2 « Contrôles », §3 référentiels, votre correction | D8 : champ `type` retiré du site, `clientId` obligatoire. §3.2 : les libellés « Agence … » des affectations RH se rattachent à un local par le fichier de décisions, sans objet agence. |
| Q7 Numérotation | Format `DEV-2026-0001` : type, exercice, compteur sur **4 chiffres minimum** (le compteur s'allonge au-delà de 9999). Exercice = année civile. Numérotation continue, par type et par exercice. | CDC §3 « Services communs », §14 « Intégrité comptable », votre correction | D4 réécrit. Codes de type repris du CDC (`DEV`, `FAC`, `BS`, `BE`, `OF`, `LOT`, `DD`, `BC`, `BCA`, `CHQ`). Les nouvelles séries partent de `0001` ; les références existantes restent telles quelles. 1b n'est plus bloquée. |
| Q8 Conservation (en partie) | Pièces et écritures : 10 ans (Acte uniforme OHADA), dans une archive longue durée distincte de la sauvegarde. GPS et photos de pointage : 12 mois (exemple déclaré à la CDP). Justificatifs médicaux : durée de l'absence plus le délai légal. Sanctions : selon le Code du travail. Puis purge ou anonymisation tracée. Sauvegarde quotidienne, conservée 30 jours, copie hors du serveur principal, restauration testée chaque trimestre. | CDC §14 « Conservation et archivage », « Sauvegarde », « Données personnelles » | D15 réécrit. **Reste à la Direction** : les autres durées (ex-employés, candidats, journaux), la déclaration à la CDP, le lieu d'hébergement, avec son conseil juridique (CDC §16). |
| Q9 Code agent | Code à 4 chiffres **choisi par l'agent** à la première connexion ; le SMS porte un **code d'activation** ; compte lié à **un seul téléphone** ; ce compte servira au pointage QR (lot 2), le PIN de kiosque disparaîtra. | CDC §13.2, §14 « Connexion des agents », §6.3 « Anti-fraude », votre correction | D11 réécrit, avec l'appareil lié et le pointage QR anticipés sans être implémentés. |
| Q11 Contrat | Le « contrat » des fiches minimales est le **contrat client** : client, sites couverts, prestations, effectif, montant mensuel HT, début, durée, reconduction, préavis, clause de révision, délai de paiement ; en option pénalités de non-couverture et volume d'heures. | CDC §9.4, §6.1 « Pénalités », « Contrats en heures », §15 lot 1 | D8 : fiche minimale définie. 1f n'est plus bloquée. |
| Q12 Familles | Six familles : matières premières, emballages, produits finis, consommables, EPI et tenues, matériel. Le matériel est une famille d'articles ; le parc (unités numérotées) porte **toujours** un `articleId`. | CDC §8.1 « Articles », §3, votre correction | D8, §2.2, §3.3 : `terrain_materiel` reçoit un `articleId` obligatoire et entre dans la migration des articles. 1e n'est plus bloquée par Q12. |
| Q13 Circuits | Le tableau des circuits du CDC §13 fait foi. Case « Direction en dernier » par circuit, cochée partout sauf les congés ; réglage Stock à trois positions (toujours, au-dessus du seuil, jamais) ; seuil Direction de départ : 0 F ; la Direction valide les congés des responsables ; une pièce née d'une pièce validée ne repasse pas en validation ; délégation tracée. | CDC §13 « Circuits de validation », §8.10, §5.3 « Validation », §13.4 « Seuils de référence » | D10 réécrit. 1h n'est plus bloquée. |
| Q14 Droits sensibles, 2FA | Cinq droits sensibles cochés un par un, jamais par défaut : salaires nominatifs, CA et rentabilité des sites, prix d'achat, exporter, valider en dernier. Matrice de départ par profil. Double authentification pour la Direction et les Finances. Blocage temporaire après **3** codes faux. Données de santé visibles par la RH et la personne seulement. | CDC §13.1 « Profils et droits », matrice des droits sensibles, §14 « Sécurité », « Hébergement et données de santé » | D6, D9 réécrits (seuil de blocage 3 au lieu de 5). 2FA par SMS, livrée en 1k. |
| Q15 RIB, IPRES, CSS | Le **mode de paiement** (virement, Wave, Orange Money, espèces) et son compte sont portés par l'employé ; leur changement passe par la RH avec une pièce. Les numéros IPRES et CSS **de l'employeur** sont des paramètres d'entreprise. | CDC §5.2 « Identité et poste », §13.2 « Mes informations », §5.7 « Identité » | D8, §3.5 : la grille ne porte plus rien de tout cela. Le numéro d'immatriculation IPRES **du salarié**, que le CDC ne cite pas, reste sur l'employé (`@Sensible`). Comme le code ne dit pas si les valeurs actuelles de la grille sont celles de l'employeur ou des salariés, la migration les fait trancher valeur par valeur. |
| Q-A Périmètre F009 / F014 | Oui : registre serveur minimal et Mon espace en consultation au lot 1. | votre réponse | §0.3 validé. |
| Q-B Emplacement | Documentation de refonte dans le backend ; l'audit est déplacé dans `docs/audit/` du backend. | votre réponse | Fait : `docs/audit/etat-des-lieux-v3.md` et `checklist-v3.csv`, retirés du dépôt front. §0.1 mis à jour. |
| Q-C Migrations | Mongock, avec le spike d'un jour, repli sur l'exécuteur maison. | votre réponse | D2 confirmé tel quel. |
| Q-D Pile technique | On garde Angular 19, Spring Boot et MongoDB, en service aujourd'hui, et non React, Node.js et PostgreSQL cités au CDC §14. Décision prise ; WOORTECH corrigera le CDC. | votre réponse du 23/11, confirmée le 24/11 | Tout le document reste valable. 1a n'est plus bloquée par aucune question. |
| Q16 Accès au serveur, stockage hors serveur | **Pas d'accès au VPS**, contrainte permanente (elle corrige la réponse « accès SSH : oui » donnée plus tôt le 24/11). Aucun stockage hors du serveur principal n'est prévu. | votre consigne du 24/11, au lancement de 1b | Toute opération serveur (sauvegarde, détection des doublons, copie anonymisée, mise en service, fermeture du port Mongo) est livrée sous forme de **procédure écrite dans `docs/exploitation/`**, destinée à l'administrateur du serveur, avec les résultats attendus à vous renvoyer. **Aucun critère d'acceptation ni aucune étape de développement ne dépend d'un accès au serveur** ; les migrations se testent sur des jeux de données construits dans les tests. 1a : procédure de mise en service à jouer par l'administrateur au **basculement final de la V3** (`docs/exploitation/basculement-v3.md`). D15 / 1l : sauvegarde et archive sur le serveur seulement ; **écart avec le CDC §14** (« copie hors du serveur principal »), signalé comme risque, non bloquant. §3.7 : la copie anonymisée devient une procédure facultative de l'administrateur. |

### R.2 Autres corrections

1. **Profils DIRECTION et ADMIN distincts** (D9). `DIRECTION` est un profil métier : tout en lecture, validation finale, tous les droits sensibles (CDC §13). `ADMIN` est un profil technique : comptes, profils, circuits, seuils et listes, imports, journal des accès (CDC §13.4, §5.7), sans aucun droit métier, sans droit sensible, jamais valideur. Le compte SUPERADMIN actuel reçoit un profil transitoire `MIGRE-SUPERADMIN` qui reproduit ses droits d'aujourd'hui, jusqu'à la réponse à Q1.
2. **D7 : dates d'effet passées interdites hors migration.** Seule une ChangeUnit peut écrire une version de date d'effet passée. Le niveau VALIDATION pour une date passée disparaît.
3. **Propriétaire des fournisseurs : les Finances** (CDC §3, §8.1 « Fournisseurs »), et non Stock / Achats. Le Stock y ajoute délais constatés et évaluation annuelle.
4. **Périmètre du compte** (sites, magasin), cité par le CDC §13.1 : ajouté à `comptes` (D9).
5. **Sauvegardes** alignées sur le CDC §14 : rétention 30 jours, restauration testée chaque trimestre, plus une archive longue durée annuelle distincte (D15).

### R.3 Points relevés à la lecture du CDC, à votre attention

- **Pile technique.** Le CDC §14 « Technologies » cite **React, Node.js, PostgreSQL**. Cette conception garde la pile en service (Angular, Spring Boot, MongoDB), puisque le CDC §6 précise que l'Exploitation « est déjà en service » et demande d'indiquer ce qui existe. C'était la question **Q-D** : vous avez confirmé ce choix le 23/11 (R.1). **La décision est prise : WOORTECH corrigera le CDC §14.** Ce n'est pas un blocage ; d'ici là, l'écart entre le CDC et la pile retenue est connu et assumé.
- **Date de référence d'un paramètre.** Le CDC §13.4 dit qu'un changement « s'applique aux pièces créées après sa date d'effet ». C'est la règle par défaut (D7). Exception proposée pour la paie : un bulletin lit les valeurs en vigueur **sur sa période**, comme le demande le CDC §5.7 (« un changement s'applique à partir du mois choisi et ne touche pas aux paies déjà clôturées »).
- **Valeurs de départ divergentes.** Le CDC §13.4 fixe des valeurs de départ qui diffèrent du code actuel : tolérance de retard 10 min (15 aujourd'hui, `rh.pointage.tolerance-retard-minutes`), stock dormant 90 jours (3 mois côté serveur, 6 mois côté front). La migration reprend les valeurs **actuelles**, pour ne rien changer à la mise en service. Les valeurs du CDC sont proposées à la Direction comme une version future datée, à confirmer dans « Seuils et listes » (CDC §16 : « les confirmer suffit »).
- **Circuit des bons.** Le CDC (§13, §8.10) met la Direction en dernier sur tous les bons. Aujourd'hui, un SUPERADMIN ou un CONTROLEUR_STOCK valide seul. Le circuit de départ du lot 1 suit le CDC : un bon validé par le magasinier attend ensuite la Direction. Le contrat d'API (`/valider`, 409 sur transition invalide) est conservé, mais le passage à `EFFECTIF` n'a lieu qu'à la dernière étape. Le front doit afficher l'étape en cours.
- **Hors lot 1, noté pour plus tard** : méthode de valorisation unique CUMP (CDC §8.4, alors que 7.6 propose aussi DERNIER_PRIX et FIXE) ; « un seul stock » incluant le stock chimie (CDC §8 intro) ; hors ligne et PWA du pointage (CDC §14 « Mobile ») ; WhatsApp en option (CDC §14, §16).

### R.4 Questions restantes

Pour la Direction : **Q1, Q2, Q3, Q4, Q8 (reste), Q10**. Plus aucune pour vous : Q16 est tranchée le 24/11 (R.1). **Contrainte permanente : pas d'accès au VPS.** Toute opération serveur est une procédure écrite pour l'administrateur du serveur (`docs/exploitation/`), avec les résultats attendus à renvoyer ; aucun critère d'acceptation ni aucune étape de développement n'en dépend. Voir §5.

## 0. Cadre

### 0.1 Sources

| Source | Chemin |
|---|---|
| État des lieux V3 (audit du 20/11/2026) | `docs/audit/etat-des-lieux-v3.md` du backend (cité « audit » ; déplacé depuis le dépôt front le 23/11) |
| Checklist V3 | `docs/audit/checklist-v3.csv` du backend |
| Lot 0 sécurité | `docs/securite/correctifs-lot-0.md` du backend (cité « lot 0 ») |
| Documentation des modules | `CLAUDE.md`, `.claude/docs/module-*.md` du backend |
| CDC V3 | WTK-CDC-2026-005 V1.4 : `docs/cdc/PointIC_CDC.pdf` (référence), `docs/cdc/cdc-pointic-v1.4.txt` (version texte, cité « CDC §x »). La première version de ce document a été écrite sans lui ; la révision du 23/11 l'intègre. |

**Convention des preuves** (la même que l'audit) : `back:` désigne `src/main/java/com/example/Pointage_Cleanic/` du backend, `front:` désigne `src/app/` du frontend, et les fichiers de racine sont nommés tels quels.

« **À confirmer** » marque une hypothèse que ni le code ni le CDC ne tranchent. Après la révision du 23/11, il ne reste que des points techniques, tranchés dans leur sous-branche.

### 0.2 Constats vérifiés sur `refonte/v3` en complément de l'audit

L'audit porte sur `feature/reclamation-qr-site` et sur un arbre de travail non commité. `refonte/v3` part de `main` et contient le lot 0. Constats revérifiés :

| Constat | Preuve |
|---|---|
| Le lot 0 est inclus (`fix/securite-lot-0` est un ancêtre de `refonte/v3`). Les QR d'agence et les réclamations **n'y sont pas** : aucune occurrence de `reclamation` ni de `AgenceQr` dans `back:`. | `git merge-base --is-ancestor` ; grep |
| `ParametresPointage` n'existe pas sur cette branche : il vient de la branche QR. Il y a donc 5 singletons de paramètres, pas 6. | `back:entities/` |
| Aucun bean `MongoTransactionManager`, aucun `@Transactional`, aucun `@Version`. | `back:configurations/MongoConfigurations.java` ; grep |
| Seuls deux fichiers créent des index par code. Ailleurs, `@Indexed`/`@CompoundIndex` restent inopérants : 15 entités RH, 11 Stock v2, 3 Terrain, 5 Chimie. C'est le cas de `DossierEmploye.matricule` unique et de `StockParSite (produitId, siteId)` unique : **des doublons peuvent déjà exister en base**. | `back:configurations/ProductionChimieIndexesConfig.java`, `TerrainIndexesConfig.java`, `back:entities/rh/DossierEmploye.java`, `back:entities/stockv2/StockParSite.java` |
| Les tests tournent déjà sur un replica set (`MongoDBContainer("mongo:7.0")`), la production en `mongo:6` standalone. | `src/test/.../MongoTestContainer.java`, `docker-compose.yml` |
| 12 `CommandLineRunner` (`@Order` 900 à 1200) jouent seeds et migrations à chaque démarrage, sans registre de ce qui a été appliqué. | `back:DataLoader/` |
| `EmployeCompletService` écrit et supprime toujours dans `employes` (synchronisation legacy). En revanche, `EmployesController`, cité par `CLAUDE.md`, n'existe pas. | `back:services/EmployeCompletService.java` (l. 119, 189, 226) |
| 13 entités portent un `byte[]`. | grep `byte[]` dans `back:entities` |
| Front : `shared/` ne contient que `confirm-dialog` et `text-normalize.util.ts`. Les gardes sont `auth.guard.ts` et `module.guard.ts` (lot 0). | `front:shared/`, `front:guards/` |
| `Droits` relit `Utilisateur.modulesAutorises` (booléens) et le rôle (`utilisateur`, sinon `login`). `RoleAdmin` ne contient pas SUPERADMIN. | `back:security/Droits.java`, `back:Enum/RoleAdmin.java` |

### 0.3 Écart de périmètre (validé le 23/11)

Les lignes de checklist transmises **excluent F009** (registre des pièces, barre Lit / Alimente) **et F014** (Mon espace). Or la définition du lot 1 dans le CDC (§15) cite « registre des pièces et liens entre modules » et « Mon espace en consultation », et le test de recette exige que les agents « ne voient que Mon espace ».

Retenu (Q-A) : un registre des pièces **minimal côté serveur**, alimenté par les événements (1b), et un Mon espace **en consultation limitée** dans 1m : mes informations, mon planning et mes pointages, mon solde de congés, mes bulletins validés (sous-rubriques du CDC §13.2, sans les demandes). Mon espace est ouvert à **tout salarié** ayant un compte, « de l'agent à la Directrice » (CDC §13.2), pas seulement aux agents.

La barre Lit / Alimente (F009) et Mon espace complet (F014 : demandes, attestations, EPI, avances) restent aux lots 2 et 6.

---

## 1. Décisions d'architecture

Format : **Contexte** (avec preuves), **Décision**, **Écarté**, **Conséquences**.

### D1 — Transactions MongoDB

**Contexte.** MongoDB tourne en standalone, sans transaction (audit §5.4). La cohérence multi-document repose sur des compensations écrites à la main :
- `back:services/stockv2/MouvementBonGenerator.java` ;
- `InventaireService.cloturer` ;
- `ProduitStockService` (bulk) ;
- `OrdreFabricationService.lancer` / `annuler`.

`StockBalanceService.appliquerDelta` fait lecture, addition, puis `save` : des mises à jour peuvent être perdues. `BonSortieService.valider` teste `statut == SOUMIS` puis écrit sans condition : en concurrence, les mouvements peuvent être générés deux fois (audit, Stock, « Points vérifiés »). Un arrêt du processus au milieu d'un bon laisse un état partiel.

**Décision.**
1. **Replica set à un nœud** en développement et en production : `mongod --replSet rs0`, plus un conteneur d'initialisation idempotent (`rs.initiate()` s'il n'est pas déjà initié), et une URI avec `?replicaSet=rs0`. Montée de version `mongo:6` → `mongo:7.0`, pour s'aligner sur Testcontainers (**à confirmer** : compatibilité des données de production, montée par `mongodump`/`mongorestore`).
2. Bean `MongoTransactionManager` dans `MongoConfigurations`, plus `TransactionTemplate` pour les cas programmatiques.
3. **Convention `@Transactional`** :
   - obligatoire sur toute méthode de service **publique** qui écrit plus d'un document ou qui écrit une pièce et son événement (D5) ;
   - jamais sur un contrôleur ;
   - aucun appel externe dans une transaction (SMTP, SMS, Google) : ces appels passent par l'outbox ;
   - transactions courtes, sous la limite de 60 s de Mongo ;
   - `readConcern`/`writeConcern` `majority`.
4. **Verrou optimiste** : `@Version Long version` sur toute pièce et tout référentiel. Un conflit donne **409 `CONFLIT_VERSION`**, et le client relit.
5. **Soldes** :
   - mise à jour atomique par `findAndModify` avec filtre de garde (`{produitId, siteId, quantite: {$gte: q}}` + `$inc: {quantite: -q}`) ;
   - aucun résultat donne 422 `STOCK_INSUFFISANT` et annule la transaction ;
   - les validations utilisent une transition conditionnelle (`{_id, statut: 'SOUMISE', version}` → `$set statut`), ce qui empêche la double validation.
6. Les compensations manuelles sont retirées **au fil des sous-branches qui touchent ces services**, pas en une fois. Chaque retrait vient avec un test « échec à mi-parcours ⇒ rien d'écrit ».

**Écarté.** Garder les compensations : elles ne protègent pas d'un arrêt du processus ; replica set à 3 nœuds : un seul serveur est connu, il n'apporte pas de haute disponibilité sur une machine unique ; verrou pessimiste par document « lock » : réinvente ce que fait la transaction.

**Conséquences.** Opération d'exploitation sur le VPS, livrée comme procédure écrite pour l'administrateur du serveur (pas d'accès au VPS, Q16 ; `docs/exploitation/basculement-v3.md`) ; les tests IT existants restent valides (déjà sur replica set) ; tout test « concurrence » devient écrivable : 2 validations simultanées ⇒ 1 seule réussit.

### D2 — Migrations de données versionnées

**Contexte.** Les 12 runners de `back:DataLoader/` sont idempotents par construction, mais rien ne dit lesquels ont tourné. Leur ordre dépend de `@Order`, ils se rejouent à chaque démarrage, et aucun verrou n'empêche deux instances de les jouer en même temps. `DataLoader.seedSuperadmin` mélange seed et migration.

**Décision.**
- **Mongock 5** (`io.mongock:mongock-springboot-v3` + `mongodb-springdata-v4-driver`) :
  - une `@ChangeUnit` par migration (id, auteur, ordre), avec `@Execution` et `@RollbackExecution` ;
  - registre `mongockChangeLog`, verrou `mongockLock` ;
  - exécution au démarrage, avant que l'application n'accepte du trafic.
- Rejeu sûr :
  - une ChangeUnit déjà appliquée n'est pas rejouée ;
  - une migration de données écrit son **rapport** dans `migration_rapports` (D-M, §3) ;
  - une migration qui exige des décisions humaines lit un **fichier de décisions** versionné (`src/main/resources/migrations/decisions/*.csv`) et s'arrête en échec (démarrage refusé, message explicite) si des cas restent non résolus.
- Conversion des 12 runners :
  - chacun devient une ChangeUnit d'ordre `0001` à `0012` ;
  - une ChangeUnit `0000-baseline` marque comme appliquées celles dont l'effet est déjà présent (même test d'idempotence que le runner actuel) ;
  - les **seeds de démonstration** (`TerrainDataLoader`, grilles et formations d'exemple de `DataLoader`) passent sous un profil `demo`, absent en prod (audit §4.3 « Seed de sites fictifs actif en production »).
- **Spike préalable** (1 jour, retenu le 23/11, Q-C), premier livrable de 1a : Mongock 5 avec Boot 3.3.6 / Spring Data MongoDB 4.3.6 et les transactions. **Repli** si le spike échoue : un exécuteur maison d'environ 150 lignes (interface `Migration { String id(); void executer(MongoTemplate) }`, collection `migrations_appliquees`, verrou par document `findAndModify` à bail).

**Écarté.** Garder des `CommandLineRunner` idempotents : pas de registre ni de verrou, et chaque démarrage coûte un balayage complet ; Flyway / Liquibase : le support MongoDB est payant ou limité, et ces outils sont pensés pour SQL ; scripts mongosh manuels : ni testés ni rejoués de la même façon partout.

**Conséquences.** Une nouvelle dépendance. Chaque migration a un test IT sur jeu de données (Testcontainers). `@Order` disparaît du dossier `DataLoader`, qui est renommé `migrations/`.

### D3 — Création des index

**Contexte.** `spring.data.mongodb.auto-index-creation` est désactivé. Les index déclarés par annotation ne sont pas créés (CLAUDE.md, module Terrain ; §0.2). Index unique de code de site non créé (audit, Exploitation §1).

**Décision.**
1. Les index sont créés **par des ChangeUnits** (D2), un fichier par module (`IndexesRhChangeUnit`…). Les deux classes `*IndexesConfig` existantes y sont reprises.
2. Les annotations `@Indexed`/`@CompoundIndex` sont **retirées** des entités, pour éviter une documentation trompeuse. La liste attendue vit dans une seule classe `IndexesAttendus`.
3. Test `IndexesAttendusIT` : il applique les migrations sur une base vide, puis compare `listIndexes()` à `IndexesAttendus`. Il échoue s'il manque un index ou s'il en existe un non déclaré.
4. **Avant tout index unique**, la ChangeUnit cherche les doublons (`$group` + `$match count > 1`). En présence de doublons, elle écrit le rapport et **échoue**, sans créer l'index ni dédoublonner en silence.

**Écarté.** Activer `auto-index-creation` : non versionné, se déclenche au démarrage sans rapport, et échoue mal sur des doublons.

**Conséquences.** Les doublons réels (matricule, agentId, code produit, couple produit/site) ne sont connus qu'après le passage, en lecture seule, du script de détection par l'administrateur du serveur (procédure `docs/exploitation/basculement-v3.md`, résultat à renvoyer). Le développement n'en dépend pas : la détection est testée sur des jeux piégés construits dans les tests.

### D4 — Modèle « pièce » commun

**Contexte.** Quatre services de compteurs sans service commun (`back:services/stockv2/CompteurStockService.java`, `productionchimie/CompteurOfService.java`, `productionchimie/CompteurLotService.java`, `terrain/CompteurTerrainService.java`). Séquence journalière (`BE-yyyyMMdd-NNN`, sauf `CH` annuel), référence prise **dès le brouillon** et brouillon supprimable, d'où des trous ; `genererReference` utilise `LocalDate.now()` sans fuseau (audit §5.3). 39 `@DeleteMapping` (audit §5.2), dont des pièces validées supprimables par `back:services/stockv2/SuppressionDefinitiveService.java`.

**Décision.**
- **Classe de base `PieceBase`** (incorporée dans chaque document de pièce) :
  - `type` (`TypePiece`). Codes imposés par le CDC §3 : `DEV`, `FAC`, `BS`, `BE`, `OF`, `LOT`, `DD`, `BC`, `BCA`, `CHQ`. Codes proposés pour les pièces que le CDC ne code pas : `CNG` (congé), `INV`, `MVT`, `BUL` (bulletin), `DSO` (déclaration sociale), `SAN` (sanction), `IMP` (import) ;
  - `numero` (nul tant que la pièce n'est pas validée), `exercice` ;
  - `statut` (`BROUILLON → SOUMISE → VALIDEE`, branches `REFUSEE`, `ANNULEE`, `ABANDONNEE`) ;
  - `creePar` / `creeLe`, `soumisePar` / `soumiseLe`, `valideePar` / `valideeLe` (identifiants de compte + **instantané** du nom) ;
  - bloc `annulation {par, le, motif, pieceCorrectiveId}` et `version`.
- **Numérotation** :
  - un service unique `NumerotationService`, une collection unique `compteurs_pieces`, `_id = "<TYPE>-<EXERCICE>"` ;
  - `findAndModify($inc)` **dans la transaction de validation** : si la validation échoue, l'incrément est annulé avec elle, donc ni trou ni doublon ;
  - format `<TYPE>-<EXERCICE>-<NNNN>` (CDC §3 : `DEV-2026-0001`), compteur sur **4 chiffres minimum**, qui s'allonge sans troncature au-delà de 9999 (`BS-2027-12345`). Le tri des numéros se fait donc sur `(exercice, compteur)`, jamais sur la chaîne ;
  - exercice = **année civile** (Q7), calculée en `Africa/Dakar` à la date de validation, via l'horloge injectée (`back:configurations/TimeConfig.java`). L'heure du serveur fait foi (CDC §14 « Intégrité comptable ») ;
  - un brouillon s'affiche sous `BROUILLON-<6 derniers caractères de l'id>`.
- **Fin des suppressions** :
  - plus aucun `@DeleteMapping` métier : `POST /{id}/annuler {motif}` (au moins 10 caractères, comme `MOTIF_MIN` de `SuppressionDefinitiveService`) pour une pièce validée, `POST /{id}/abandonner` pour un brouillon (conservé, statut `ABANDONNEE`) ;
  - les référentiels se **désactivent** (`actif=false`, motif), sans suppression ;
  - un test ArchUnit interdit `deleteById`, `delete(`, `deleteAll`, `remove(` hors d'une liste blanche nommée (jetons de réinitialisation, éphémères `sms_envoyes` en dev, `PurgeService` de D16, nettoyage de l'outbox traitée) ;
  - `RoutesSuppressionIT` vérifie qu'aucune route `DELETE` n'existe hors liste blanche (même principe que `RoutesPubliquesIT` du lot 0).
- **Annulation d'une pièce validée** :
  - elle ne touche pas aux effets directement : elle émet `PieceAnnulee` (D5), et les consommateurs produisent les **contre-effets** (mouvements inverses, contre-écritures futures) ;
  - `SuppressionDefinitiveService` et `stockv2_suppressions` sont remplacés ; la collection est conservée en lecture pour l'historique.
- **Registre des pièces** (`registre_pieces`, une ligne par pièce : type, numéro, statut, site, montant, liens `amont[]` / `aval[]`) :
  - alimenté par les événements (D5) ;
  - base de F009 au lot 2, et de la recherche transverse.

**Écarté.** Numéro au brouillon : trous inévitables ; séquence sans transaction avec numéro « réservé puis libéré » : complexe et sujet aux trous ; suppression logique par `deleted=true` : ne trace ni qui ni pourquoi, et contredit la règle 5.

**Conséquences.** Les contrats du front changent : les boutons « Supprimer » deviennent « Annuler » ou « Abandonner », et le numéro est vide en brouillon ; les références existantes (`BE-20260617-001`) sont conservées telles quelles : pas de renumérotation de l'historique. Leur format diffère du nouveau, donc pas de collision : les nouvelles séries commencent à `0001` sur l'exercice de mise en service.

### D5 — Événements de domaine et outbox Mongo

**Contexte.** Les effets sont des appels directs. `MouvementBonGenerator` est appelé par `BonSortieService.valider`. Les notifications partent pendant la requête (`back:services/stockv2/BonMailNotificationService.java`, SMTP synchrone, pas d'`@EnableAsync`). Il n'existe ni journal d'événements ni comptabilité (F104).

**Décision.**
- Collection **`evenements_domaine`** :
  - `{id, type, version, agregatType, agregatId, siteId, survenuLe, auteurCompteId, correlationId, payload, statut (A_TRAITER, EN_COURS, TRAITE, EN_ERREUR), tentatives, prochainEssai, derniereErreur}` ;
  - écrite **dans la même transaction** que la pièce (`EvenementPublisher.publier(evt)`, qui exige une transaction active).
- **Dispatcher** `@Scheduled(fixedDelay = 2 s)` :
  - il réclame un lot par `findAndModify` (`A_TRAITER` et `prochainEssai <= now` → `EN_COURS`, bail de 5 min) ;
  - il appelle les consommateurs enregistrés pour le type ;
  - ensuite `TRAITE`, ou un nouvel essai à délai exponentiel (1 min → 1 h, 10 tentatives) puis `EN_ERREUR` ;
  - une alerte apparaît sur l'écran d'exploitation.
  - Sûr en multi-instance grâce au bail, sans ShedLock.
- **Consommateurs idempotents** : chaque consommateur insère `{consommateur, evenementId}` dans `evenements_consommes` (index unique), dans **sa propre** transaction avec ses écritures. Un doublon est ignoré.
- **Règle 3** :
  - un module n'appelle jamais le service d'écriture d'un autre module pour produire un effet ;
  - les **lectures** de référentiels restent des appels directs, en lecture seule (`ReferentielSiteService`…) ;
  - un test ArchUnit interdit à `services.<moduleA>` d'importer un service d'écriture de `services.<moduleB>`, sur liste explicite.
- Les événements **traités ne sont pas supprimés** : ils servent de journal rejouable pour les futures écritures comptables (lot 4/7). Leur conservation est un paramètre daté (D16).

**Écarté.** Change Streams : demandent la gestion des jetons de reprise, et il n'y a pas d'événement si l'écriture n'a pas eu lieu ; registre d'événements Spring Modulith : il suppose des packages par module (ici par couche : `controllers/rh`, `services/rh`…) et ne conserve pas un journal métier interrogeable ; courtier externe (Kafka, RabbitMQ) : nouvelle infrastructure sans besoin de volume.

**Conséquences.** Les effets deviennent asynchrones (délai d'environ 2 s). Exemple : la validation d'un bon de sortie rend la main avant que les soldes soient débités. **Exception voulue** : les soldes de stock restent débités **dans la transaction de validation**. Le contrôle « stock insuffisant ⇒ refus » doit être synchrone, et le stock fait partie de la pièce, pas d'un autre module. Ce point est à revoir au lot 3.

### D6 — Journal d'audit et journal des accès

**Contexte.** Il n'existe pas de journal global (F004), ni `AuditorAware`, ni `@CreatedBy`. On trouve des historiques locaux : `back:entities/rh/HistoriqueValidationConge.java`, `entities/stockv2/EntreeHistorique.java`, `entities/productionchimie/HistoriqueStatutOf.java`. Aucune trace des connexions ni des échecs (F012).

**Décision.**
- **`journal_audit`**, écrit dans la transaction métier :
  - contenu : `{le, compteId, nomInstantane, action (CREATION, MODIFICATION, SOUMISSION, VALIDATION, REFUS, ANNULATION, DESACTIVATION, IMPORT, EXPORT, PARAMETRE), objetType, objetId, numero, changements: [{champ, avant, apres}], motif, ip, correlationId}` ;
  - les champs `@Sensible` (D9) sont journalisés **masqués** (`"***"`) ;
  - le service `JournalAudit` calcule le différentiel à partir de l'état relu en début de transaction.
- `AuditorAware<String>` (compte courant via `back:services/terrain/CurrentUserProvider.java`) pour `@CreatedBy` / `@LastModifiedBy` sur les référentiels.
- **`journal_acces`** :
  - connexions réussies et échouées (écouteurs `AuthenticationSuccessEvent` / `AbstractAuthenticationFailureEvent`, plus la connexion agent de D11), refus 403, exports (D13), consultation de données sensibles (fiche de paie, salaire, RIB) ;
  - champs : `{le, identifiantSaisi (masqué partiellement), compteId?, resultat, motif, ip, userAgent}`.
- **Blocage** : après N échecs en M minutes, le compte est verrouillé T minutes (3 paramètres datés). Valeurs initiales **3** / 15 / 15 : le CDC (§13.1 « Journal des accès », §14 « Sécurité ») impose le blocage après 3 codes faux, pour les mots de passe comme pour les codes agents. Le limiteur du kiosque (`pointage.limite.*`, lot 0) est un autre mécanisme et reste inchangé jusqu'au lot 2.
- Le journal des accès trace aussi **tout changement de droit** et les actions sensibles citées par le CDC §13.1 (export de la paie, consultation des salaires, validation finale). Il n'est pas modifiable.
- Les historiques locaux restent en place (contrats d'API du front), mais ne sont plus la source de l'audit.
- Écran de consultation filtrable, droit `parametres.journaux` au niveau LECTURE.

**Écarté.** Audit par `AbstractMongoEventListener` : hors transaction, sans motif ni auteur fiable ; journaux applicatifs comme audit : non interrogeables, et purgés par rotation.

**Conséquences.** Le volume de `journal_acces` est à surveiller (le kiosque n'y écrit pas : le pointage n'est pas une connexion). Les durées de conservation sont fixées en D16.

### D7 — Paramètres datés et listes de choix

**Contexte.** Les singletons sont écrasés sans date d'effet (F006) :
`back:entities/rh/ParametresPaie.java`, `ParametresConges.java`, `back:entities/terrain/ParametresEscalade.java`, `back:entities/productionchimie/ParametresProductionChimie.java`, `back:entities/stockv2/ParametrageValorisation.java`.

Des valeurs vivent aussi dans `application.yml` (`rh.pointage.tolerance-retard-minutes`, `app.conges.jours-acquis-par-mois`, `rh.import.bulk.max-size`) ou en constantes, par exemple `ValorisationSupport` (`SEUIL_DERIVE = 20`…) et `SanctionService` (8 j, 3 j, 2 sanctions/12 mois). D'autres sont **dupliquées et divergentes** avec le front (`front:constants/stock.constants.ts` : dormance 6 mois contre 3 côté back, dérive 30 % contre 20 %, audit Stock « Règles codées en dur »). Les types de congé, d'absence et les majorations d'heures supplémentaires sont des enums (`back:Enum/rh/TypeConge.java`, `TypeMajoration.java`).

**Décision.**
- **Catalogue des clés en code** (`CatalogueParametres`), une entrée par règle chiffrée :
  - `{cle ("stock.valorisation.seuilDerivePct"), type (ENTIER, DECIMAL, DUREE, BOOLEEN, BAREME, JSON), unite, bornes, valeurParDefaut, moduleProprietaire, sousRubriqueDroit}` ;
  - **le catalogue est du code, les valeurs sont des données.**
- **`parametres`** : `{cle, valeur, dateEffet, saisiPar, saisiLe, motif, annulee?}`.
  - On n'édite jamais une version : une erreur s'annule (règle 5), une évolution est une nouvelle version datée ;
  - **une date d'effet passée est interdite hors migration** : `dateEffet >= aujourd'hui` (en `Africa/Dakar`) pour toute saisie par l'API, sinon 422 `DATE_EFFET_PASSEE`. Seule une ChangeUnit (D2) peut écrire une version rétroactive, et elle l'écrit avec l'auteur « migration » ;
  - une version dont la date d'effet est atteinte est immuable ; une version future peut être annulée (motif obligatoire) avant sa date ;
  - index unique `(cle, dateEffet)`.
- **Lecture à date** : `ParametreService.valeurA(cle, LocalDate date)` renvoie la dernière version non annulée de `dateEffet <= date`, avec un cache invalidé à l'écriture. Un calcul passe **toujours** une date de référence, jamais `now()` :
  - par défaut, la **date de création de la pièce** : « chaque changement s'applique aux pièces créées après sa date d'effet » (CDC §13.4) ;
  - exception déclarée par le type de pièce : le **bulletin** lit les valeurs en vigueur sur **sa période** (CDC §5.7 : « un changement s'applique à partir du mois choisi et ne touche pas aux paies déjà clôturées »). Le bulletin de mars recalculé en avril garde les taux de mars.
- **Paramètres composites** (barème IR par tranches, paliers d'ancienneté des congés) : type `BAREME` ou `JSON` avec schéma. Les 5 singletons sont migrés vers des clés du catalogue (une ChangeUnit par singleton). La première version prend `dateEffet = 01/01/2000`, pour couvrir tout l'historique avec la valeur actuelle, et l'auteur « migration ».
- **`listes_choix`** : `{liste ("rh.typesConge"), code, libelle, ordre, actif, dateDebut, dateFin, attributs (ex. décompte du solde oui/non)}`.
  - Les enums métier extensibles y migrent au fil des lots ; les enums de **statut** restent du code ;
  - lot 1 : le moteur, plus les listes nécessaires aux fiches minimales (motifs de désactivation, motifs d'annulation, familles d'articles avec leur compte comptable en attribut, natures, unités, modes de paiement, types de magasin) ;
  - les listes suivent la même règle de date que les paramètres : `dateDebut` passée interdite hors migration.
- **Écran « Seuils et listes »** (front), sous Paramètres et accès (CDC §13.4) :
  - une page par module ;
  - pour chaque clé : valeur courante, valeur future programmée et historique ;
  - saisie d'une nouvelle valeur datée avec motif, date d'effet à partir d'aujourd'hui (sélecteur de date borné) ;
  - droit `parametres.seuils` niveau SAISIE.
- **Valeurs de départ.** La migration des singletons reprend les valeurs **actuelles** du code (aucun changement de comportement à la mise en service). Quand le CDC §13.4 « Seuils de référence » fixe une autre valeur de départ (tolérance de retard 10 min contre 15 aujourd'hui ; stock dormant 90 jours contre 3 mois côté serveur et 6 côté front), la ChangeUnit l'écrit comme une **version future** datée au premier jour du mois suivant la mise en service, que la Direction confirme ou annule dans « Seuils et listes » (CDC §16 : « les confirmer suffit »).
- Le front ne garde **aucune** valeur de seuil : `GET /api/parametres/valeurs?module=stock&date=` remplace `front:constants/stock-v2-valorisation.constants.ts` et `stock.constants.ts`. Les constantes réglementaires (`paie.constants.ts`) suivent le même chemin.

**Écarté.** Un document « jeu complet » par module et par date : une modification d'une seule valeur recopie tout, et les différentiels sont illisibles ; Spring Cloud Config : pas de notion de date d'effet.

**Conséquences.** Les constantes migrent au rythme des lots. Le lot 1 livre le moteur, les 5 singletons, les 3 propriétés `application.yml` et les seuils Stock dupliqués avec le front ; les services lisent par `ParametreService` ; les getters de singletons disparaissent.

### D8 — Référentiels uniques

**Contexte.** Le CDC §3 « Référentiels partagés » fixe un propriétaire par donnée. Dans le code, la seule donnée partagée par identifiant est le site (`sites_clients`, via `back:services/stockv2/ReferentielSiteService.java`). Ailleurs (audit §1, « Données dupliquées ») :
- employé en 3 collections ;
- article en 5 listes ;
- client et fournisseur en texte libre ;
- site par nom côté RH (`back:entities/rh/AffectationSite.java`, `back:util/SiteAffecteUtils.java`).

**Décision.** Un propriétaire par référentiel. Les autres modules le **lisent**, par identifiant, au travers d'un service de lecture `Referentiel<X>Lecture`, et ne recopient que des **instantanés figés dans une pièce** (nom, code, libellé au moment de la pièce, dans un sous-objet `instantane`).

| Référentiel | Collection cible | Propriétaire | Champs minimaux | Références (par id) | Instantanés autorisés |
|---|---|---|---|---|---|
| Employé | `dossiers_employes` (conservée) | RH / Personnel (CDC §3) | matricule, agentId (code de kiosque, voué à disparaître au lot 2, voir D11), nom, prénom, **téléphone (E.164)**, e-mail, poste, département, statut, dateEmbauche, dateSortie, personne à prévenir, affectations[{siteId, dateDebut, dateFin, horaires}], superieurHierarchiqueId, **mode de paiement** {mode (liste : VIREMENT, WAVE, ORANGE_MONEY, ESPECES), compte (RIB ou numéro de portefeuille)} et **n° IPRES du salarié** (`@Sensible`, CDC §5.2) | siteId → `sites_clients`, categorieCode → grille, superieur → dossier | nom, prénom, matricule dans congés, bulletins, absences, HS, sanctions, bons ; mode et compte de paiement figés dans le bulletin |
| Site (« local » du CDC) | `sites_clients` (conservée) | Commercial à la signature du contrat ; Exploitation pour le GPS, le rayon, les horaires et les QR (CDC §3, §6.1). Au lot 1, sans module Commercial : voir Q10 | code (unique), nom, **clientId (obligatoire)**, **contratClientId**, adresse, contact sur place, GPS, rayon (200 m par défaut, CDC §6.3), horaires et jours de service, superviseurId, actif. **Réservés pour le lot 2** : `qrPointage` et `qrAppreciation` (jetons régénérables, CDC §6.1), effectif prévu par jour | clientId, contratClientId, superviseurId | code, nom dans bons, mouvements, pointages, affectations |
| Magasin | **`magasins`** (nouvelle, Q5) | Stock | code (unique), libellé, type (liste : CENTRAL, ATELIER, DEPORTE), **siteId (obligatoire si DEPORTE, interdit sinon)**, actif | siteId | code, libellé dans bons et mouvements (lot 3) |
| Article | **`articles`** (renommage de `stockv2_produits`, **ids conservés**) | Stock (CDC §3, §8.1 « Catalogue unique ») | code (unique), libellé, **famille** (liste à 6 valeurs, Q12), nature (CONSOMMABLE, ou DURABLE avec durée de vie en mois), unité (liste), catégorie, seuil d'alerte, stock de sécurité, suivi par lot (oui / non), fournisseurPrincipalId, actif, coût (`@Sensible PRIX_ACHAT`). Compte comptable porté par la famille | categorieId, fournisseurId | code, libellé, unité, coût unitaire dans lignes de bon, mouvements, inventaires |
| Extensions article | `production_chimie_matieres_premieres` (+ `articleId`), `produits_phyto` (+ `articleId`), **`terrain_materiel` (+ `articleId` obligatoire)** | Industrie pour les matières (MA %, FDS) ; Exploitation pour le phyto (homologation, délais) et le parc (numéro, date et valeur d'achat, affectation, entretiens, CDC §6.7) | articleId + champs métier propres | articleId | — |
| Client | **`clients`** (nouvelle) | Commercial (CDC §3) ; fiche minimale au lot 1 | code, raison sociale, secteur, **NINEA**, registre de commerce, contacts, adresse (CDC §9.1), statut (PROSPECT, CLIENT, ANCIEN), actif | — | raison sociale dans bons, chantiers, futures factures |
| Fournisseur | **`fournisseurs`** (nouvelle) | **Finances** (CDC §3, §11.5) ; le Stock y ajoute délais constatés et évaluation annuelle (CDC §8.1) | code, raison sociale, NINEA, contacts, adresse, conditions de paiement (liste), actif | — | raison sociale dans bons d'entrée, mouvements chimie |
| Contrat client (minimal, Q11) | **`contrats_clients`** (nouvelle) | Commercial | numéro (pièce, type proposé `CTR`), clientId, sites couverts [{siteId, effectif prévu}], prestations (texte), montant mensuel HT (`@Sensible CA_SITES`), dateDebut, durée en mois, reconduction (oui / non), préavis en jours, clause de révision (texte), délai de paiement en jours, statut. Facultatifs : pénalité de non-couverture {montant ou %, délai}, volume d'heures mensuel (CDC §9.4, §6.1). Avenants : lot 5 | clientId, siteIds | numéro dans site et futures factures |
| Grille des salaires | `categories_professionnelles` (conservée, **versions datées**) | RH / Paie | code, libellé, niveau, échelon, salaire de base et primes **par date d'effet** (D7). Ne porte plus ni RIB, ni IPRES, ni CSS (Q15) | — | code et montants figés dans le bulletin (déjà le cas : `back:entities/rh/BulletinPaie.java`) |

Points particuliers :
- **Contrat** : c'est le **contrat client** (Q11, CDC §9.4 et §15 lot 1). Le contrat de travail existe déjà (`back:entities/rh/Contrat.java`) et n'est pas touché.
- **Site actif ⇒ contrat en cours** (CDC §6.1 « Lien contrat ») : la règle est écrite au lot 1 mais pilotée par un paramètre daté `exploitation.site.contratObligatoire`, à `false` jusqu'à la reprise des contrats (CDC §14 « Reprise des données » : liste des sites à valider). Sans cela, la mise en service désactiverait tous les sites existants, qui n'ont pas de contrat. La désactivation automatique en fin de contrat vient au lot 5.
- **Mode de paiement, IPRES, CSS** (Q15) : aujourd'hui `numeroIpres`, `numeroCss`, `rib`, `banque` sont portés par la grille et recopiés dans le bulletin depuis elle (`CalculPaieService.orchestrer`). Cible : mode et compte de paiement, n° IPRES du salarié sur l'employé ; numéros IPRES et CSS **de l'employeur** en paramètres d'entreprise (CDC §5.7 « Identité »). Un changement de compte de paiement est une demande à la RH avec une pièce jointe (CDC §13.2), donc au lot 6 pour Mon espace ; au lot 1, la RH le saisit, avec journal d'audit.
- **Solde chimie** : `MatierePremiere.quantiteEnStock` reste provisoirement dans son module. C'est une dette nommée, levée au lot 3 (stock) ou 8 (Industrie), puisque le CDC §8 veut « un seul stock », dont le magasin de l'Industrie est une vue. Le lot 1 n'unifie que le **catalogue**.
- **Magasins** (Q5) : le lot 1 crée le référentiel et deux fiches de départ, `MAG-CENTRAL` et `MAG-ATELIER`. Les soldes de `StockParSite` restent indexés par site au lot 1 ; leur bascule sur `magasinId` (le seau `siteId = null` de `StockEntrepotUniqueMigrationRunner` devient `MAG-CENTRAL`) et les bons de transfert entre magasins viennent au lot 3, qui touche ces services.
- **Matériel** (Q12) : famille d'articles. Le catalogue dit *ce que c'est* (« monobrosse 17 pouces »), le parc `terrain_materiel` suit *chaque unité* (numéro, affectation, entretiens) et porte toujours un `articleId`. Le lien avec les immobilisations (CDC §6.7) vient au lot 4/7.
- **Lecture « selon les droits »** : chaque référentiel expose `GET /api/referentiels/<x>` (recherche, sélecteur). Les champs sont masqués par D9. Le niveau LECTURE des référentiels est accordé par défaut à tout profil non agent : le CDC §13 donne à chaque responsable de module la « lecture des référentiels des autres modules ».

**Écarté.** Un « méga-référentiel » générique de tiers (client et fournisseur fusionnés) : séduisant pour la comptabilité, mais le CDC §3 leur donne deux propriétaires différents (Commercial et Finances). À revoir au lot 4 ; laisser chaque module garder sa liste avec une synchronisation : c'est la situation actuelle ; un niveau « agence » au-dessus du site : le CDC n'en a pas (Q6).

**Conséquences.** Les sélecteurs du front pointent vers les routes de référentiel. Les écritures de fiches passent par leur module propriétaire. Il faut 5 migrations de rapprochement (§3).

### D9 — Comptes, profils et droits à 5 niveaux

**Contexte.** Deux modèles : `login` (`back:entities/User.java`, seul porteur de SUPERADMIN) et `utilisateur` (`back:entities/Utilisateur.java`), sans lien vers le dossier employé. Le rapprochement se fait par e-mail non unique (`back:services/rh/CongeIdentiteService.java`) ; droits en booléens par sous-rubrique (`back:entities/GestionModules/ModulesAutorises.java` + `SousModules/{Rh,Stock,Terrain,ProductionChimie}.java`), contrôlés depuis le lot 0 par `back:security/Droits.java` et `DroitsExpr.java` ; contrôles de rôle en dur dans les services (`HabilitationStock`, `CongeIdentiteService`…) ; masquage par DTO réduit (`DossierEmployeResumeDto`, lot 0 C4) ; claim JWT `modules` (`back:security/JwtUtil.java`), enrichi à la connexion par `back:services/rh/CongeModuleEnricher.java`.

**Décision.**
- **`comptes`**, collection unique qui remplace `login` et `utilisateur` :
  - `{id, employeId (obligatoire sauf type TECHNIQUE et EXTERNE), type (INTERNE, AGENT, EXTERNE, TECHNIQUE), modeConnexion (MOT_DE_PASSE, CODE_TELEPHONE), identifiants: {email?, telephone?}, motDePasseHash?, codeConnexionHash? et appareil? (D11), profilIds[], perimetre: {siteIds[], magasinIds[]}, doubleAuthentification (booléen, dérivé des profils), actif, motifDesactivation, dateExpiration (accès externe daté, obligatoire pour EXTERNE), mustChangePassword, echecs, verrouilleJusqua, derniereConnexion, version}` ;
  - unicité de l'e-mail et du téléphone (index unique sparse) ;
  - « un compte part toujours d'une fiche du Personnel » (CDC §13.1) : un compte INTERNE ou AGENT se crée **depuis** le dossier employé, jamais par ressaisie de l'identité.
- **Création automatique du compte agent** : consommateur de `EmployeCree` quand le dossier a un téléphone et un poste d'agent (CDC §13.1, §13.3 « Agent »), voir D11.
- **Désactivation automatique à la sortie**, le jour même : consommateur de l'événement `EmployeSorti` (F010, CDC §13.1).
- **Périmètre** (CDC §13.1 « périmètre (sites, dépôt) », §13 « Superviseur terrain : ses sites et ses agents ») : vide = pas de restriction. `@droits.dansPerimetre(siteId)` est livré au lot 1 et appliqué aux référentiels site et employé (un superviseur ne voit que ses sites et les agents qui y sont affectés). Les modules l'appliquent à leurs pièces dans leur lot.
- **`profils`** : `{code, libelle, nature (METIER, TECHNIQUE), droits: {"<module>.<sousRubrique>": NIVEAU}, droitsSensibles: [..], doubleAuthentification, actif}`.
  - **Niveaux ordonnés** (CDC §13.1) : `AUCUN < LECTURE < SAISIE < VALIDATION < TOUT`. `TOUT` ajoute l'annulation de pièces validées et le paramétrage de la sous-rubrique. Une sous-rubrique à `AUCUN` disparaît du menu, et ses données n'apparaissent dans aucun tableau de bord de l'utilisateur.
  - **Droits sensibles** (Q14, CDC §13.1), cumulatifs, indépendants des niveaux, **jamais accordés par défaut** : `SALAIRES` (salaires nominatifs), `CA_SITES` (CA et rentabilité des sites), `PRIX_ACHAT`, `EXPORTER`, `VALIDER_EN_DERNIER`. La matrice de départ du CDC §13.1 sert de seed aux profils.
  - **Données de santé** (maladie, visite médicale, accidents) : règle fixe, hors droits cochables : visibles par la RH (niveau ≥ LECTURE sur `rh.absences`) et par la personne concernée ; les autres voient seulement « absent » (CDC §14). Annotation `@Sensible(SANTE)` traitée par le même mécanisme de masquage.
  - Un compte cumule ses profils au maximum par sous-rubrique.
- **Profils de départ** (seed, modifiables ensuite) : ceux du CDC §13 (Direction Générale, Responsable de module, Superviseur terrain, Agent, Chimiste, Magasinier, Commercial, Caissier, Comptable, Expert-comptable ou auditeur), plus `ADMIN`. Deux profils sont structurants :
  - **`DIRECTION`** (nature METIER) : LECTURE sur toutes les sous-rubriques, les 5 droits sensibles, dont `VALIDER_EN_DERNIER`, et double authentification. Elle valide par les étapes « Direction » des circuits (D10), pas par un niveau `TOUT` : le CDC §13 dit « Tout, en lecture. Validation finale ».
  - **`ADMIN`** (nature TECHNIQUE) : `TOUT` sur `parametres.*` (utilisateurs, profils et droits, circuits, seuils et listes, imports et modèles, journal des accès, CDC §13.4) et sur le paramétrage d'entreprise (CDC §5.7), `AUCUN` sur tout le reste, aucun droit sensible. Il n'est jamais valideur d'une pièce métier : un circuit refuse une étape qui désigne un profil TECHNIQUE.
  - **Garde-fous** (testés) : un compte ne modifie jamais ses propres profils, droits ou périmètre ; seul un compte DIRECTION peut attribuer le profil DIRECTION ou un droit sensible ; chaque changement de droit va au journal des accès (CDC §13.1).
- **Catalogue des sous-rubriques** en code (`CatalogueDroits`), généré à partir des sous-modules actuels (19 RH, 27 Stock, 9 Terrain, 7 Chimie) plus `parametres.*` et `referentiels.*`.
- **API de contrôle** (évolution de `Droits`) :
  - `@droits.peut('stock.bonsSortie', 'SAISIE')` ;
  - la forme du lot 0 `@droits.peut('stock.bonsSortie')` est conservée et vaut `LECTURE` pour les GET, ce qui rend la migration progressive ;
  - `@droits.sensible('SALAIRES')` ;
  - `estSuperAdmin()` disparaît : les routes de gestion des comptes passent à `@droits.peut('parametres.utilisateurs', 'SAISIE')` (profil ADMIN), les validations finales à l'étape Direction du circuit.
  - Les droits restent **relus en base** : cache de 30 s par compte, invalidé à la modification du compte ou du profil.
- **Masquage dans les DTO** :
  - annotation `@Sensible(DroitSensible.SALAIRES)` sur les champs des DTO de sortie ;
  - un `BeanSerializerModifier` Jackson remplace la valeur par `null` et ajoute `"_masques": ["salaireBase", ...]` si le compte n'a pas le droit ;
  - le même mécanisme sert aux exports (D13) et au journal d'audit (D6) ;
  - il remplace à terme `DossierEmployeResumeDto` (gardé tant que le front l'utilise).
  - Test : un DTO annoté sérialisé avec et sans le droit.
- **Contrôles de rôle des services** : `HabilitationStock`, `CongeIdentiteService` et `ParametresCongesService` sont réécrits en droits de niveau `VALIDATION` / `TOUT`, ou portés par le moteur de circuits (D10). Chaque réécriture vient avec son test SecuriteIT.
- **Migration** :
  1. chaque `utilisateur` devient un `comptes` (même id), relié au dossier par e-mail **exact et unique** ; sinon il part au rapport (§3.1) ;
  2. chaque combinaison distincte de drapeaux devient un profil `MIGRE-<n>` : drapeau vrai donne `SAISIE`, sauf les sous-rubriques de lecture pure (tableaux de bord, analyses), qui donnent `LECTURE` ;
  3. rôles : `CONTROLEUR_STOCK` → `VALIDATION` sur `stock.bonsEntree`/`bonsSortie`/`inventaires` ; `RH` → `TOUT` sur `rh.*` ; `SUPERADMIN` (`login`) → profil **transitoire** `MIGRE-SUPERADMIN` (`TOUT` partout + tous les droits sensibles), qui reproduit exactement ses droits d'aujourd'hui. À la réponse de Q1, ce compte reçoit `DIRECTION`, `ADMIN` ou les deux, et le profil transitoire est désactivé (opération tracée, pas une migration) ;
  4. test `MigrationDroitsIT` : pour chaque compte d'un jeu représentatif et chaque route de `DroitsModulesSecuriteIT`, la décision avant et après migration est **identique**.
- **JWT** : il ne porte plus `modules`, seulement `sub`, `type` et `employeId`. Le front lit `GET /api/moi/droits`, utilisé par la sidebar et `module.guard.ts`.
- **F018** : déconnexion après inactivité (paramètre, 30 min proposées), jeton de rafraîchissement en cookie HttpOnly (rotation, révocation à la désactivation), durée du jeton d'accès ramenée à 15 min.
- **Double authentification** (Q14, CDC §14 : « pour la Direction et les Finances ») : exigée pour tout compte dont un profil porte `doubleAuthentification` (seed : DIRECTION, Responsable Finances, Comptable, Caissier). Après le mot de passe, un code à usage unique de 6 chiffres part par SMS au téléphone du compte (5 min de validité, 3 essais, puis blocage D6) ; le jeton n'est émis qu'après ce code. Le modèle et l'étape de connexion sont posés en 1g ; l'envoi passe par `FournisseurSms`, donc la fonction est livrée en **1k**. En prod, elle n'est activable qu'avec un vrai fournisseur (Q3).

**Écarté.** RBAC Spring par `hasAuthority` sur des chaînes : les niveaux ordonnés et le masquage ne s'y expriment pas ; garder les deux collections de comptes : c'est la source de la duplication du mot de passe (audit §5.6).

**Conséquences.** L'écran `front:adminPage/gestion-privilege/` est réécrit en deux écrans, Profils (matrice profil × rubrique avec « Détail n/N » et aperçu du menu de chaque profil, CDC §13.1) et Comptes ; les 49 specs front cassés du lot 0 sur ces écrans (lot 0 §1) seront remplacés, pas réparés.

### D10 — Moteur de circuits de validation

**Contexte.** Les circuits sont codés écran par écran (F005) :
- congés Supérieur → RH → DG dans `back:Enum/rh/NiveauValidationConge.java` et `back:services/rh/CongeWorkflowService.java` ;
- bons BROUILLON → SOUMIS → VALIDE, validés par le seul SUPERADMIN (`back:services/stockv2/HabilitationStock.java`).

Il n'y a ni seuil ni délégation.

**Décision.**
Le CDC §13 « Circuits de validation » est la **référence unique** des circuits ; les sections des modules y renvoient (CDC §8.10).

- **`circuits_validation`** :
  - `{typePiece, code, dateEffet, conditions: {sousType? (ex. type de bon : ACHAT, PRODUCTION, RETOUR_SITE, LIVRAISON_SITE, DOTATION, CASSE…), siteIds?, profilBeneficiaire?}, etapes: [{ordre, libelle, valideur: {genre: PROFIL | SUPERIEUR_HIERARCHIQUE | RESPONSABLE_SITE | DEMANDEUR | COMPTE, ref}, delaiHeures, escaladeVers?}], direction: {mode: TOUJOURS | AU_DESSUS_DU_SEUIL | JAMAIS, seuilMontant?, casAnormaux[]?}, actif}` ;
  - l'étape **Direction** n'est pas une étape saisie : le moteur l'ajoute en dernier selon `direction.mode`. C'est la case « Direction en dernier » du CDC §13.1, et le réglage à trois positions du CDC §8.10 ;
  - `AU_DESSUS_DU_SEUIL` fait monter la pièce si son montant dépasse `seuilMontant` (paramètre daté, D7) **ou** si elle présente un cas anormal déclaré (CDC §8.10 : dépassement de plus de 20 % de la moyenne d'un site, nouveau fournisseur, importation, casse, perte, don ; les détecteurs de ces cas viennent avec leur lot) ;
  - versionné par date d'effet, comme D7 : une pièce garde le circuit en vigueur **à sa soumission**.
- **Choix du circuit** : le plus spécifique dont les conditions sont remplies. Si aucun circuit ne convient, la soumission est refusée (422 `CIRCUIT_ABSENT`).
- **Pièce née d'une pièce validée** : elle ne repasse pas en validation (CDC §13 : bon de commande issu d'une demande de dépense validée, bon de caisse…). Le moteur la valide d'office si son `pieceOrigineId` désigne une pièce `VALIDEE` d'un type déclaré parent ; la décision est tracée « validée par la pièce n° … ».
- **Invariants vérifiés par le moteur, et testés** :
  1. **Personne ne valide sa propre demande** (CDC §13), ni directement ni par délégation : l'auteur et le bénéficiaire (congé, avance) sont exclus de toutes les étapes ;
  2. **Direction en dernier** : l'étape Direction est toujours la dernière, et ses valideurs doivent avoir le droit sensible `VALIDER_EN_DERNIER` ;
  3. un même compte ne valide pas deux étapes d'une même pièce ;
  4. un profil de nature TECHNIQUE (ADMIN) n'est jamais valideur ;
  5. refus = motif obligatoire (au moins 10 caractères, comme `MOTIF_REFUS_MIN_LENGTH` de `CongeWorkflowService`).
- **Réglage de départ** (Q13), seedé depuis le tableau du CDC §13 : Direction `TOUJOURS` sur toutes les pièces, sauf les congés (`JAMAIS`). Seuil « montant au-dessus duquel la Direction valide » : 0 F (CDC §13.4). Les seuils réels des pièces financières (bon de caisse, demande de dépense, chèque) restent à fixer par la Direction (CDC §16) et se règlent au lot 4, sans développement.
- **`delegations`** : `{delegantCompteId, delegataireCompteId, typesPiece[], dateDebut, dateFin, motif, creePar}`. Chevauchements autorisés, délégation de délégation interdite. Tout est journalisé (D6).
- **`instances_validation`** : `{pieceType, pieceId, circuitId, circuitVersion, etapeCourante, statut, decisions: [{etape, compteId, pourLeCompteDe? (délégation), decision, motif, le}]}`.
- **API commune** :
  - `GET /api/validations/a-traiter` (vue agrégée, base de F030 au lot 11) ;
  - `POST /api/validations/{instanceId}/decider` ;
  - les routes existantes `/valider` et `/refuser` des modules deviennent des façades.
- **Effets** : la dernière décision favorable passe la pièce en `VALIDEE`, attribue le numéro (D4) et publie `PieceValidee` (D5), dans une seule transaction. Les notifications aux valideurs passent par D14.
- **Premier cas porté** : les congés.
  - Circuit de départ selon le CDC (§13, §5.3) : Superviseur → RH, Direction `JAMAIS` ; un second circuit, de condition `profilBeneficiaire = responsable de module`, ajoute la Direction (« la Direction valide les congés des responsables ») ;
  - **écart avec l'existant** : `NiveauValidationConge` impose aujourd'hui Supérieur → RH → DG pour tous. Les tests `CongeWorkflowService*` existants servent d'oracle de non-régression **du moteur**, joués avec un circuit Supérieur → RH → Direction configuré dans le test ; le circuit seedé, lui, suit le CDC. Ce changement de comportement est annoncé à la RH ;
  - les contrats d'API de `TempsPresencesCongeController` sont conservés (`/demandes/a-valider`, `/valider`, `/refuser`).
- **Deuxième cas** : les bons d'entrée et de sortie, avec les circuits du CDC §8.10 : par exemple entrée d'achat = magasinier → Direction, sortie casse / perte / don = magasinier (photo et motif) → Direction. Au lot 1, le type de bon existant (`TypeSortie`, catégories d'entrée) sert de `sousType` ; les étapes qui supposent des pièces du lot 3 (besoins du site relevés par le superviseur, préparation) sont seedées mais n'agissent qu'au lot 3. **Écart avec l'existant** : un bon validé par le magasinier attend désormais la Direction avant de devenir `EFFECTIF`. Les routes `/valider` et `/refuser` restent, les transitions invalides restent en **409** (contrat figé, CLAUDE.md Stock 7.4), et la réponse indique l'étape en cours.

**Écarté.** Moteur BPMN (Flowable, Camunda) : poids, base relationnelle requise, et circuits simples ; un enum de niveaux par module : la situation actuelle.

**Conséquences.** Les étapes de départ viennent du CDC ; la Direction les ajuste ensuite dans Paramètres et accès › Circuits de validation, sans développement. Le lot 1 livre le moteur et deux types de pièce. Les autres pièces (paie, caisse, achats) s'y branchent dans leur lot.

### D11 — Connexion agent : téléphone, code choisi, appareil lié

**Contexte.** La connexion actuelle se fait par e-mail et mot de passe (`back:controllers/LoginController.java`). Aucun SMS (F013, audit §5). Le code à 4 chiffres existant (`DossierEmploye.agentId`) est le **code de pointage**, saisi sur un kiosque partagé et visible des collègues : il ne doit pas devenir un secret de connexion.

Le CDC tranche le mode de connexion (Q9) : « l'agent s'y connecte avec son numéro de téléphone et un code à 4 chiffres choisi à la première connexion (le SMS porte un code d'activation) ; le même compte sert au pointage QR » (§13.2) ; « un compte agent est lié à un seul téléphone » (§6.3 « Anti-fraude ») ; passerelle SMS « d'un opérateur sénégalais », WhatsApp en option (§14).

**Décision.**
- Interface **`FournisseurSms`** (`ResultatEnvoi envoyer(String numeroE164, String message, String reference)`) :
  - implémentation **`FournisseurSmsFactice`** active par défaut (`sms.fournisseur=factice`) : elle écrit dans `sms_envoyes` (numéro, message, date) et **n'envoie rien**. Un écran de développement, profil non-prod et droit `parametres.journaux`, affiche les SMS « envoyés » ;
  - en prod, le démarrage est refusé si `sms.fournisseur=factice` sans drapeau explicite, sur le modèle de `SecretsRequisVerificateur` ;
  - la passerelle de l'opérateur viendra dans une classe à part, choisie par propriété (Q3). WhatsApp serait une seconde implémentation de la même interface.
- **Envoi** toujours via notifications et outbox (D14), jamais dans la transaction. Nouvel essai géré par le dispatcher. Le texte d'un SMS ne contient jamais le code de connexion choisi, seulement un code d'activation.
- **Cycle de vie du compte agent** :
  1. **Création** : la RH crée le dossier, ou l'importe (D13). L'événement `EmployeCree` crée le compte `AGENT` (CDC §13.1 : « son compte Agent est créé tout seul »), état `EN_ATTENTE_ACTIVATION`, profil `AGENT` (seul droit `monEspace.*` en LECTURE, données de l'agent seulement).
  2. **Code d'activation** : 6 chiffres tirés par `SecureRandom`, **haché**, à usage unique, valable 72 h (paramètre daté), envoyé par SMS. Un nouvel envoi invalide le précédent. Six chiffres et non quatre : ce code circule par SMS et n'est pas mémorisé, il peut donc être plus long.
  3. **Première connexion** `POST /api/agent/activation {telephone, codeActivation, codeChoisi, cleAppareil}` :
     - l'agent **choisit** son code à 4 chiffres. Sont refusés : les codes triviaux (4 chiffres identiques, suites croissantes ou décroissantes), le code égal à son `agentId` de kiosque, et le code égal aux 4 derniers chiffres de son téléphone ;
     - le code choisi est haché (BCrypt) dans `codeConnexionHash` ;
     - **liaison de l'appareil** : la PWA génère au navigateur une paire de clés ECDSA P-256 **non extractible** (WebCrypto, conservée dans IndexedDB) et envoie la clé publique. Le compte enregistre `appareil {clePublique, empreinte (sha256 de la clé), libelle (user-agent abrégé), lieLe}` ;
     - le compte passe `ACTIF`.
- **Connexion** `POST /api/agent/connexion {telephone, code, defi, signature}` :
  - `defi` est un nonce à usage unique obtenu par `POST /api/agent/defi {telephone}` (valable 2 min). La PWA le signe avec la clé de l'appareil : sans **ce téléphone-là**, le bon code ne suffit pas ;
  - numéro normalisé E.164 (`+221` par défaut, paramètre) ;
  - **3 échecs ⇒ blocage 15 min** (D6, CDC §13.1 et §14), que l'échec porte sur le code ou sur la signature. Une signature d'un autre appareil va au journal des accès avec le motif `APPAREIL_INCONNU` ;
  - limite de débit par IP et par téléphone, sur le principe de `back:services/pointage/PointageLimiteurDebit.java`.
- **Code oublié** `POST /api/agent/code-oublie {telephone}` : nouveau code d'activation par SMS, réponse identique que le numéro existe ou non (comme `ResetPasswordService` au lot 0). La réactivation exige la signature de l'appareil déjà lié : un code oublié ne change pas de téléphone.
- **Changement de téléphone**, ou données du navigateur effacées (la clé est perdue) : action de la RH ou du superviseur, `POST /api/comptes/{id}/reinitialiser-appareil {motif}`. Elle délie l'appareil, remet le compte en `EN_ATTENTE_ACTIVATION` et renvoie un code d'activation **au numéro du dossier**. Journalisée.
- **Jeton agent** : `type=AGENT`, plus l'empreinte de l'appareil. Toute route hors `/api/moi/**` refuse ce type (règle globale dans `Droits`, plus un test qui parcourt les routes comme `RoutesPubliquesIT`). La durée de session des agents est un paramètre daté, distinct de celle des comptes de bureau (F018).
- Routes publiques nouvelles : `POST /api/agent/defi`, `/activation`, `/connexion`, `/code-oublie`. Elles sont ajoutées à `SecurityConfig` **et** à la liste blanche de `RoutesPubliquesIT`.
- **Anticipation du lot 2, sans implémentation au lot 1** :
  - le pointage QR sera une route authentifiée de Mon espace (`POST /api/moi/pointages`), qui exigera le jeton AGENT **et** une signature, par la clé de l'appareil, du contenu du scan (jeton du QR de pointage du local, heure, position GPS). C'est la règle « un compte agent est lié à un seul téléphone » du CDC §6.3. Une signature se calcule sans réseau, ce qui reste compatible avec le pointage hors ligne « différé » du même §6.3 ;
  - le PIN de kiosque disparaîtra alors : `DossierEmploye.agentId`, la route publique `POST /api/pointages` et `PointageLimiteurDebit` seront retirés au lot 2, avec la décision « quelle source fait foi » (§2.2). Au lot 1, **rien ne s'authentifie par `agentId`** et le kiosque continue de fonctionner tel quel ;
  - le pointage par le chef d'équipe pour les agents sans smartphone (CDC §6.3) sera un droit du compte superviseur, borné par son périmètre (D9), au lot 2.

**Écarté.** Code tiré par le serveur : le CDC veut un code choisi ; réutiliser `agentId` : secret partagé de fait ; secret d'appareil stocké en clair dans le navigateur : copiable vers un autre téléphone ; empreinte du navigateur (*fingerprinting*) : instable sur Android d'entrée de gamme, et donnée personnelle de plus ; lien magique par SMS : plus coûteux en SMS, inutilisable sur un téléphone partagé ; OTP à chaque connexion : un SMS par connexion, coût récurrent.

**Conséquences.** 4 chiffres donnent 10 000 combinaisons : la sécurité repose sur le blocage après 3 échecs, la limite de débit **et** la possession du téléphone lié. Les trois sont testés. Un changement de téléphone coûte une action RH et un SMS. Mon espace ne montre que des données de l'agent lui-même (`employeId` du jeton).

### D12 — Fichiers et pièces jointes

**Contexte.** Les fichiers sont des `byte[]` dans 13 entités (audit §5.10 : `DossierEmploye.photo`, `Contrat.fichierContrat`, `DocumentEmploye.fichier`, `ProduitStock.photo` et `.ficheTechnique`, `MatierePremiere.ficheSecurite`, `SiteClient.cahierDesChargesFichier`…). Ils pèsent sur la limite de 16 Mo par document et sur chaque lecture de liste. Pas de métadonnées communes, pas de composant Angular partagé (F007).

**Décision.**
- Interface `StockageFichiers { String deposer(InputStream, Meta); InputStream lire(String); }`, avec l'implémentation **GridFS** (`GridFsTemplate`), dans la même base.
- Collection **`fichiers`** :
  - `{id, stockageRef, nomOriginal, mimeType, taille, sha256, proprietaire: {type, id}, categorie (liste : PHOTO, CONTRAT, JUSTIFICATIF, FDS…), creePar, creeLe, statut (ACTIF, REMPLACE, ARCHIVE), conserverJusqua}` ;
  - un fichier remplacé passe en `REMPLACE`, sans suppression ;
  - type MIME vérifié par signature (Apache Tika, **à confirmer** : nouvelle dépendance) plutôt que par l'extension.
- Routes communes :
  - `POST /api/fichiers` (multipart, propriétaire en paramètre) ;
  - `GET /api/fichiers/{id}` (flux) ;
  - `GET /api/fichiers?proprietaireType=&proprietaireId=`.
  - Le **droit** se déduit du propriétaire : registre `ControleAccesFichier` par type (ex. justificatif d'absence → `rh.absences` LECTURE). Chaque téléchargement sensible va au journal des accès.
- Les routes de flux existantes (`/{id}/photo`, `/{id}/fiche-technique`, `/{id}/fichier`…) restent en façade, avec le même contrat, et lisent désormais GridFS.
- Front : composant `app-pieces-jointes` (`[proprietaireType]`, `[proprietaireId]`, `[categories]`, `[lectureSeule]`). Il gère dépôt, liste, aperçu et téléchargement avec le jeton (blob, comme l'organigramme au lot 0).

**Écarté.** Stockage objet S3 (SeaweedFS, MinIO) maintenant : nouvelle infrastructure à opérer et à sauvegarder, sans accès au VPS. L'interface le permet plus tard ; disque local : pas sauvegardé avec la base, et lié au conteneur.

**Conséquences.** Les écritures GridFS se font **hors transaction**, d'où la séquence « déposer, puis enregistrer la métadonnée en transaction ». Un fichier orphelin (dépôt sans métadonnée) est nettoyé par un job hebdomadaire et journalisé ; la sauvegarde Mongo inclut les fichiers.

### D13 — Import et export génériques, écrans allégés

**Contexte.** Il existe 3 imports spécifiques : `DossierEmployeService.importBulk` (stratégies TOUT_OU_RIEN ou IMPORTER_LIGNES_VALIDES, `BulkInsertPartialFailureException`), `ProduitStockController /bulk` et `EmployeCompletController /import-excel` (legacy).

Il n'existe ni annulation d'import (F015), ni export Word, ni filtrage des exports par droits (F016). Le front utilise `xlsx` 0.18.5, vulnérable (`front:services/*-export.service.ts`, 12 fichiers). Le menu est en accordéon, sans panneau latéral (F017).

**Décision.**
- **Définition de champs unique** (`DefinitionObjet`, une par objet importable ou exportable : employé, site, article, client, fournisseur, contrat client, grille) :
  - champs : `{code, libelle, type, obligatoire, longueurMax, liste? (D7), reference? (référentiel), sensible? (D9), importable, exportable, aide}` ;
  - **source unique** de trois sorties :
    1. `GET /api/definitions/{objet}` pour les formulaires du front (validation et libellés) ;
    2. le **modèle `.xlsx`** généré par POI (`GET /api/imports/{objet}/modele`), avec en-têtes, listes déroulantes de validation et onglet d'aide ;
    3. le contrôle d'import.
- **Import**, en trois temps :
  1. `POST /api/imports/{objet}` (fichier) crée une pièce `IMPORT` en BROUILLON : analyse complète, erreurs par ligne et colonne, aperçu, rien d'écrit dans l'objet cible ;
  2. `POST /api/imports/{id}/confirmer` : **tout ou rien en une transaction** (D1), `importId` posé sur chaque document créé, pièce numérotée `IMP-<exercice>-<n>` ;
  3. `POST /api/imports/{id}/annuler {motif}`, possible pendant un délai paramétré (24 h). Il annule les objets créés, refusé si l'un d'eux est déjà référencé ailleurs (ex. agent déjà pointé). Tout est journalisé.
  - Taille maximale par import en paramètre, reprise de `rh.import.bulk.max-size: 1000`.
- **Exports serveur** `GET /api/exports/{objet}?format=xlsx|pdf|docx&<filtres>` :
  - Excel par POI, PDF par OpenPDF, **Word par POI XWPF** (même dépendance `poi-ooxml`) ;
  - ils passent par la **même sérialisation masquée** (D9) ;
  - ils exigent le droit sensible `EXPORT` en plus de la lecture ;
  - chaque export va au journal des accès (D6) avec ses filtres et le nombre de lignes.
- Les exports front `xlsx` sont remplacés progressivement. La dépendance `xlsx` est retirée à la fin de 1j, en ce qui concerne les référentiels ; les autres écrans suivent leur lot.
- **Écrans allégés (F017)** : composants Angular génériques :
`app-liste-referentiel` (tableau, filtres, export), `app-panneau-lateral` (création et édition à droite, bouton « + Nouveau »), `app-formulaire-dynamique` (piloté par `DefinitionObjet`), aide repliée, liens directs par ancre (`#referentiels/clients`). Ils sont appliqués au lot 1 aux 7 fiches minimales.

**Écarté.** Garder les exports côté client : le masquage serait contournable et les exports ne seraient pas journalisés ; bibliothèque de génération Word sur gabarit (docx4j) : inutile à ce stade. F044 (modèles de documents RH) est au lot 6.

**Conséquences.** F022 (imports variés : pointages, éléments variables, balance…) n'est couvert au lot 1 que pour les **référentiels**. Les autres objets s'y brancheront dans leur lot, par une simple `DefinitionObjet`.

### D14 — Notifications persistées et multicanal

**Contexte.** STOMP dans `back:services/rh/CongeNotificationService.java`, `stockv2/StockNotificationService.java` et `terrain/TerrainNotificationService.java` ; ce dernier cible un **identifiant** alors que le principal STOMP est l'**e-mail**, donc ses messages ciblés n'arrivent pas (lot 0 §8). E-mail synchrone (`back:services/EmailService.java`). Rien n'est persisté (F008), et le front écoute des topics que plus personne n'émet (`/topic/annulationRequests`, lot 0 §4.3).

**Décision.**
- **`notifications`** : `{id, destinataireCompteId, type (liste), titre, message, lien, objet: {type, id}, canaux: {APP, EMAIL, SMS} → {statut, tentatives, envoyeLe, erreur}, lue, lueLe, creeLe, evenementId}`.
- Production **uniquement par des consommateurs d'événements** (D5). Exemples : `PieceSoumise` → valideurs de l'étape ; `PieceValidee` / `Refusee` → auteur ; `CompteCree(AGENT)` → SMS d'activation.
- Canaux : **APP** (persistance, puis poussée STOMP `/user/queue/notifications`, principal = identifiant de compte, ce qui unifie e-mail et téléphone), **EMAIL** (`EmailService`), **SMS** (`FournisseurSms`, D11). Les canaux par type sont paramétrés (D7) ; les préférences individuelles sont hors lot 1.
- Routes : `GET /api/moi/notifications` (paginé, non lues), `POST /api/moi/notifications/{id}/lue`, `POST /api/moi/notifications/tout-lu`. Cloche dans le header du front.
- Les trois services STOMP existants deviennent des consommateurs. Les topics de diffusion (`/topic/alertes-terrain`…) restent pour les tableaux de bord. Les abonnements front morts sont retirés.

**Écarté.** Envoyer depuis les services métier : couplage, et pas de nouvel essai ; notification uniquement en mémoire : la situation actuelle.

**Conséquences.** Les e-mails deviennent asynchrones : la création d'un bon n'attend plus le SMTP (tolérance aux pannes exigée par CLAUDE.md, « E-mail à la création d'un bon »).

### D15 — Données personnelles et sauvegardes

**Contexte.** Aucune purge ni anonymisation (F019). Aucune sauvegarde dans les dépôts : seulement le volume Docker `mongo_data` (F020, `docker-compose.yml`). Mongo est sans authentification et le port 27018 publié (lot 0 §8). L'existence d'une sauvegarde hors dépôt sur le serveur n'est pas connue (audit §4.6 Q11).

**Décision.**
- **Catalogue de conservation** : paramètres datés (D7) `conservation.<categorie>`. Valeurs de départ tirées du CDC §14 « Conservation et archivage » :

  | Catégorie | Durée de départ | Source | Au terme |
  |---|---|---|---|
  | Pièces de gestion et écritures comptables | 10 ans après la clôture de l'exercice | CDC §14 (Acte uniforme OHADA relatif au droit comptable) | archive longue durée, jamais purgées avant |
  | Positions GPS et photos de pointage | 12 mois | CDC §14 (exemple déclaré à la CDP) | GPS tronqué, photo effacée |
  | Justificatifs médicaux | durée de l'absence + délai légal (**délai à fournir** par le conseil juridique) | CDC §14 | fichier effacé, absence conservée sans motif médical |
  | Sanctions | selon le Code du travail (**durée à fournir**) | CDC §14, §5.10 | anonymisation |
  | Ex-employés (identité, contrat, paie), candidats, journaux d'accès et d'audit | **à fixer** par la Direction (Q8, reste) | — | anonymisation |
  | SMS factices, événements traités, fichiers orphelins | durées techniques proposées (30 j, 10 ans, 7 j) | — | suppression (liste blanche D4) |

  Tant qu'une durée vaut « à fixer », le `PurgeService` ne touche pas à la catégorie. Les durées retenues sont celles **déclarées à la CDP** (loi 2008-12, CDC §14 « Données personnelles »), déclaration qui reste à la charge de la Direction.
- **`PurgeService`** `@Scheduled` mensuel :
  - il **anonymise** et ne supprime pas : les pièces restent, l'identité est remplacée par un pseudonyme stable, les coordonnées GPS sont tronquées, les fichiers d'identité sont archivés puis effacés de GridFS ;
  - il trace chaque passage dans `purges` (`{le, categorie, critere, nombre, idsAffectes (hachés)}`) et dans le journal d'audit ;
  - mode « à blanc » qui produit le rapport sans écrire, obligatoire avant le premier vrai passage ;
  - c'est la seule exception documentée à la règle 5 pour les fichiers.
- **Sauvegardes** (actions d'exploitation, scriptées dans le dépôt sous `ops/sauvegarde/` et mises en service par l'administrateur du serveur selon `docs/exploitation/sauvegarde.md` : pas d'accès au VPS, Q16) :
  - `mongodump --oplog --gzip` **quotidien**, chiffrement (`age` ou `gpg`, choix technique de 1l), copie **hors du serveur principal** demandée par le CDC, **non prévue** (Q16, 24/11 : aucun stockage hors serveur ; écart signalé comme risque), **conservée 30 jours** (CDC §14 « Sauvegarde ») ;
  - **test de restauration trimestriel** au minimum (CDC §14), scripté : restauration dans un conteneur jetable, puis contrôles de comptage et d'intégrité (`IndexesAttendusIT` en mode lecture), compte rendu horodaté ;
  - **archive longue durée**, distincte de la sauvegarde (CDC §14) : à la clôture de chaque exercice, un export chiffré et signé (sha256) des pièces et écritures de l'exercice, conservé 10 ans sur un support séparé, avec sa procédure de relecture ;
  - authentification Mongo activée et port non publié, en même temps que le replica set (1a).

**Écarté.** Suppression physique des ex-employés : elle casserait les pièces (bulletins, déclarations) à conserver légalement ; snapshot de disque du VPS seul : non testé, et pas hors site.

**Conséquences.** Sans accès au VPS (Q16), la partie sauvegarde est un livrable de scripts testés en local et de procédure (`docs/exploitation/sauvegarde.md`). Sa mise en service revient à l'administrateur du serveur, qui renvoie les résultats attendus (premier dump, premier test de restauration).

### D16 — Multi-entreprises

**Contexte.** Aucun `tenantId` (F021). Valeurs Cleanic en dur : e-mail HTML de `ResetPasswordService`, grille « Grille générique Cleanic » de `DataLoader`, CORS dans `back:security/OriginesAutorisees.java`, expéditeur de `EmailService`. Le CDC §14 exige des « données cloisonnées par entreprise, pour la commercialisation future de PointIC », et le CDC §5.7 un paramétrage par entreprise (identité, NINEA, numéros IPRES et CSS de l'employeur, organisation…) « de sorte qu'un nouveau client démarre sans développement ». La propriété du logiciel et le calendrier de revente restent en attente (Q4).

**Décision (coût minimal aujourd'hui, porte ouverte).**
- **Une base par entreprise, donc un déploiement par entreprise.** C'est le cloisonnement le plus fort demandé par le CDC §14. Aucun champ `entrepriseId` au lot 1.
- Toutes les valeurs propres à Cleanic deviennent des **paramètres** (D7 : raison sociale, logo, expéditeur, préfixe téléphonique, en-têtes de PDF) ou des variables d'environnement (origines CORS, URL publique).
- Règle de conception, vérifiée en revue : aucune requête ne suppose qu'il n'existe qu'**une** entreprise dans le monde. Par exemple, pas de singleton nommé « Cleanic ». Les compteurs, les paramètres et les profils sont déjà « par base ».
- Si la Direction choisit plus tard une base partagée : une ChangeUnit ajoute `entrepriseId` partout (valeur unique pour les données existantes), plus un filtre global par aspect sur `MongoTemplate`. Le coût est connu et différé.

**Écarté.** `entrepriseId` dès maintenant : il faudrait le porter dans chaque requête, chaque index et chaque test, sans client second identifié ; collections préfixées par entreprise : opérationnellement pire que des bases séparées.

**Conséquences.** Le coût d'hébergement est proportionnel au nombre d'entreprises. Il est acceptable tant qu'il n'y en a qu'une ou deux.

---

## 2. Modèle de données cible du socle

### 2.1 Collections nouvelles ou transformées

| Collection | Champs clés | Références | Index |
|---|---|---|---|
| `comptes` | type, modeConnexion, identifiants.email, identifiants.telephone, employeId, profilIds, perimetre, appareil.empreinte, statut d'activation, actif, dateExpiration, verrouilleJusqua, version | employeId → `dossiers_employes`, profilIds → `profils`, perimetre → `sites_clients`, `magasins` | email unique sparse ; telephone unique sparse ; employeId unique sparse ; appareil.empreinte unique sparse ; (actif, type) |
| `profils` | code, libellé, nature (METIER, TECHNIQUE), droits{}, droitsSensibles[], doubleAuthentification | — | code unique |
| `codes_usage_unique` | compteId, usage (ACTIVATION, DEUXIEME_FACTEUR, DEFI), hash, expireLe, essais | compte | (compteId, usage) ; expireLe (TTL) |
| `magasins` | code, libellé, type, siteId, actif, version | site | code unique ; siteId sparse |
| `compteurs_pieces` | _id `TYPE-EXERCICE`, compteur | — | _id |
| `registre_pieces` | type, numero, exercice, statut, siteId, montant, amont[], aval[] | pièce (type + id), sites | (type, numero) unique sparse ; (type, pieceId) unique ; siteId ; statut |
| `evenements_domaine` | type, agregatType, agregatId, statut, prochainEssai, survenuLe | agrégat | (statut, prochainEssai) ; (agregatType, agregatId, survenuLe) ; survenuLe |
| `evenements_consommes` | consommateur, evenementId, le | événement | (consommateur, evenementId) unique |
| `journal_audit` | le, compteId, action, objetType, objetId, changements[] | compte, objet | (objetType, objetId, le) ; (compteId, le) ; le |
| `journal_acces` | le, identifiantSaisi, compteId, resultat, ip | compte | (compteId, le) ; (ip, le) ; le |
| `parametres` | cle, valeur, dateEffet, saisiPar, motif, annulee | — | (cle, dateEffet) unique |
| `listes_choix` | liste, code, libellé, ordre, actif, dateDebut, dateFin | — | (liste, code) unique ; (liste, actif, ordre) |
| `circuits_validation` | typePiece, code, dateEffet, conditions, etapes[] | profils, comptes | (typePiece, actif, dateEffet) |
| `delegations` | delegant, delegataire, typesPiece, dateDebut, dateFin | comptes | (delegataireCompteId, dateDebut, dateFin) ; delegantCompteId |
| `instances_validation` | pieceType, pieceId, etapeCourante, statut, decisions[] | pièce, circuit | (pieceType, pieceId) unique ; (statut, etapeCourante) |
| `notifications` | destinataireCompteId, type, lue, creeLe, canaux{} | compte, objet, événement | (destinataireCompteId, lue, creeLe) ; evenementId |
| `fichiers` (+ `fs.files`, `fs.chunks` GridFS) | proprietaire.type/id, categorie, sha256, statut | objet propriétaire | (proprietaire.type, proprietaire.id, statut) ; sha256 |
| `imports` (pièce IMPORT) | objet, statut, numero, lignes, erreurs, importLe, annulableJusqua | objets créés (par `importId`) | (objet, statut) ; numero unique sparse |
| `articles` (ex `stockv2_produits`) | code, libellé, famille, nature, unité, categorieId, fournisseurPrincipalId, actif, version | catégorie, fournisseur | code unique ; categorieId ; (famille, actif) |
| `clients` | code, raisonSociale, ninea, statut, actif, version | — | code unique ; ninea unique sparse ; raisonSociale (texte) |
| `fournisseurs` | code, raisonSociale, ninea, actif, version | — | code unique ; ninea unique sparse ; raisonSociale (texte) |
| `contrats_clients` | numero, clientId, sites[{siteId, effectifPrevu}], montantMensuelHt, dateDebut, dureeMois, reconduction, preavisJours, delaiPaiementJours, statut, penalite?, volumeHeures? | client, sites | numero unique ; clientId ; sites.siteId |
| `migration_rapports` | migrationId, executeLe, comptages, rejets[] | — | (migrationId, executeLe) |
| `sms_envoyes` (factice) | numero, message, le, reference | — | le (TTL selon paramètre) |
| `purges` | le, categorie, nombre | — | le |

Champs ajoutés aux collections conservées :
- `dossiers_employes` : `telephone` normalisé (index unique sparse), `affectations[].siteId`, `modePaiement {mode, compte}`, `numeroIpres`, `personneAPrevenir`, `version`, `idsHistoriques` (ids des fiches `employes` / `employes_complet` fusionnées) ;
- `sites_clients` : `clientId` (obligatoire), `contratClientId`, `superviseurId`, `version` ; champs réservés au lot 2 `qrPointage`, `qrAppreciation` ; création de l'index unique `code`. **Pas** de champ `type` (Q6) ;
- `categories_professionnelles` : versions datées (D7) ; champs `numeroIpres`, `numeroCss`, `rib`, `banque` retirés après la migration §3.5 ;
- `production_chimie_matieres_premieres` et `produits_phyto` : `articleId` ; `terrain_materiel` : `articleId` obligatoire ;
- `stockv2_bons_entree`, `production_chimie_mouvements_stock` : `fournisseurId` + instantané ;
- `stockv2_bons_sortie` (`destinataire.clientId`), `stockv2_chantiers` : `clientId` + instantané ;
- toutes les pièces : bloc `PieceBase` (D4).

### 2.2 Devenir des collections actuelles du socle

| Collection actuelle | Preuve | Devenir |
|---|---|---|
| `login` | `back:entities/User.java` | **Fusionnée** dans `comptes`, puis archivée (`archive_login`, lecture seule, supprimable après une durée fixée par la Direction) |
| `utilisateur` | `back:entities/Utilisateur.java` | **Fusionnée** dans `comptes` (mêmes ids), puis archivée |
| `utilisateur.modulesAutorises` | `back:entities/GestionModules/` | **Converti** en `profils` (D9) |
| `employes` | `back:entities/Employe.java` | **Fusionnée** dans `dossiers_employes` (§3.1), puis archivée ; synchronisation `EmployeCompletService` retirée |
| `employes_complet` | `back:entities/EmployeComplet.java` | **Fusionnée** dans `dossiers_employes`, puis archivée ; module `/api/employe-complet` supprimé (import remplacé par D13), ce qui lève les 2 bloqueurs listés dans CLAUDE.md |
| `dossiers_employes` | `back:entities/rh/DossierEmploye.java` | **Conservée**, référentiel employé |
| `sites_clients` | `back:entities/terrain/SiteClient.java` | **Conservée**, référentiel site ; `cahierDesChargesFichier` vers GridFS |
| `produits` (stock historique) | `back:entities/stock/Produit.java` | **Rapprochée** de `articles` (§3.3), puis archivée ; `ProduitService` et son dépôt supprimés (lot 0 §8) |
| `stockv2_produits` | `back:entities/stockv2/ProduitStock.java` | **Renommée** `articles` (ids conservés) ; photo et fiche technique vers GridFS |
| `production_chimie_matieres_premieres` | `back:entities/productionchimie/MatierePremiere.java` | **Conservée en extension** (+ `articleId`) ; code, nom, unité, fournisseur lus depuis l'article ; `quantiteEnStock` provisoire |
| `produits_phyto` | `back:entities/terrain/ProduitPhytosanitaire.java` | **Conservée en extension** (+ `articleId`) |
| `terrain_materiel` | `back:entities/terrain/Materiel.java` | **Conservée** comme parc (unités numérotées), **+ `articleId` obligatoire** vers un article de famille MATERIEL (Q12, §3.3) |
| `categories_professionnelles` | `back:entities/rh/CategorieProfessionnelle.java` | **Conservée**, versions datées ; RIB et banque déplacés vers le mode de paiement de l'employé, numéros IPRES et CSS de l'employeur vers les paramètres d'entreprise (Q15, §3.5) |
| `contrats` (travail) | `back:entities/rh/Contrat.java` | **Conservée** ; fichier vers GridFS |
| `stockv2_compteurs`, `production_chimie_compteurs_of`, `production_chimie_compteurs_lots`, `compteurs_terrain` | `CompteurStockService`, `CompteurOfService`, `CompteurLotService`, `CompteurTerrainService` | **Remplacées** par `compteurs_pieces` pour les nouveaux numéros ; anciennes collections conservées en lecture. Le format des lots (`AAAAMMJJ-NNN`) reste au lot 8 (F069). |
| `stockv2_suppressions` | `back:entities/stockv2/SuppressionStockLog.java` | **Conservée en lecture** ; plus alimentée (annulation tracée, D4) |
| `parametres_paie`, `parametres_conges`, `terrain_parametres_escalade`, `production_chimie_parametres`, `stockv2_parametrage_valorisation` | entités citées en D7 | **Migrées** vers `parametres` (versions datées), puis archivées |
| `ResetPasswordToken` | `back:entities/ResetPasswordToken.java` | **Conservée** (éphémère, en liste blanche de suppression) ; référence `comptes` |
| `pointage_request` | `back:entities/PointageRequest.java` | **Archivée** : aucune lecture hors de son dépôt (audit Exploitation §1) |
| `pointages` / `terrain_pointages` | audit §4.6 Q1 | **Hors lot 1** (lot 2) : décision « quelle source fait foi » |

---

## 3. Stratégie de migration

### 3.0 Règles communes

1. **Jamais de fusion silencieuse.** Tout cas non univoque va dans `migration_rapports.rejets[]` (`{collection, id, motif, candidats[], valeursComparees}`), plus un export CSV pour la revue.
2. **Fichier de décisions** (`migrations/decisions/<migration>.csv`, versionné) : une ligne par cas tranché par un humain (`idSource; action (LIER, CREER, IGNORER); idCible; auteur; date`). La migration le relit à chaque exécution.
3. **Rejeu** : une migration se met au point sur des **jeux de données construits dans les tests** (Testcontainers), qui reproduisent les cas attendus (doublons, ambiguïtés, formats anciens), jusqu'à **0 cas non résolu**. En production, elle n'est jouée que lorsque le rapport à blanc, produit par l'administrateur du serveur selon une procédure de `docs/exploitation/` puis renvoyé, est vide. Une copie anonymisée (§3.7) peut compléter ces jeux si l'administrateur la fournit ; aucun critère n'en dépend.
4. **Non destructive** : les collections sources sont **renommées** en `archive_*`, jamais supprimées dans le lot. Les champs remplacés (`siteAffecte` texte, `fournisseur` texte) sont conservés dans un `*Origine`.
5. **Vérification après migration** (test IT sur jeu de données, plus script de contrôle joué en prod par l'administrateur du serveur, résultats renvoyés) : comptages source = cible + rejets ignorés ; aucune référence vers un id inexistant (`$lookup` de contrôle) ; unicité (index créés sans erreur) ; 20 fiches comparées champ par champ, jointes au compte rendu.
6. **Normalisation** partagée par toutes les migrations : minuscules, sans accents (même règle que `front:shared/text-normalize.util.ts`, reprise côté serveur), sans espaces multiples ni ponctuation ; téléphones en E.164 (`+221` si 9 chiffres commençant par 7 ou 3, **à confirmer**) ; e-mails en minuscules, sans espaces.

### 3.1 Employés : 3 collections et 2 modèles de compte

**Source de vérité** : `dossiers_employes`. `DossierAgentIdMigrationRunner` a déjà relié les fiches par `agentId` (`back:DataLoader/DossierAgentIdMigrationRunner.java`).

Algorithme, pour chaque document de `employes_complet`, puis de `employes` :
1. `agentId` non vide et exactement **un** dossier avec ce `agentId` : candidat. Le nom normalisé doit concorder (égalité, ou distance de Levenshtein ≤ 2 sur nom + prénom). Sinon rejet `AGENTID_NOM_DIVERGENT`.
2. Sinon, `matricule` exact et unique : même contrôle du nom.
3. Sinon, téléphone ou e-mail normalisés, uniques des deux côtés : candidat, **rejet systématique** pour revue (`RAPPROCHEMENT_FAIBLE`), jamais lié automatiquement.
4. Aucun candidat : rejet `ORPHELIN`. Le fichier de décisions dit `CREER` (nouveau dossier) ou `IGNORER` (fiche morte).
5. Lien établi : ajout de l'id source à `idsHistoriques` ; les champs absents du dossier (téléphone, photo) sont complétés, **jamais écrasés**. Un conflit de valeur non vide est rejeté (`CONFLIT_CHAMP`).

Doublons internes de `dossiers_employes` (index uniques jamais créés, D3) : la recherche de doublons sur `matricule`, `agentId` et téléphone **précède** tout, sous la forme d'un rapport et de décisions humaines.

**Comptes ↔ dossiers** : `utilisateur.email` normalisé = `dossier.email` normalisé, avec exactement un dossier. Sinon le rapport conduit à une décision (`LIER` à un dossier, ou compte `TECHNIQUE` / `EXTERNE` sans employé). Le compte SUPERADMIN de `login` (`diarra.niang@…`, `DataLoader.seedSuperadmin`) est relié à son dossier s'il existe, sinon il devient un compte `INTERNE` à relier ; ses profils finaux dépendent de Q1 (D9, profil transitoire).

Vérification : tout compte INTERNE ou AGENT a un `employeId` valide ; chaque id de `employes_complet` et `employes` figure dans un `idsHistoriques` ou dans une décision `IGNORER` ; les `employeId` des pièces RH, des affectations terrain et des bons pointent tous vers un dossier.

### 3.2 Sites par nom côté RH

Sources : `DossierEmploye.affectations[].site` et `siteAffecte` (chaînes, `back:entities/rh/AffectationSite.java`, découpage par `back:util/SiteAffecteUtils.java`).

Algorithme :
- pour chaque libellé distinct, recherche dans `sites_clients` par `code` normalisé, puis par `nom` normalisé ;
- **un seul** site : `siteId` posé, libellé conservé dans `siteLibelleOrigine` ;
- zéro ou plusieurs sites : rejet `SITE_INCONNU` / `SITE_AMBIGU`, avec la liste des candidats et le nombre d'affectations concernées. Les décisions se prennent **par libellé** (un « Agence Plateau » est tranché une fois pour toutes ses occurrences).
- `siteAffecte` reste un champ **dérivé** de lecture, recalculé depuis les `siteId` (CLAUDE.md, mémoire « Affectations multi-sites »).

La confusion « agence / site client » (audit §4.6 Q7) est tranchée par le CDC (Q6) : il n'y a que des **locaux** (`sites_clients`), rattachés à un client. Un libellé « Agence Plateau » désigne un local (par exemple l'agence d'une banque cliente) et se rattache à lui par le fichier de décisions ; aucun objet agence n'est créé. Un libellé qui désigne en fait un dépôt Cleanic ou un siège (ni local servi, ni client) est rejeté `SITE_NON_LOCAL` et tranché à la main (`IGNORER`, ou création d'un local d'un client interne « Cleanic »).

Les sites existants sans client reçoivent leur `clientId` par §3.4 (un client par `raisonSociale` validée), **avant** que `clientId` devienne obligatoire.

Vérification : 0 affectation active sans `siteId` ; l'effectif par site avant (par libellé) et après (par id) est identique, sur le jeu de test comme dans le contrôle joué en production par l'administrateur.

### 3.3 Articles : 5 listes vers le catalogue unique

Ordre et algorithme :
1. **`stockv2_produits` → `articles`** : renommage, ids conservés. Aucune référence à réécrire (mouvements, bons, états, historique de coût). **Famille** posée depuis `TypeProduit` (Q12, CDC §8.1) : `MATIERE_PREMIERE` → matières premières, `PRODUIT_FINI` → produits finis, `CONSOMMABLE` → consommables, `EPI` → EPI et tenues. Les familles **emballages** et **matériel** n'ont pas d'équivalent : un article actuel classé ailleurs mais qui en relève (bidon vide, étiquette, monobrosse) n'est reclassé que par le fichier de décisions, sur une liste proposée par recherche de mots-clés dans le libellé. Nature : `DURABLE` pour la famille matériel, `CONSOMMABLE` sinon, sauf décision.
2. **`produits`** (legacy, sans écran) : rapprochement par `codeProduit` = `articles.code` (normalisé), sinon par libellé normalisé. Trouvé : lien historique seulement. Non trouvé : rejet `ARTICLE_LEGACY_ORPHELIN`, puis décision `CREER` (article inactif) ou `IGNORER`. Ensuite, archivage.
3. **`production_chimie_matieres_premieres`** :
   - par `code`, puis par libellé normalisé, contre `articles` ;
   - trouvé avec unité identique : `articleId` posé ;
   - trouvé avec unité différente : rejet `UNITE_DIVERGENTE` (kg contre L, sans densité, voir audit F071) ;
   - non trouvé : **création** de l'article (famille matières premières, ou emballages selon la catégorie de la matière), `articleId` posé ;
   - le code ou le libellé d'un autre article déjà pris donne un rejet `CONFLIT_CODE`.
   - Les références des formulations, OF et mouvements chimie (`matierePremiereId`) **ne changent pas**.
4. **`produits_phyto`** : même algorithme sur `nomCommercial`, création si absent.
5. **`terrain_materiel`** (Q12) : chaque unité du parc reçoit un `articleId` de famille matériel. Regroupement **proposé** par type et désignation normalisés (« Monobrosse 17" » ×4 donne un seul article, 4 unités), sans rien écrire ; validation humaine par l'Exploitation et le Stock ; puis création des articles et pose des `articleId`. Les champs du parc (numéro, date et valeur d'achat, affectation, entretiens) restent sur l'unité. Une unité sans décision fait échouer la migration (§3.0, règle 3), puisque l'`articleId` devient obligatoire.

Vérification : toute matière première et tout produit phyto actifs ont un `articleId` existant ; toute unité du parc a un `articleId` de famille matériel ; aucun code d'article en double ; somme des `quantiteEnStock` chimie inchangée (non migrée) ; valeur du stock Stock v2 inchangée (`ValeurStockService` avant/après, sur le jeu de test comme dans le contrôle joué en production par l'administrateur).

### 3.4 Fournisseurs et clients en texte libre

Sources : fournisseurs dans `BonEntree.fournisseur`, `ProduitStock.fournisseurPrincipal`, `MatierePremiere.fournisseur`, `MouvementStockChimie.fournisseur`, `ProduitPhytosanitaire.fournisseur` ; clients dans `Chantier.client`, `DestinataireBon.clientNom`, `SiteClient.raisonSociale` (audit « Données dupliquées »).

Algorithme en deux passes, **toujours avec une validation humaine** :
1. **Extraction** : valeurs distinctes normalisées, avec effectifs et exemples de documents. Regroupement *proposé* des valeurs proches (Levenshtein ≤ 2 ou inclusion, ex. « SENCHIM » / « Senchim SA »), sans rien écrire. Résultat : un CSV `proposition-fournisseurs.csv`.
2. **Validation** par un humain (Achats pour les fournisseurs, Commercial ou Exploitation pour les clients) : le CSV devient le fichier de décisions (`valeurOrigine; code fournisseur cible; raison sociale retenue`).
3. **Application** :
   - création des fiches ;
   - pose de `fournisseurId` / `clientId` sur chaque document, en gardant le texte en instantané (`fournisseurOrigine`) ;
   - une valeur absente du fichier fait **échouer** la migration (§3.0, règle 3).
   - Pour les sites : un client par `raisonSociale` distincte validée, puis `sites_clients.clientId`.

Vérification : 0 document postérieur à la migration avec un fournisseur ou client texte non relié ; nombre de fiches créées = nombre de lignes distinctes validées.

### 3.5 Grille des salaires et contrats clients

- **Grille** : chaque `categories_professionnelles` reçoit une version datée `dateEffet = 01/01/2000` portant les montants actuels (D7, écriture rétroactive permise parce que c'est une migration).
- **RIB, banque, IPRES, CSS de la grille** (Q15) : le code les appelle « champs perso du bulletin », mais une valeur portée par une grille vaut pour tous ses employés. La migration ne devine pas leur nature :
  1. rapport des valeurs distinctes par grille, avec le nombre d'employés rattachés par `categorieCode` ;
  2. décision par valeur : `EMPLOYEUR` (numéro IPRES ou CSS de Cleanic → paramètre d'entreprise, CDC §5.7), `EMPLOYE` (recopiée vers chaque employé rattaché **dont le champ est vide** ; RIB et banque → `modePaiement {VIREMENT, compte}`), ou `IGNORER` ;
  3. un conflit (valeur différente déjà présente sur l'employé) est rejeté `CONFLIT_CHAMP` ;
  4. les champs de la grille sont retirés une fois la décision appliquée, et `CalculPaieService` lit l'employé et les paramètres d'entreprise.
- **Contrats clients** : aucune source dans le code. Saisie ou import (D13) à partir de la liste des clients, contrats et sites validée par la Direction (CDC §14 « Reprise des données », §16 « Liste définitive des clients, contrats et sites »). Tant qu'elle n'est pas faite, `exploitation.site.contratObligatoire` reste à `false` (D8).

### 3.6 Fichiers en `byte[]`

Pour chacune des 13 entités, une ChangeUnit en **deux phases** :
1. **Copie** : par lots de 100 documents, le `byte[]` est écrit dans GridFS, puis la métadonnée `fichiers` avec son sha256, et la référence `fichierId` posée sur le document. Le `byte[]` est **conservé**. Reprise possible : un document qui a déjà `fichierId` est sauté.
2. **Vérification puis purge** (ChangeUnit distincte, jouée après une période d'observation de 2 semaines en prod, **à confirmer**) : on relit GridFS, on compare le sha256 au `byte[]`, puis `$unset`. Toute divergence est un rejet et le champ est conservé.

Vérification : nombre de `byte[]` non vides en phase 1 = nombre de `fichiers` créés ; 0 divergence de sha256 ; la taille moyenne des documents baisse (statistiques `collStats` jointes).

### 3.7 Copie anonymisée de la production

**Procédure facultative de l'administrateur du serveur** (pas d'accès au VPS, Q16). Elle sera écrite dans `docs/exploitation/copie-anonymisee.md` quand une migration en aura l'usage. **Aucun critère d'acceptation n'en dépend** : les migrations se testent sur des jeux construits dans les tests (§3.0). Scripts versionnés sous `ops/copie-anonymisee/`, aucune donnée versionnée :

1. **Extraction** par l'exploitant, sur le serveur : `mongodump --gzip --archive` vers un fichier chiffré, transfert vers **un seul poste sécurisé** désigné. La copie n'y est jamais décompressée hors d'un conteneur.
2. **Restauration** dans un conteneur `mongo:7.0` isolé (réseau Docker sans sortie, volume chiffré).
3. **Pseudonymisation déterministe** (script mongosh `anonymiser.js`, clé HMAC propre à la copie, détruite après usage). La même entrée donne toujours la même sortie, ce qui **préserve les doublons et les ambiguïtés** que les migrations doivent rencontrer :
   - noms et prénoms : `HMAC(nom normalisé)` → nom tiré d'un dictionnaire fictif. Deux variantes d'orthographe restent deux noms distincts, de même distance d'édition approximative (**à confirmer** : variante « mutation des lettres » si la distance doit être conservée) ;
   - e-mails : `u<hash8>@exemple.invalid`, domaine conservé sous forme de classe (interne / externe) ;
   - téléphones : `+22170` + 7 chiffres issus du HMAC, unicité préservée ;
   - RIB, IPRES, CSS, NINEA, adresses : HMAC tronqué ;
   - salaires et montants de paie : **conservés** (nécessaires aux tests de calcul). La copie reste donc **confidentielle** au même niveau que la paie ;
   - GPS des pointages : arrondi à 2 décimales ;
   - GPS des sites : conservé (données d'entreprise) ;
   - mots de passe et codes : remplacés par un hachage d'une valeur connue de test ;
   - fichiers (`byte[]`, GridFS) : remplacés par un fichier factice **de même taille** et de même type MIME. La taille est conservée pour mesurer la migration de §3.6 ;
   - texte libre (motifs, commentaires, réclamations) : remplacé par `lorem` de même longueur. **Exception** : les champs fournisseur et client en texte libre, pseudonymisés par HMAC pour conserver les regroupements de §3.4.
4. **Contrôle** `verifier-anonymisation.js` : recherche de motifs réels (domaine e-mail de l'entreprise, préfixes téléphoniques réels, liste de noms d'un échantillon de contrôle). Le moindre résultat bloque l'usage de la copie.
5. **Usage** : jouer les migrations à blanc, puis en réel, lire les rapports, compléter les fichiers de décisions. La copie est détruite à la fin de chaque campagne (journal de destruction).

---

## 4. Découpage en sous-branches

Chaque sous-branche est créée depuis `refonte/v3` et fusionnée dans `refonte/v3`, **dans les deux dépôts sous le même nom** (`refonte/v3-1a-socle-technique`…). Elle n'est fusionnée qu'avec `./gradlew test` vert, `ng build` vert et les specs front nouveaux verts.

**Ordre proposé** (charge indicative : 1 XL, 5 L, 7 M), **corrigé par rapport au point de départ :**
- **1g (droits) avant les référentiels** : la recette exige « visibles selon les droits », et le masquage (D9) conditionne les DTO de référentiels ;
- **1k scindé** : les notifications et l'interface SMS (1k) passent **avant 1h**, car les circuits notifient les valideurs ;
- **connexion agent et Mon espace (nouvelle 1m) après 1j**, car la recette passe par l'import Excel des 20 agents ;
- **le journal des connexions passe dans 1g** (il dépend de `comptes`). 1l garde les données personnelles et les sauvegardes.

```
1a ─┬─ 1b ─┬─ 1c ─── 1g ─┬─ 1d ─┬─ 1e ─┬─ 1f ─── 1i ─── 1k ─── 1h ─── 1j ─── 1m
    │      │             │      │      │
    └──────┴─────────────┴──────┴──────┴──────────────────────────────── 1l (après 1c + 1i)
```

### 1a — Socle technique : transactions, verrous, index, migrations

- **Objectif** : D1, D2, D3.
- **Lignes** : F023 (outillage), prérequis de F002 et F003.
- **Dépendances** : aucune.
- **Critères d'acceptation** :
  - `TransactionIT` : écriture de 2 documents, exception, aucun des deux en base ;
  - `ValidationConcurrenteIT` : 2 validations simultanées d'un bon SOUMIS donnent 1 succès et 1 409, mouvements générés une fois ;
  - `SoldeConcurrentIT` : 50 sorties concurrentes sur un stock de 10, solde jamais négatif ;
  - `MigrationsIT` : base vide, toutes les ChangeUnits, puis rejeu sans effet. Base « existante » (jeu représentatif), baseline sans rejouer les 12 runners ;
  - `IndexesAttendusIT` vert ; détection de doublons qui fait échouer la ChangeUnit d'index sur un jeu piégé ;
  - `docker-compose up` démarre un replica set initialisé ;
  - compte rendu du spike Mongock (Q-C) joint à la sous-branche : Mongock retenu, ou repli sur l'exécuteur maison avec la raison.
- **Taille** : M.
- **Bloquée par** : rien. La mise en production est une procédure écrite pour l'administrateur du serveur, à jouer au basculement final de la V3 (pas d'accès au VPS, Q16).

### 1b — Pièces : numérotation, annulation tracée, fin des suppressions, audit, outbox

- **Objectif** : D4, D5, D6 (journal d'audit) ; registre minimal des pièces.
- **Lignes** : F002, F003, F004 (et base de F009).
- **Dépendances** : 1a.
- **Critères d'acceptation** :
  - `NumerotationIT` : 100 validations concurrentes donnent 100 numéros consécutifs sans trou ; validation en échec, numéro non consommé ; changement d'exercice au 1er janvier (heure de Dakar), repart à `0001` ; passage de `9999` à `10000` sans erreur ni troncature ;
  - `RoutesSuppressionIT` : 0 route DELETE hors liste blanche ;
  - test ArchUnit des appels `delete*` ;
  - bons de stock (premier type porté) : annulation d'un bon EFFECTIF qui génère les mouvements inverses par événement, sans suppression ;
  - `OutboxIT` : événement écrit seulement si la transaction est validée ; consommateur rejoué 2 fois, effet une seule fois ; échec répété, `EN_ERREUR` après 10 tentatives ;
  - `JournalAuditIT` : chaque transition de pièce a sa ligne, champs sensibles masqués.
- **Taille** : L.
- **Bloquée par** : aucune (Q7 tranchée par le CDC §3).

### 1c — Paramètres datés et listes de choix

- **Objectif** : D7, et le volet « valeurs en dur » de D16.
- **Lignes** : F006, F021 (paramétrage des valeurs propres à l'entreprise).
- **Dépendances** : 1a ; 1b (audit des saisies).
- **Critères d'acceptation** :
  - `ParametreServiceTest` : lecture à date (avant, à et après la date d'effet), version atteinte immuable (409), **date d'effet passée refusée par l'API (422) et acceptée par une ChangeUnit** ;
  - valeurs du CDC §13.4 qui diffèrent de l'existant écrites en versions futures, valeurs actuelles inchangées le jour de la mise en service ;
  - migration des 5 singletons et des 3 propriétés `application.yml` : valeurs lues identiques avant et après (test par clé) ;
  - bulletin recalculé pour un mois passé avec les taux de ce mois (test sur 2 versions) ;
  - front : plus aucune valeur de seuil dans `constants/stock*.ts` (test de grep en CI) ;
  - écran « Seuils et listes » avec spec de composant ;
  - `AucuneValeurCleanicTest` : aucune chaîne « Cleanic » dans `back:` hors paramètres par défaut et seeds `demo`.
- **Taille** : M.
- **Bloquée par** : Q2 (administrateur du paramétrage), Q4 (multi-entreprises, pour figer D16).

### 1g — Comptes, profils, droits à 5 niveaux, masquage, journal des connexions

- **Objectif** : D9, et D6 pour le journal des accès (connexions, refus, verrouillage).
- **Lignes** : F010, F011, F012, F018.
- **Dépendances** : 1b, 1c.
- **Critères d'acceptation** :
  - `MigrationDroitsIT` : décisions identiques avant et après migration, pour toutes les routes de `DroitsModulesSecuriteIT` ;
  - `NiveauxDroitsIT` : LECTURE refusée en écriture (403), SAISIE refusée en validation, TOUT autorise l'annulation ;
  - `MasquageSerialisationTest` : champ `@Sensible` à `null` et listé dans `_masques` sans le droit ;
  - `EmployeSortiIT` : sortie d'un employé, compte désactivé, jeton suivant en 403 ;
  - `VerrouillageConnexionIT` : **3** échecs, verrouillage, journal ;
  - `ProfilsDirectionAdminIT` : ADMIN gère comptes et profils mais reçoit 403 sur toute route métier ; DIRECTION lit tout et n'écrit nulle part ; un compte ne modifie pas ses propres droits ; seul un compte DIRECTION attribue DIRECTION ou un droit sensible ;
  - `PerimetreIT` : un superviseur ne voit que les sites et agents de son périmètre ;
  - `DonneesSanteIT` : motif médical visible par la RH et l'intéressé, « absent » pour les autres ;
  - `RafraichissementJetonIT` ;
  - les tests du lot 0 (`SecuriteITBase` et suivants) restent verts ;
  - front : écrans Profils et Comptes avec specs ; sidebar et `module.guard.ts` alimentés par `/api/moi/droits`.
- **Taille** : L.
- **Bloquée par** : Q2 (qui reçoit le profil ADMIN). Q1 ne bloque pas : le profil transitoire `MIGRE-SUPERADMIN` garde l'existant jusqu'à la réponse.

### 1d — Référentiels employé et site (+ migration)

- **Objectif** : D8 (employé, site), §3.1, §3.2 ; suppression du module `EmployeComplet` et de la synchronisation `employes`.
- **Lignes** : F001 (employé, site), F023.
- **Dépendances** : 1g.
- **Critères d'acceptation** :
  - migrations §3.1 et §3.2 jouées sur des jeux de données construits dans les tests (cas univoques, ambigus, orphelins, conflits) : rejets attendus rapportés, 0 cas non résolu une fois le fichier de décisions appliqué, rejeu sans effet ; procédure du rapport à blanc en production écrite pour l'administrateur ;
  - `ReferentielEmployeIT` : un employé créé dans RH est lisible par Stock, Terrain et Chimie avec le masquage selon le profil ;
  - `ReferentielSiteIT` : un site créé est sélectionnable dans RH (affectation par `siteId`), Stock et Terrain ; création refusée sans `clientId` ; `contratObligatoire` respecté quand il est activé ;
  - index uniques `matricule`, `agentId`, `telephone`, `sites_clients.code` créés ;
  - `/api/employe-complet` absent (`RoutesPubliquesIT` et test de route).
- **Taille** : L.
- **Bloquée par** : Q10 (qui crée la fiche site tant que le module Commercial n'existe pas). Q5 et Q6 sont tranchées par le CDC.

### 1e — Référentiel article unique (+ migration, stock chimie compris pour le catalogue)

- **Objectif** : D8 (article), §3.3.
- **Lignes** : F001 (article), F023.
- **Dépendances** : 1d (sites pour les états de stock), 1c (listes famille, nature, unité).
- **Critères d'acceptation** :
  - migration §3.3 avec 0 cas non résolu sur des jeux de données construits dans les tests ; procédure du rapport à blanc en production écrite pour l'administrateur ;
  - `ReferentielArticleIT` : un article créé une fois est visible dans Stock, dans la fiche matière première (Chimie) et dans les produits phyto ;
  - la formulation lit code, libellé et unité depuis l'article ;
  - valeur de stock et soldes chimie inchangés (tests de comparaison) ;
  - collection `produits` archivée, `ProduitService` supprimé ;
  - les 6 familles du CDC §8.1 en liste de choix, chaque article actif dans une famille ;
  - parc : toute unité de `terrain_materiel` porte un `articleId` de famille matériel ; création d'une unité sans article refusée ;
  - référentiel `magasins` avec `MAG-CENTRAL` et `MAG-ATELIER` ; magasin `DEPORTE` refusé sans `siteId`.
- **Taille** : L.
- **Bloquée par** : aucune (Q5, Q12 tranchées par le CDC).

### 1f — Référentiels client, fournisseur, contrat minimal, grille des salaires

- **Objectif** : D8 (client, fournisseur, contrat client, grille datée), §3.4, §3.5.
- **Lignes** : F001 (client, fournisseur), F023.
- **Dépendances** : 1e (fournisseur principal de l'article), 1d (client du site).
- **Critères d'acceptation** :
  - fichiers de décisions validés pour fournisseurs et clients ;
  - `ReferentielClientIT` : un client créé est sélectionnable dans le bon de sortie, le chantier et la fiche site ;
  - `ReferentielFournisseurIT` : idem pour le bon d'entrée et la réception chimie ;
  - grille datée : bulletin d'un mois passé avec le salaire de base de ce mois ;
  - aucun nouveau document avec fournisseur ou client en texte seul (test de service).
- **Taille** : M.
- **Bloquée par** : aucune (Q11, Q15 tranchées par le CDC). La saisie des contrats réels attend la liste validée par la Direction (CDC §16), pas le développement.

### 1i — Fichiers et pièces jointes

- **Objectif** : D12, §3.6 (phase 1 ; la phase 2 est différée).
- **Lignes** : F007.
- **Dépendances** : 1g (contrôle d'accès par propriétaire), 1d-1f (entités à migrer stabilisées).
- **Critères d'acceptation** :
  - `StockageFichiersIT` : dépôt, lecture, sha256 ;
  - accès refusé sans droit sur le propriétaire (403) ;
  - migration §3.6 phase 1 avec 0 divergence ;
  - les routes de flux existantes renvoient le même contenu qu'avant (test octet par octet sur un jeu de fichiers construit dans les tests) ;
  - composant `app-pieces-jointes` avec spec, intégré à la fiche employé et à l'article.
- **Taille** : M.
- **Bloquée par** : aucune (Tika : décision technique).

### 1k — Notifications persistées et interface SMS

- **Objectif** : D14, l'interface plus le fournisseur factice de D11, et la double authentification par SMS (D9).
- **Lignes** : F008.
- **Dépendances** : 1b (outbox), 1g (destinataires = comptes).
- **Critères d'acceptation** :
  - `NotificationsIT` : événement, notification persistée, poussée STOMP au bon principal (y compris Terrain, corrigé) ;
  - lu / non lu ;
  - panne SMTP : création d'un bon réussie et e-mail réessayé ;
  - `FournisseurSmsFacticeIT` : le SMS apparaît dans `sms_envoyes` ;
  - `DoubleAuthentificationIT` : compte DIRECTION ou Finances sans le code SMS, pas de jeton ; code expiré ou 3 codes faux, refus et blocage ;
  - démarrage prod refusé en `factice` sans drapeau ;
  - cloche du header avec spec ; abonnements front morts retirés.
- **Taille** : M.
- **Bloquée par** : Q3 pour le vrai fournisseur, pas pour la livraison.

### 1h — Moteur de circuits de validation

- **Objectif** : D10, avec les congés puis les bons de stock.
- **Lignes** : F005.
- **Dépendances** : 1b, 1g, 1k.
- **Critères d'acceptation** :
  - `MoteurCircuitTest` : auteur et bénéficiaire exclus (y compris par délégation) ; étape Direction toujours dernière, en mode `TOUJOURS`, `AU_DESSUS_DU_SEUIL` (montant, cas anormal) et `JAMAIS` ; valideur Direction sans `VALIDER_EN_DERNIER` refusé ; profil TECHNIQUE refusé comme valideur ; pièce née d'une pièce validée validée d'office ; délégation datée active ou expirée ; circuit absent = 422 ;
  - congés : circuit seedé Superviseur → RH, avec Direction pour un bénéficiaire responsable ;
  - tests existants `CongeWorkflowService*` et `BonWorkflowServiceIT` verts **sans modification des assertions de contrat** ;
  - validation finale qui émet `PieceValidee` et notifie l'auteur ;
  - `GET /api/validations/a-traiter` agrégé congés + bons ;
  - écran de paramétrage des circuits et délégations avec spec.
- **Taille** : L.
- **Bloquée par** : aucune (Q13 tranchée par le CDC §13). Q1 désigne qui porte le profil DIRECTION, sans bloquer le moteur.

### 1j — Import / export génériques et écrans allégés

- **Objectif** : D13 pour les 7 référentiels.
- **Lignes** : F015, F016, F017, F022 (référentiels).
- **Dépendances** : 1d-1f, 1g (masquage), 1i (fichier importé conservé comme pièce jointe de l'import).
- **Critères d'acceptation** :
  - `ImportEmployesIT` : modèle généré, 20 lignes valides, aperçu sans écriture, confirmation qui crée 20 dossiers en une transaction ;
  - une ligne invalide parmi 20 donne 0 créé ;
  - annulation à 23 h autorisée, à 25 h refusée ;
  - annulation refusée si un employé importé est déjà référencé ;
  - `ExportMasqueIT` : export Excel sans `SALAIRES`, colonnes masquées ; sans `EXPORT`, 403 ; export journalisé ;
  - export Word produit et lisible (ouverture POI) ;
  - front : `app-liste-referentiel`, `app-panneau-lateral`, `app-formulaire-dynamique` avec specs ; `xlsx` retiré des écrans de référentiels.
- **Taille** : XL. **À couper** si besoin en 1j-1 (import + modèle) puis 1j-2 (exports + écrans).
- **Bloquée par** : aucune.

### 1m — Connexion agent par SMS et Mon espace en consultation

- **Objectif** : D11, et Mon espace limité (§0.3).
- **Lignes** : F013 (et F014 partiel, à valider §0.3).
- **Dépendances** : 1j (import des agents), 1k (SMS), 1g (type de compte AGENT).
- **Critères d'acceptation** (**test de recette du lot**) :
  - `RecetteLot1IT` : import de 20 agents par le modèle Excel, 20 comptes AGENT, 20 SMS dans le fournisseur factice. Chaque agent se connecte avec téléphone + code et n'accède qu'à `/api/moi/**` (toutes les autres routes renvoient 403, parcours automatique comme `RoutesPubliquesIT`) ;
  - un client, un site, un employé et un article créés une fois sont lus par chaque module, selon le profil ;
  - `ActivationAgentIT` : création du dossier, compte en attente, SMS d'activation ; activation avec code choisi ; codes triviaux, `agentId` et fin du téléphone refusés ; code d'activation expiré ou déjà utilisé refusé ;
  - `ConnexionAgentIT` : **3** échecs, verrouillage ; bon code signé par un autre appareil refusé et journalisé ; défi rejoué refusé ; code oublié avec réponse identique ; réinitialisation de l'appareil par la RH ;
  - front : écran de connexion agent et Mon espace (profil, planning, soldes, bulletins validés) avec specs.
- **Taille** : M.
- **Bloquée par** : Q3 (en production seulement). Q9 tranchée par le CDC.

### 1l — Données personnelles, sauvegardes

- **Objectif** : D15.
- **Lignes** : F019, F020.
- **Dépendances** : 1c (durées en paramètres), 1i (fichiers à purger).
- **Critères d'acceptation** :
  - `PurgeServiceIT` : mode à blanc sans écriture ; mode réel qui anonymise un ex-employé au-delà de la durée, garde ses bulletins, trace dans `purges` ;
  - scripts `ops/sauvegarde/` testés en local (dump, chiffrement, restauration dans un conteneur, contrôles) ;
  - procédure `docs/exploitation/sauvegarde.md` destinée à l'administrateur du serveur, avec les résultats à renvoyer ; aucun critère ne dépend de sa mise en service (pas d'accès au VPS, Q16).
- **Taille** : M.
- **Bloquée par** : Q8 pour les durées encore « à fixer » (le moteur est livré, la catégorie reste inactive). Q16 tranchée : pas de stockage hors serveur, donc la copie hors site du CDC §14 n'est pas livrée (risque signalé).

### Récapitulatif des lignes de la checklist

| Ligne | Sous-branche(s) | Ligne | Sous-branche(s) |
|---|---|---|---|
| F001 | 1d, 1e, 1f | F013 | 1m |
| F002 | 1b | F015 | 1j |
| F003 | 1b | F016 | 1j |
| F004 | 1b | F017 | 1j |
| F005 | 1h | F018 | 1g |
| F006 | 1c | F019 | 1l |
| F007 | 1i | F020 | 1l |
| F008 | 1k | F021 | 1c (décision Q4) |
| F010 | 1g | F022 | 1j (référentiels seulement) |
| F011 | 1g | F023 | 1a, puis 1d, 1e, 1f |
| F012 | 1g | F009, F014 | hors liste, voir §0.3 |

---

## 5. Questions restantes

### 5.1 Pour la Direction

| # | Question | Ce que le CDC en dit déjà | Bloque |
|---|---|---|---|
| Q1 | Le SUPERADMIN actuel (`diarra.niang@…`) reçoit-il le profil DIRECTION, le profil ADMIN, ou les deux ? Qui d'autre a le profil DIRECTION ? | Profil « Direction Générale » : tout en lecture, validation finale, tous les droits sensibles (§13) | Rien : le profil transitoire `MIGRE-SUPERADMIN` garde l'existant ; la réponse règle l'attribution finale (1g, 1h) |
| Q2 | Qui administre Paramètres et accès (profil ADMIN) : la Directrice Générale seule, ou un administrateur qu'elle désigne ? | « réservé à la Direction (ou à un administrateur qu'elle désigne) » (§13.1) ; question ouverte au §16 | 1c, 1g |
| Q3 | Quelle passerelle SMS, avec quel budget mensuel, quel nom d'expéditeur et quel engagement de délivrabilité ? SMS seul, ou SMS et WhatsApp ? | « passerelle SMS d'un opérateur sénégalais (WhatsApp en option, à trancher) » (§14) ; « coût des SMS à prévoir au budget » (§14) ; question ouverte au §16 | 1k et 1m **en production** seulement (le fournisseur factice suffit au développement et à la recette en test) ; double authentification en production |
| Q4 | Propriété du logiciel et calendrier de commercialisation : faut-il prévoir plus d'une entreprise sur un même serveur ? | « Données cloisonnées par entreprise, pour la commercialisation future » (§14) ; paramétrage par entreprise (§5.7) | 1c (figer D16 : une base par entreprise suffit-elle ?) |
| Q8 (reste) | Durées de conservation des ex-employés (identité, contrat, paie), des candidats, des journaux d'accès et d'audit ; délai légal des justificatifs médicaux ; durée des sanctions ; déclaration à la CDP et lieu d'hébergement | 10 ans pour les pièces et écritures, 12 mois pour le GPS et les photos de pointage (§14) ; conseil juridique à consulter (§16) | 1l (les catégories sans durée restent inactives) |
| Q10 | Tant que le module Commercial (lot 5) n'existe pas, qui crée la fiche site, la fiche client et le contrat client : l'Exploitation, ou un utilisateur désigné comme « Commercial » dès le lot 1 ? | Cible : le Commercial crée le site à la signature, l'Exploitation le complète (GPS, rayon, horaires, QR) (§3, §6.1) | 1d (droits de saisie sur `referentiels.sites`, `clients`, `contratsClients`) |

### 5.2 Pour vous

Aucune. Q16 (accès au VPS, stockage hors serveur) est tranchée le 24/11 (R.1) : **pas d'accès au VPS**, aucun stockage hors serveur prévu.

**Contrainte permanente qui en découle.** Toute opération serveur (sauvegarde, détection des doublons, copie anonymisée, mise en service, fermeture du port Mongo) est livrée sous forme de procédure écrite dans `docs/exploitation/`, destinée à l'administrateur du serveur, avec les résultats attendus à vous renvoyer. Aucun critère d'acceptation ni aucune étape de développement ne dépend d'un accès au serveur ; les migrations se testent sur des jeux de données construits dans les tests.

### 5.3 Questions closes

Q5, Q6, Q7, Q9, Q11, Q12, Q13, Q14, Q15 (tranchées par le CDC et vos corrections), Q-A, Q-B, Q-C, Q-D, Q16 (vos réponses ; Q16 corrigée le 24/11 : pas d'accès au VPS). Détail et sources : « Révision du 23/11 », R.1.

---

## 6. Résumé

1. Le lot 1 commence par un socle technique (1a) : replica set, `MongoTransactionManager`, `@Version`, soldes atomiques, Mongock (après un spike d'un jour, repli maison), index versionnés.
2. Une pièce commune (1b) reçoit son numéro **par type et par exercice civil**, au format du CDC (`DEV-2026-0001`, 4 chiffres minimum), **à la validation, dans la transaction**, donc sans trou ; les 39 DELETE deviennent des annulations tracées.
3. Les effets passent par une outbox Mongo avec des consommateurs idempotents : pas d'appel croisé entre modules, base des futures écritures comptables.
4. Toute règle chiffrée devient un paramètre daté ; une date d'effet passée est interdite hors migration ; le front ne garde plus aucun seuil. La migration garde les valeurs actuelles et propose celles du CDC en versions futures.
5. Les droits passent à 5 niveaux par sous-rubrique, plus les 5 droits sensibles du CDC, avec masquage central par `@Sensible` et périmètre par sites. **DIRECTION** (métier : lecture, validation finale) et **ADMIN** (technique : comptes, paramètres) sont deux profils distincts. Double authentification par SMS pour la Direction et les Finances ; blocage après 3 codes faux.
6. Le moteur de circuits reprend le tableau du CDC §13 : Direction en dernier (toujours, au-dessus du seuil, jamais), personne ne valide sa propre demande, délégation tracée. Les congés passent à Superviseur → RH (Direction pour les responsables), les bons de stock à magasinier → Direction.
7. Huit référentiels ont chacun le propriétaire fixé par le CDC §3. Le site est le « local » du CDC, rattaché à un client et à un contrat client ; les magasins forment un référentiel à part ; l'article a 6 familles, matériel compris, et le parc porte toujours un `articleId`.
8. L'agent active son compte par un code reçu par SMS, choisit son code à 4 chiffres, et son compte est lié à son téléphone par une clé non extractible : c'est ce qui permettra au lot 2 de remplacer le PIN de kiosque par le pointage QR.
9. Chaque fusion (employés, sites par nom, articles et parc, tiers en texte libre, champs de paie de la grille, fichiers) produit un rapport de rejet et se pilote par fichier de décisions, sans jamais fusionner en silence. Elle se met au point sur des jeux de données construits dans les tests, puis se joue en production selon une procédure écrite pour l'administrateur du serveur.
10. 13 sous-branches. Blocages Direction restants : 1c (Q2, Q4), 1g (Q2), 1d (Q10) ; 1l livre son moteur et attend Q8 pour les durées manquantes ; Q3 ne bloque que la production de 1k et 1m. 1a n'attend aucune question (Q16 tranchée le 24/11 : pas d'accès au VPS ; toute opération serveur est une procédure pour l'administrateur, dans `docs/exploitation/`). Il reste 6 questions pour la Direction, aucune pour vous. Conception validée le 24/11 : 1a est lancée.
