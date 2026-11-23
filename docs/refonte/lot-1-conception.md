# Lot 1 V3 — Conception du socle

Document de conception, rédigé le 23/11/2026 sur la branche `refonte/v3` (backend et frontend). **Aucun code n'a été modifié.** Ce document attend votre validation avant la création de toute sous-branche.

## 0. Cadre

### 0.1 Sources

| Source | Chemin |
|---|---|
| État des lieux V3 (audit du 20/11/2026) | `pointage-agents/docs/audit/etat-des-lieux-v3.md` (cité « audit ») |
| Checklist V3 | `pointage-agents/docs/audit/checklist-v3.csv` |
| Lot 0 sécurité | `Pointage-Cleanic-Backend/docs/securite/correctifs-lot-0.md` (cité « lot 0 ») |
| Documentation des modules | `Pointage-Cleanic-Backend/CLAUDE.md`, `.claude/docs/module-*.md` |
| CDC V3 | WTK-CDC-2026-005 V1.4 : **absent des dépôts**. Seules la définition du lot 1 et les six règles transmises avec la demande ont été utilisées. Le libellé exact de chaque ligne F n'est pas disponible : il est déduit de la colonne « Écart » de la checklist. |

**Convention des preuves** (la même que l'audit) : `back:` désigne `src/main/java/com/example/Pointage_Cleanic/` du backend, `front:` désigne `src/app/` du frontend, et les fichiers de racine sont nommés tels quels.

« **À confirmer** » marque une hypothèse que ni le code ni le CDC transmis ne tranchent.

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

### 0.3 Écart de périmètre à valider par vous

Les lignes de checklist transmises **excluent F009** (registre des pièces, barre Lit / Alimente) **et F014** (Mon espace). Or la définition du lot 1 dans le CDC cite « registre des pièces et liens entre modules » et « Mon espace en consultation », et le test de recette exige que les agents « ne voient que Mon espace ».

Proposition : un registre des pièces **minimal côté serveur**, alimenté par les événements (1b), et un Mon espace **en consultation limitée** (profil, planning, soldes de congés, bulletins validés) dans 1m.

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

**Conséquences.** Opération d'exploitation sur le VPS (accès requis, voir Q16) ; les tests IT existants restent valides (déjà sur replica set) ; tout test « concurrence » devient écrivable : 2 validations simultanées ⇒ 1 seule réussit.

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
- **Spike préalable** (1 jour) : Mongock 5 avec Boot 3.3.6 / Spring Data MongoDB 4.3.6 et les transactions. **Repli** si le spike échoue : un exécuteur maison d'environ 150 lignes (interface `Migration { String id(); void executer(MongoTemplate) }`, collection `migrations_appliquees`, verrou par document `findAndModify` à bail).

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

**Conséquences.** Il faut un premier passage en lecture seule sur la copie anonymisée (§3.7) pour connaître les doublons réels (matricule, agentId, code produit, couple produit/site).

### D4 — Modèle « pièce » commun

**Contexte.** Quatre services de compteurs sans service commun (`back:services/stockv2/CompteurStockService.java`, `productionchimie/CompteurOfService.java`, `productionchimie/CompteurLotService.java`, `terrain/CompteurTerrainService.java`). Séquence journalière (`BE-yyyyMMdd-NNN`, sauf `CH` annuel), référence prise **dès le brouillon** et brouillon supprimable, d'où des trous ; `genererReference` utilise `LocalDate.now()` sans fuseau (audit §5.3). 39 `@DeleteMapping` (audit §5.2), dont des pièces validées supprimables par `back:services/stockv2/SuppressionDefinitiveService.java`.

**Décision.**
- **Classe de base `PieceBase`** (incorporée dans chaque document de pièce) :
  - `type` (`TypePiece` : CONGE, BE, BS, INV, MVT, BULLETIN, DECLARATION, SANCTION, IMPORT…) ;
  - `numero` (nul tant que la pièce n'est pas validée), `exercice` ;
  - `statut` (`BROUILLON → SOUMISE → VALIDEE`, branches `REFUSEE`, `ANNULEE`, `ABANDONNEE`) ;
  - `creePar` / `creeLe`, `soumisePar` / `soumiseLe`, `valideePar` / `valideeLe` (identifiants de compte + **instantané** du nom) ;
  - bloc `annulation {par, le, motif, pieceCorrectiveId}` et `version`.
- **Numérotation** :
  - un service unique `NumerotationService`, une collection unique `compteurs_pieces`, `_id = "<TYPE>-<EXERCICE>"` ;
  - `findAndModify($inc)` **dans la transaction de validation** : si la validation échoue, l'incrément est annulé avec elle, donc ni trou ni doublon ;
  - format `<TYPE>-<EXERCICE>-<NNNNNN>` (ex. `BS-2027-000123`) ;
  - exercice = année civile en `Africa/Dakar` via l'horloge injectée (`back:configurations/TimeConfig.java`), début d'exercice en paramètre daté (**à confirmer**, Q7) ;
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

**Conséquences.** Les contrats du front changent : les boutons « Supprimer » deviennent « Annuler » ou « Abandonner », et le numéro est vide en brouillon ; les références existantes (`BE-20260617-001`) sont conservées telles quelles : pas de renumérotation de l'historique ; les nouveaux numéros commencent à `000001` sur l'exercice courant, ou après le maximum existant si la Direction veut la continuité (Q7).

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
- **Blocage** : après N échecs en M minutes, le compte est verrouillé T minutes (3 paramètres datés, valeurs initiales 5 / 15 / 15, alignées sur `pointage.limite.*` du lot 0).
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
  - une version dont la date d'effet est passée est immuable ;
  - index unique `(cle, dateEffet)`.
- **Lecture à date** : `ParametreService.valeurA(cle, LocalDate date)` renvoie la dernière version de `dateEffet <= date`, avec un cache invalidé à l'écriture. Un calcul (bulletin, retard, valorisation) passe **toujours** la date du fait, jamais `now()`. Exemple : le bulletin de mars utilise les taux en vigueur en mars.
- **Paramètres composites** (barème IR par tranches, paliers d'ancienneté des congés) : type `BAREME` ou `JSON` avec schéma. Les 5 singletons sont migrés vers des clés du catalogue (une ChangeUnit par singleton). La première version prend `dateEffet = 01/01/2000`, pour couvrir tout l'historique avec la valeur actuelle, et l'auteur « migration ».
- **`listes_choix`** : `{liste ("rh.typesConge"), code, libelle, ordre, actif, dateDebut, dateFin, attributs (ex. décompte du solde oui/non)}`.
  - Les enums métier extensibles y migrent au fil des lots ; les enums de **statut** restent du code ;
  - lot 1 : le moteur, plus les listes nécessaires aux fiches minimales (motifs de désactivation, motifs d'annulation, familles d'articles, natures, unités).
- **Écran « Seuils et listes »** (front), sous Paramètres et accès :
  - une page par module ;
  - pour chaque clé : valeur courante, valeur future programmée et historique ;
  - saisie d'une nouvelle valeur datée avec motif ;
  - droit `parametres.seuils` niveau SAISIE, et niveau VALIDATION pour une date d'effet passée (**à confirmer** : faut-il interdire purement les dates passées ?).
- Le front ne garde **aucune** valeur de seuil : `GET /api/parametres/valeurs?module=stock&date=` remplace `front:constants/stock-v2-valorisation.constants.ts` et `stock.constants.ts`. Les constantes réglementaires (`paie.constants.ts`) suivent le même chemin.

**Écarté.** Un document « jeu complet » par module et par date : une modification d'une seule valeur recopie tout, et les différentiels sont illisibles ; Spring Cloud Config : pas de notion de date d'effet.

**Conséquences.** Les constantes migrent au rythme des lots. Le lot 1 livre le moteur, les 5 singletons, les 3 propriétés `application.yml` et les seuils Stock dupliqués avec le front ; les services lisent par `ParametreService` ; les getters de singletons disparaissent.

### D8 — Référentiels uniques

**Contexte.** La seule donnée partagée par identifiant est le site (`sites_clients`, via `back:services/stockv2/ReferentielSiteService.java`). Ailleurs (audit §1, « Données dupliquées ») :
- employé en 3 collections ;
- article en 5 listes ;
- client et fournisseur en texte libre ;
- site par nom côté RH (`back:entities/rh/AffectationSite.java`, `back:util/SiteAffecteUtils.java`).

**Décision.** Un propriétaire par référentiel. Les autres modules le **lisent**, par identifiant, au travers d'un service de lecture `Referentiel<X>Lecture`, et ne recopient que des **instantanés figés dans une pièce** (nom, code, libellé au moment de la pièce, dans un sous-objet `instantane`).

| Référentiel | Collection cible | Propriétaire | Champs minimaux | Références (par id) | Instantanés autorisés |
|---|---|---|---|---|---|
| Employé | `dossiers_employes` (conservée) | RH / Personnel | matricule, agentId (pointage), nom, prénom, **téléphone (E.164)**, e-mail, poste, département, statut, dateEmbauche, dateSortie, affectations[{siteId, dateDebut, dateFin, horaires}], superieurHierarchiqueId, RIB / n° IPRES / CSS (`@Sensible`) | siteId → `sites_clients`, categorieCode → grille, superieur → dossier | nom, prénom, matricule dans congés, bulletins, absences, HS, sanctions, bons |
| Site | `sites_clients` (conservée) | Commercial dans la cible, Exploitation tant que le Commercial n'existe pas (**à confirmer**, Q10) | code (unique), nom, **clientId**, **contratClientId**, type (SITE, AGENCE, MAGASIN, **à confirmer** Q5/Q6), adresse, GPS, rayon, actif | clientId, contratClientId | code, nom dans bons, mouvements, pointages, affectations |
| Article | **`articles`** (renommage de `stockv2_produits`, **ids conservés**) | Stock / Achats | code (unique), libellé, famille (liste), nature (consommable, durable), unité (liste), catégorie, fournisseurPrincipalId, actif, prix (`@Sensible PRIX_ACHAT`) | categorieId, fournisseurId | code, libellé, unité, coût unitaire dans lignes de bon, mouvements, inventaires |
| Extensions article | `production_chimie_matieres_premieres` (+ `articleId`), `produits_phyto` (+ `articleId`) | Industrie / Exploitation pour les champs propres (MA %, FDS, homologation, délais) | articleId + champs métier propres | articleId | — |
| Client | **`clients`** (nouvelle) | Commercial (lot 5) ; fiche minimale au lot 1 | code, raison sociale, NINEA (**à confirmer**), contacts, adresse, statut (PROSPECT, CLIENT, ANCIEN), actif | — | raison sociale dans bons, chantiers, futures factures |
| Fournisseur | **`fournisseurs`** (nouvelle) | Achats / Stock | code, raison sociale, NINEA, contacts, adresse, conditions de paiement (liste), actif | — | raison sociale dans bons d'entrée, mouvements chimie |
| Contrat client (minimal) | **`contrats_clients`** (nouvelle) | Commercial | numéro, clientId, siteIds[], dateDebut, dateFin, statut, effectif contractuel par site | clientId, siteIds | numéro dans site et futures factures |
| Grille des salaires | `categories_professionnelles` (conservée, **versions datées**) | RH / Paie | code, libellé, niveau, échelon, salaire de base et primes **par date d'effet** (D7) | — | code et montants figés dans le bulletin (déjà le cas : `back:entities/rh/BulletinPaie.java`) |

Points particuliers :
- **Contrat** : le lot 1 du CDC cite « contrat » entre « client » et « site ». On suppose le **contrat client** (F085), puisque le contrat de travail existe déjà (`back:entities/rh/Contrat.java`). **À confirmer** (Q11).
- **RIB, IPRES, CSS** : aujourd'hui portés par la grille et recopiés dans le bulletin depuis elle (`CalculPaieService.orchestrer`, audit RH « Données dupliquées »). Cible : sur l'employé (`@Sensible`). **À confirmer** (Q15).
- **Solde chimie** : `MatierePremiere.quantiteEnStock` reste provisoirement dans son module. C'est une dette nommée, levée au lot 3 (stock) ou 8 (Industrie). Le lot 1 n'unifie que le **catalogue**, comme demandé.
- **Matériel** (`back:entities/terrain/Materiel.java`) : bien durable suivi à l'unité, ce n'est pas un article. Il reste à part, avec un `articleId` facultatif (**à confirmer** avec F061 / F106).
- **Lecture « selon les droits »** : chaque référentiel expose `GET /api/referentiels/<x>` (recherche, sélecteur). Les champs sont masqués par D9. Le droit `referentiels.lecture` est accordé par défaut à tout profil non agent.

**Écarté.** Un « méga-référentiel » générique de tiers (client et fournisseur fusionnés) : séduisant pour la comptabilité, mais aucune demande du CDC transmis. À revoir au lot 4 ; laisser chaque module garder sa liste avec une synchronisation : c'est la situation actuelle.

**Conséquences.** Les sélecteurs du front pointent vers les routes de référentiel. Les écritures de fiches passent par leur module propriétaire. Il faut 5 migrations de rapprochement (§3).

### D9 — Comptes, profils et droits à 5 niveaux

**Contexte.** Deux modèles : `login` (`back:entities/User.java`, seul porteur de SUPERADMIN) et `utilisateur` (`back:entities/Utilisateur.java`), sans lien vers le dossier employé. Le rapprochement se fait par e-mail non unique (`back:services/rh/CongeIdentiteService.java`) ; droits en booléens par sous-rubrique (`back:entities/GestionModules/ModulesAutorises.java` + `SousModules/{Rh,Stock,Terrain,ProductionChimie}.java`), contrôlés depuis le lot 0 par `back:security/Droits.java` et `DroitsExpr.java` ; contrôles de rôle en dur dans les services (`HabilitationStock`, `CongeIdentiteService`…) ; masquage par DTO réduit (`DossierEmployeResumeDto`, lot 0 C4) ; claim JWT `modules` (`back:security/JwtUtil.java`), enrichi à la connexion par `back:services/rh/CongeModuleEnricher.java`.

**Décision.**
- **`comptes`**, collection unique qui remplace `login` et `utilisateur` :
  - `{id, employeId (obligatoire sauf type TECHNIQUE), type (INTERNE, AGENT, EXTERNE, TECHNIQUE), identifiants: {email?, telephone?}, motDePasseHash?, codeConnexionHash? (D11), profilIds[], actif, motifDesactivation, dateExpiration (accès externe daté), mustChangePassword, echecs, verrouilleJusqua, version}` ;
  - unicité de l'e-mail et du téléphone (index unique sparse).
- **Désactivation automatique à la sortie** : consommateur de l'événement `EmployeSorti` (F010).
- **`profils`** : `{code, libelle, droits: {"<module>.<sousRubrique>": NIVEAU}, droitsSensibles: [..], actif}`.
  - **Niveaux ordonnés** : `AUCUN < LECTURE < SAISIE < VALIDATION < TOUT`. `TOUT` ajoute l'annulation de pièces validées et le paramétrage de la sous-rubrique.
  - **Droits sensibles** (cumulatifs, indépendants des niveaux) : `SALAIRES`, `DONNEES_PERSONNELLES`, `PRIX_ACHAT`, `CHIFFRE_AFFAIRES`, `EXPORT`. **À confirmer** (Q14).
  - Un compte cumule ses profils au maximum par sous-rubrique.
- **Catalogue des sous-rubriques** en code (`CatalogueDroits`), généré à partir des sous-modules actuels (19 RH, 27 Stock, 9 Terrain, 7 Chimie) plus `parametres.*` et `referentiels.*`.
- **API de contrôle** (évolution de `Droits`) :
  - `@droits.peut('stock.bonsSortie', 'SAISIE')` ;
  - la forme du lot 0 `@droits.peut('stock.bonsSortie')` est conservée et vaut `LECTURE` pour les GET, ce qui rend la migration progressive ;
  - `@droits.sensible('SALAIRES')` ;
  - `estSuperAdmin()` devient `aProfil('DIRECTION')` (Q1).
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
  3. rôles : `CONTROLEUR_STOCK` → `VALIDATION` sur `stock.bonsEntree`/`bonsSortie`/`inventaires` ; `RH` → `TOUT` sur `rh.*` ; `SUPERADMIN` (`login`) → profil `DIRECTION` (`TOUT` partout + tous les droits sensibles) ;
  4. test `MigrationDroitsIT` : pour chaque compte d'un jeu représentatif et chaque route de `DroitsModulesSecuriteIT`, la décision avant et après migration est **identique**.
- **JWT** : il ne porte plus `modules`, seulement `sub`, `type` et `employeId`. Le front lit `GET /api/moi/droits`, utilisé par la sidebar et `module.guard.ts`.
- **F018** : déconnexion après inactivité (paramètre, 30 min proposées), jeton de rafraîchissement en cookie HttpOnly (rotation, révocation à la désactivation), durée du jeton d'accès ramenée à 15 min. Double authentification : **décision de la Direction** (Q14), conçue comme une étape de plus de la connexion si elle est retenue.

**Écarté.** RBAC Spring par `hasAuthority` sur des chaînes : les niveaux ordonnés et le masquage ne s'y expriment pas ; garder les deux collections de comptes : c'est la source de la duplication du mot de passe (audit §5.6).

**Conséquences.** L'écran `front:adminPage/gestion-privilege/` est réécrit en deux écrans, Profils et Comptes ; les 49 specs front cassés du lot 0 sur ces écrans (lot 0 §1) seront remplacés, pas réparés.

### D10 — Moteur de circuits de validation

**Contexte.** Les circuits sont codés écran par écran (F005) :
- congés Supérieur → RH → DG dans `back:Enum/rh/NiveauValidationConge.java` et `back:services/rh/CongeWorkflowService.java` ;
- bons BROUILLON → SOUMIS → VALIDE, validés par le seul SUPERADMIN (`back:services/stockv2/HabilitationStock.java`).

Il n'y a ni seuil ni délégation.

**Décision.**
- **`circuits_validation`** :
  - `{typePiece, code, dateEffet, conditions: {montantMin?, montantMax?, siteIds?, typeSortie?}, etapes: [{ordre, valideur: {genre: PROFIL | SUPERIEUR_HIERARCHIQUE | RESPONSABLE_SITE | COMPTE, ref}, delaiHeures, escaladeVers?}], actif}` ;
  - versionné par date d'effet, comme D7 : une pièce garde le circuit en vigueur **à sa soumission**.
- **Choix du circuit** : le plus spécifique dont les conditions sont remplies. Si aucun circuit ne convient, la soumission est refusée (422 `CIRCUIT_ABSENT`).
- **Invariants vérifiés par le moteur, et testés** :
  1. **Personne ne valide sa propre pièce**, ni directement ni par délégation : l'auteur et le bénéficiaire (congé) sont exclus de toutes les étapes ;
  2. **Direction en dernier** : si le circuit contient une étape Direction, c'est la dernière, ce qui est contrôlé à l'enregistrement du circuit. Au-delà du seuil paramétré par type de pièce, une étape Direction est exigée ;
  3. un même compte ne valide pas deux étapes d'une même pièce ;
  4. refus = motif obligatoire (au moins 10 caractères, comme `MOTIF_REFUS_MIN_LENGTH` de `CongeWorkflowService`).
- **`delegations`** : `{delegantCompteId, delegataireCompteId, typesPiece[], dateDebut, dateFin, motif, creePar}`. Chevauchements autorisés, délégation de délégation interdite. Tout est journalisé (D6).
- **`instances_validation`** : `{pieceType, pieceId, circuitId, circuitVersion, etapeCourante, statut, decisions: [{etape, compteId, pourLeCompteDe? (délégation), decision, motif, le}]}`.
- **API commune** :
  - `GET /api/validations/a-traiter` (vue agrégée, base de F030 au lot 11) ;
  - `POST /api/validations/{instanceId}/decider` ;
  - les routes existantes `/valider` et `/refuser` des modules deviennent des façades.
- **Effets** : la dernière décision favorable passe la pièce en `VALIDEE`, attribue le numéro (D4) et publie `PieceValidee` (D5), dans une seule transaction. Les notifications aux valideurs passent par D14.
- **Premier cas porté** : les congés.
  - Circuit initial = Supérieur → RH → Direction, ce qui reproduit `NiveauValidationConge` ;
  - les contrats d'API de `TempsPresencesCongeController` sont conservés (`/demandes/a-valider`, `/valider`, `/refuser`) ;
  - le comportement actuel sert d'oracle de non-régression (tests `CongeWorkflowService*` existants).
- **Deuxième cas** : les bons d'entrée et de sortie, avec un circuit initial à une étape `VALIDATION` sur `stock.bons*`, ce qui reproduit le SUPERADMIN / CONTROLEUR_STOCK actuel. Les transitions invalides restent en **409** (contrat figé, CLAUDE.md Stock 7.4).

**Écarté.** Moteur BPMN (Flowable, Camunda) : poids, base relationnelle requise, et circuits simples ; un enum de niveaux par module : la situation actuelle.

**Conséquences.** Les seuils et les étapes réels sont une décision de la Direction (Q13). Le lot 1 livre le moteur et deux types de pièce. Les autres pièces (paie, caisse, achats) s'y branchent dans leur lot.

### D11 — Connexion agent par téléphone et code à 4 chiffres

**Contexte.** La connexion actuelle se fait par e-mail et mot de passe (`back:controllers/LoginController.java`). Aucun SMS (F013, audit §5). Le code à 4 chiffres existant (`DossierEmploye.agentId`) est le **code de pointage**, saisi sur un kiosque partagé et visible des collègues : il ne doit pas devenir un secret de connexion.

**Décision.**
- Interface **`FournisseurSms`** (`ResultatEnvoi envoyer(String numeroE164, String message, String reference)`) :
  - implémentation **`FournisseurSmsFactice`** active par défaut (`sms.fournisseur=factice`) : elle écrit dans `sms_envoyes` (numéro, message, date) et **n'envoie rien**. Un écran de développement, profil non-prod et droit `parametres.journaux`, affiche les SMS « envoyés » ;
  - en prod, le démarrage est refusé si `sms.fournisseur=factice` sans drapeau explicite, sur le modèle de `SecretsRequisVerificateur` ;
  - un vrai fournisseur viendra dans une classe à part, choisie par propriété (Q3).
- **Envoi** toujours via notifications et outbox (D14), jamais dans la transaction. Nouvel essai géré par le dispatcher.
- **Activation** :
  1. l'import (D13) ou la fiche employé crée un compte `AGENT` (profil `AGENT`, seul droit `monEspace.*` en LECTURE) ;
  2. un **code de connexion à 4 chiffres** est tiré par `SecureRandom`, distinct de `agentId` (Q9), puis **haché** (BCrypt) ;
  3. le SMS d'activation part avec le code. `mustChangeCode` est facultatif (**à confirmer** : l'agent choisit-il son code ?).
- **Connexion** `POST /api/agent/connexion {telephone, code}` :
  - numéro normalisé E.164 (`+221` par défaut, paramètre), **5 échecs ⇒ verrouillage 15 min**, puis renvoi d'un nouveau code par SMS sur demande (`POST /api/agent/code-oublie`, réponse identique que le numéro existe ou non, comme `ResetPasswordService` au lot 0) ;
  - limite de débit par IP et par téléphone, réutilisant le principe de `back:services/pointage/PointageLimiteurDebit.java`.
- **Jeton agent** : `type=AGENT`. Toute route hors `/api/moi/**` refuse ce type (règle globale dans `Droits`, plus un test qui parcourt les routes comme `RoutesPubliquesIT`).
- Routes publiques nouvelles : `POST /api/agent/connexion`, `POST /api/agent/code-oublie`. Elles sont ajoutées à `SecurityConfig` **et** à la liste blanche de `RoutesPubliquesIT`.

**Écarté.** Réutiliser `agentId` : secret partagé de fait ; lien magique par SMS : plus coûteux en SMS, inutilisable sur un téléphone partagé ; OTP à chaque connexion : un SMS par connexion, coût récurrent (**à revoir** avec le choix du fournisseur).

**Conséquences.** 4 chiffres donnent 10 000 combinaisons : la sécurité repose **entièrement** sur le verrouillage et la limite de débit, qui sont testés. Mon espace ne montre que des données de l'agent lui-même (`employeId` du jeton).

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
- **Catalogue de conservation** : paramètres datés (D7) `conservation.<categorie>` pour les ex-employés (identité, contrat, paie), les candidats, les positions GPS des pointages, les journaux d'accès et d'audit, les SMS factices, les événements traités et les fichiers par catégorie. **Durées à fixer par la Direction** avec son conseil (loi sénégalaise 2008-12 sur les données personnelles et obligations sociales, **à confirmer**, Q8).
- **`PurgeService`** `@Scheduled` mensuel :
  - il **anonymise** et ne supprime pas : les pièces restent, l'identité est remplacée par un pseudonyme stable, les coordonnées GPS sont tronquées, les fichiers d'identité sont archivés puis effacés de GridFS ;
  - il trace chaque passage dans `purges` (`{le, categorie, critere, nombre, idsAffectes (hachés)}`) et dans le journal d'audit ;
  - mode « à blanc » qui produit le rapport sans écrire, obligatoire avant le premier vrai passage ;
  - c'est la seule exception documentée à la règle 5 pour les fichiers.
- **Sauvegardes** (actions d'exploitation, scriptées dans le dépôt sous `ops/sauvegarde/`) :
  - `mongodump --oplog --gzip` quotidien, chiffrement (`age` ou `gpg`, **à confirmer**), copie **hors serveur** (stockage à désigner, Q16), rotation 7 jours / 4 semaines / 12 mois ;
  - **test de restauration mensuel** scripté : restauration dans un conteneur jetable, puis contrôles de comptage et d'intégrité (`IndexesAttendusIT` en mode lecture), compte rendu horodaté ;
  - authentification Mongo activée et port non publié, en même temps que le replica set (1a).

**Écarté.** Suppression physique des ex-employés : elle casserait les pièces (bulletins, déclarations) à conserver légalement ; snapshot de disque du VPS seul : non testé, et pas hors site.

**Conséquences.** Sans accès au VPS, la partie sauvegarde reste un livrable de scripts et de procédure (`docs/exploitation/sauvegarde.md`), non mis en service.

### D16 — Multi-entreprises

**Contexte.** Aucun `tenantId` (F021). Valeurs Cleanic en dur : e-mail HTML de `ResetPasswordService`, grille « Grille générique Cleanic » de `DataLoader`, CORS dans `back:security/OriginesAutorisees.java`, expéditeur de `EmailService`. La décision de la Direction (propriété du logiciel, revente) est en attente (Q4).

**Décision (coût minimal aujourd'hui, porte ouverte).**
- **Une base par entreprise, donc un déploiement par entreprise.** Aucun champ `entrepriseId` au lot 1.
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
| `comptes` | type, identifiants.email, identifiants.telephone, employeId, profilIds, actif, dateExpiration, verrouilleJusqua, version | employeId → `dossiers_employes`, profilIds → `profils` | email unique sparse ; telephone unique sparse ; employeId unique sparse ; (actif, type) |
| `profils` | code, libellé, droits{}, droitsSensibles[] | — | code unique |
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
| `contrats_clients` | numero, clientId, siteIds[], dateDebut, dateFin, statut | client, sites | numero unique ; clientId ; siteIds |
| `migration_rapports` | migrationId, executeLe, comptages, rejets[] | — | (migrationId, executeLe) |
| `sms_envoyes` (factice) | numero, message, le, reference | — | le (TTL selon paramètre) |
| `purges` | le, categorie, nombre | — | le |

Champs ajoutés aux collections conservées :
- `dossiers_employes` : `telephone` normalisé (index unique sparse), `affectations[].siteId`, `version`, `idsHistoriques` (ids des fiches `employes` / `employes_complet` fusionnées) ;
- `sites_clients` : `clientId`, `contratClientId`, `type`, `version` ; création de l'index unique `code` ;
- `categories_professionnelles` : versions datées (D7) ;
- `production_chimie_matieres_premieres` et `produits_phyto` : `articleId` ;
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
| `terrain_materiel` | `back:entities/terrain/Materiel.java` | **Conservée**, hors référentiel article (à confirmer) |
| `categories_professionnelles` | `back:entities/rh/CategorieProfessionnelle.java` | **Conservée**, versions datées ; RIB / IPRES / CSS déplacés vers l'employé (à confirmer, Q15) |
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
3. **Rejeu** : une migration se joue sur la copie anonymisée (§3.7) autant de fois que nécessaire, jusqu'à **0 cas non résolu**. En production, elle n'est jouée que lorsque le rapport à blanc est vide.
4. **Non destructive** : les collections sources sont **renommées** en `archive_*`, jamais supprimées dans le lot. Les champs remplacés (`siteAffecte` texte, `fournisseur` texte) sont conservés dans un `*Origine`.
5. **Vérification après migration** (test IT sur jeu de données plus script de contrôle en prod) : comptages source = cible + rejets ignorés ; aucune référence vers un id inexistant (`$lookup` de contrôle) ; unicité (index créés sans erreur) ; 20 fiches comparées champ par champ, jointes au compte rendu.
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

**Comptes ↔ dossiers** : `utilisateur.email` normalisé = `dossier.email` normalisé, avec exactement un dossier. Sinon le rapport conduit à une décision (`LIER` à un dossier, ou compte `TECHNIQUE` / `EXTERNE` sans employé). Le compte SUPERADMIN de `login` (`diarra.niang@…`, `DataLoader.seedSuperadmin`) est relié à son dossier s'il existe, sinon il devient un compte `INTERNE` à relier (**à confirmer**).

Vérification : tout compte INTERNE ou AGENT a un `employeId` valide ; chaque id de `employes_complet` et `employes` figure dans un `idsHistoriques` ou dans une décision `IGNORER` ; les `employeId` des pièces RH, des affectations terrain et des bons pointent tous vers un dossier.

### 3.2 Sites par nom côté RH

Sources : `DossierEmploye.affectations[].site` et `siteAffecte` (chaînes, `back:entities/rh/AffectationSite.java`, découpage par `back:util/SiteAffecteUtils.java`).

Algorithme :
- pour chaque libellé distinct, recherche dans `sites_clients` par `code` normalisé, puis par `nom` normalisé ;
- **un seul** site : `siteId` posé, libellé conservé dans `siteLibelleOrigine` ;
- zéro ou plusieurs sites : rejet `SITE_INCONNU` / `SITE_AMBIGU`, avec la liste des candidats et le nombre d'affectations concernées. Les décisions se prennent **par libellé** (un « Agence Plateau » est tranché une fois pour toutes ses occurrences).
- `siteAffecte` reste un champ **dérivé** de lecture, recalculé depuis les `siteId` (CLAUDE.md, mémoire « Affectations multi-sites »).

La confusion « agence / site client » (audit §4.6 Q7) est tranchée **avant** cette migration (Q6). Si une agence est un objet distinct, une liste `type=AGENCE` est créée d'abord.

Vérification : 0 affectation active sans `siteId` ; l'effectif par site avant (par libellé) et après (par id) est identique sur la copie.

### 3.3 Articles : 5 listes vers le catalogue unique

Ordre et algorithme :
1. **`stockv2_produits` → `articles`** : renommage, ids conservés. Aucune référence à réécrire (mouvements, bons, états, historique de coût).
2. **`produits`** (legacy, sans écran) : rapprochement par `codeProduit` = `articles.code` (normalisé), sinon par libellé normalisé. Trouvé : lien historique seulement. Non trouvé : rejet `ARTICLE_LEGACY_ORPHELIN`, puis décision `CREER` (article inactif) ou `IGNORER`. Ensuite, archivage.
3. **`production_chimie_matieres_premieres`** :
   - par `code`, puis par libellé normalisé, contre `articles` ;
   - trouvé avec unité identique : `articleId` posé ;
   - trouvé avec unité différente : rejet `UNITE_DIVERGENTE` (kg contre L, sans densité, voir audit F071) ;
   - non trouvé : **création** de l'article (famille matière première, **à confirmer** Q12), `articleId` posé ;
   - le code ou le libellé d'un autre article déjà pris donne un rejet `CONFLIT_CODE`.
   - Les références des formulations, OF et mouvements chimie (`matierePremiereId`) **ne changent pas**.
4. **`produits_phyto`** : même algorithme sur `nomCommercial`, création si absent.
5. `terrain_materiel` : pas de migration (D8).

Vérification : toute matière première et tout produit phyto actifs ont un `articleId` existant ; aucun code d'article en double ; somme des `quantiteEnStock` chimie inchangée (non migrée) ; valeur du stock Stock v2 inchangée (`ValeurStockService` avant/après sur la copie).

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

- **Grille** : chaque `categories_professionnelles` reçoit une version datée `dateEffet = 01/01/2000` portant les montants actuels (D7). Si Q15 est validée, RIB / IPRES / CSS sont recopiés de la grille vers **chaque employé** rattaché par `categorieCode`, uniquement si le champ de l'employé est vide. Un conflit (valeurs différentes déjà présentes) est rejeté.
- **Contrats clients** : aucune source dans le code. Création manuelle ou par import (D13) après validation de Q11.

### 3.6 Fichiers en `byte[]`

Pour chacune des 13 entités, une ChangeUnit en **deux phases** :
1. **Copie** : par lots de 100 documents, le `byte[]` est écrit dans GridFS, puis la métadonnée `fichiers` avec son sha256, et la référence `fichierId` posée sur le document. Le `byte[]` est **conservé**. Reprise possible : un document qui a déjà `fichierId` est sauté.
2. **Vérification puis purge** (ChangeUnit distincte, jouée après une période d'observation de 2 semaines en prod, **à confirmer**) : on relit GridFS, on compare le sha256 au `byte[]`, puis `$unset`. Toute divergence est un rejet et le champ est conservé.

Vérification : nombre de `byte[]` non vides en phase 1 = nombre de `fichiers` créés ; 0 divergence de sha256 ; la taille moyenne des documents baisse (statistiques `collStats` jointes).

### 3.7 Copie anonymisée de la production

Préalable : accès au VPS (Q16). Procédure (`ops/copie-anonymisee/`, scripts versionnés, aucune donnée versionnée) :

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
  - `docker-compose up` démarre un replica set initialisé.
- **Taille** : M.
- **Bloquée par** : Q16 (accès VPS) pour la mise en production, pas pour le développement.

### 1b — Pièces : numérotation, annulation tracée, fin des suppressions, audit, outbox

- **Objectif** : D4, D5, D6 (journal d'audit) ; registre minimal des pièces.
- **Lignes** : F002, F003, F004 (et base de F009).
- **Dépendances** : 1a.
- **Critères d'acceptation** :
  - `NumerotationIT` : 100 validations concurrentes donnent 100 numéros consécutifs sans trou ; validation en échec, numéro non consommé ; changement d'exercice, repart à 1 ;
  - `RoutesSuppressionIT` : 0 route DELETE hors liste blanche ;
  - test ArchUnit des appels `delete*` ;
  - bons de stock (premier type porté) : annulation d'un bon EFFECTIF qui génère les mouvements inverses par événement, sans suppression ;
  - `OutboxIT` : événement écrit seulement si la transaction est validée ; consommateur rejoué 2 fois, effet une seule fois ; échec répété, `EN_ERREUR` après 10 tentatives ;
  - `JournalAuditIT` : chaque transition de pièce a sa ligne, champs sensibles masqués.
- **Taille** : L.
- **Bloquée par** : Q7 (exercice, continuité de la numérotation).

### 1c — Paramètres datés et listes de choix

- **Objectif** : D7, et le volet « valeurs en dur » de D16.
- **Lignes** : F006, F021 (paramétrage des valeurs propres à l'entreprise).
- **Dépendances** : 1a ; 1b (audit des saisies).
- **Critères d'acceptation** :
  - `ParametreServiceTest` : lecture à date (avant, à et après la date d'effet), version passée immuable (409) ;
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
  - `VerrouillageConnexionIT` : 5 échecs, verrouillage, journal ;
  - `RafraichissementJetonIT` ;
  - les tests du lot 0 (`SecuriteITBase` et suivants) restent verts ;
  - front : écrans Profils et Comptes avec specs ; sidebar et `module.guard.ts` alimentés par `/api/moi/droits`.
- **Taille** : L.
- **Bloquée par** : Q1 (SUPERADMIN = Direction), Q2, Q14 (droits sensibles, 2FA).

### 1d — Référentiels employé et site (+ migration)

- **Objectif** : D8 (employé, site), §3.1, §3.2 ; suppression du module `EmployeComplet` et de la synchronisation `employes`.
- **Lignes** : F001 (employé, site), F023.
- **Dépendances** : 1g.
- **Critères d'acceptation** :
  - migrations §3.1 et §3.2 jouées sur la copie anonymisée avec 0 cas non résolu, rapport joint ;
  - `ReferentielEmployeIT` : un employé créé dans RH est lisible par Stock, Terrain et Chimie avec le masquage selon le profil ;
  - `ReferentielSiteIT` : un site créé est sélectionnable dans RH (affectation par `siteId`), Stock et Terrain ;
  - index uniques `matricule`, `agentId`, `telephone`, `sites_clients.code` créés ;
  - `/api/employe-complet` absent (`RoutesPubliquesIT` et test de route).
- **Taille** : L.
- **Bloquée par** : Q5, Q6 (agence / site / local, magasins), Q10 (propriétaire du site).

### 1e — Référentiel article unique (+ migration, stock chimie compris pour le catalogue)

- **Objectif** : D8 (article), §3.3.
- **Lignes** : F001 (article), F023.
- **Dépendances** : 1d (sites pour les états de stock), 1c (listes famille, nature, unité).
- **Critères d'acceptation** :
  - migration §3.3 avec 0 cas non résolu sur la copie ;
  - `ReferentielArticleIT` : un article créé une fois est visible dans Stock, dans la fiche matière première (Chimie) et dans les produits phyto ;
  - la formulation lit code, libellé et unité depuis l'article ;
  - valeur de stock et soldes chimie inchangés (tests de comparaison) ;
  - collection `produits` archivée, `ProduitService` supprimé.
- **Taille** : L.
- **Bloquée par** : Q12 (familles), Q5.

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
- **Bloquée par** : Q11 (contrat), Q15 (RIB / IPRES / CSS).

### 1i — Fichiers et pièces jointes

- **Objectif** : D12, §3.6 (phase 1 ; la phase 2 est différée).
- **Lignes** : F007.
- **Dépendances** : 1g (contrôle d'accès par propriétaire), 1d-1f (entités à migrer stabilisées).
- **Critères d'acceptation** :
  - `StockageFichiersIT` : dépôt, lecture, sha256 ;
  - accès refusé sans droit sur le propriétaire (403) ;
  - migration §3.6 phase 1 avec 0 divergence ;
  - les routes de flux existantes renvoient le même contenu qu'avant (test octet par octet sur la copie) ;
  - composant `app-pieces-jointes` avec spec, intégré à la fiche employé et à l'article.
- **Taille** : M.
- **Bloquée par** : aucune (Tika : décision technique).

### 1k — Notifications persistées et interface SMS

- **Objectif** : D14, et l'interface plus le fournisseur factice de D11.
- **Lignes** : F008.
- **Dépendances** : 1b (outbox), 1g (destinataires = comptes).
- **Critères d'acceptation** :
  - `NotificationsIT` : événement, notification persistée, poussée STOMP au bon principal (y compris Terrain, corrigé) ;
  - lu / non lu ;
  - panne SMTP : création d'un bon réussie et e-mail réessayé ;
  - `FournisseurSmsFacticeIT` : le SMS apparaît dans `sms_envoyes` ;
  - démarrage prod refusé en `factice` sans drapeau ;
  - cloche du header avec spec ; abonnements front morts retirés.
- **Taille** : M.
- **Bloquée par** : Q3 pour le vrai fournisseur, pas pour la livraison.

### 1h — Moteur de circuits de validation

- **Objectif** : D10, avec les congés puis les bons de stock.
- **Lignes** : F005.
- **Dépendances** : 1b, 1g, 1k.
- **Critères d'acceptation** :
  - `MoteurCircuitTest` : auteur exclu (y compris par délégation), Direction en dernier exigée au-delà du seuil, délégation datée active ou expirée, circuit absent = 422 ;
  - tests existants `CongeWorkflowService*` et `BonWorkflowServiceIT` verts **sans modification des assertions de contrat** ;
  - validation finale qui émet `PieceValidee` et notifie l'auteur ;
  - `GET /api/validations/a-traiter` agrégé congés + bons ;
  - écran de paramétrage des circuits et délégations avec spec.
- **Taille** : L.
- **Bloquée par** : Q1, Q13 (étapes et seuils réels).

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
  - `ConnexionAgentIT` : 5 échecs, verrouillage ; code oublié avec réponse identique ; code de pointage refusé comme code de connexion ;
  - front : écran de connexion agent et Mon espace (profil, planning, soldes, bulletins validés) avec specs.
- **Taille** : M.
- **Bloquée par** : Q9 (code distinct), Q3 (en production).

### 1l — Données personnelles, sauvegardes

- **Objectif** : D15.
- **Lignes** : F019, F020.
- **Dépendances** : 1c (durées en paramètres), 1i (fichiers à purger).
- **Critères d'acceptation** :
  - `PurgeServiceIT` : mode à blanc sans écriture ; mode réel qui anonymise un ex-employé au-delà de la durée, garde ses bulletins, trace dans `purges` ;
  - scripts `ops/sauvegarde/` testés en local (dump, chiffrement, restauration dans un conteneur, contrôles) ;
  - procédure `docs/exploitation/sauvegarde.md`.
- **Taille** : M.
- **Bloquée par** : Q8 (durées), Q16 (stockage hors serveur, accès VPS).

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

## 5. Questions bloquantes pour la Direction

| # | Question | Pourquoi le code ne tranche pas | Bloque |
|---|---|---|---|
| Q1 | Le SUPERADMIN est-il la « Direction » du CDC ? Qui d'autre a le profil Direction ? | SUPERADMIN n'existe que dans `login` (`DataLoader.seedSuperadmin`), absent de `RoleAdmin` ; « DG » existe dans `NiveauValidationConge` | 1g, 1h |
| Q2 | Qui administre « Paramètres et accès » (comptes, profils, circuits, seuils) ? Une seule personne ou un binôme (saisie / validation) ? | Aujourd'hui, seul le SUPERADMIN gère les comptes (lot 0 C2) | 1c, 1g |
| Q3 | Quel fournisseur SMS (Orange, opérateur agrégateur…), avec quel budget mensuel, quel nom d'expéditeur et quel engagement de délivrabilité ? | Aucune dépendance SMS dans le code | 1k (vrai fournisseur), 1m (prod) |
| Q4 | Multi-entreprises : PointIC servira-t-il d'autres sociétés ? Qui est propriétaire du logiciel ? | Aucun `tenantId` ; valeurs Cleanic en dur | 1c (D16) |
| Q5 | Stock : plusieurs magasins avec transferts, ou entrepôt unique ? Un magasin est-il un « site » ? | `StockEntrepotUniqueMigrationRunner` a ramené les soldes à un entrepôt unique (`siteId = null`) | 1d, 1e |
| Q6 | Agence, site et local : quelles définitions ? Un QR par site ou par local ? | Le code confond agence et site client (`SiteClient`, audit §4.6 Q7) | 1d |
| Q7 | Exercice = année civile ? Les nouveaux numéros repartent-ils à 1 ou continuent-ils l'existant ? | Séquences actuelles journalières, sauf `CH` annuel | 1b |
| Q8 | Durées de conservation par catégorie (ex-employés, candidats, GPS, journaux) : avec quel conseil juridique ? | Aucune règle dans le code | 1l |
| Q9 | Le code de connexion de l'agent est-il distinct du code de pointage ? (Recommandé : oui.) L'agent peut-il le changer ? | `agentId` à 4 chiffres saisi sur un kiosque partagé | 1m |
| Q10 | Qui crée et possède la fiche site : le Commercial (cible du CDC) ou l'Exploitation, tant que le Commercial n'existe pas ? | Site créé par l'Exploitation aujourd'hui (F051) | 1d |
| Q11 | Le « contrat » des fiches minimales est-il le contrat **client** ? Quels champs minimaux ? | Seul le contrat de travail existe (`entities/rh/Contrat.java`) | 1f |
| Q12 | Correspondance entre les 6 familles d'articles du CDC et les 4 `TypeProduit` actuels (PRODUIT_FINI, MATIERE_PREMIERE, CONSOMMABLE, EPI) ? | Écart signalé en F072 | 1e |
| Q13 | Étapes et seuils réels des circuits (congés, bons d'entrée et de sortie), et seuil au-delà duquel la Direction valide ? | Circuits figés aujourd'hui | 1h |
| Q14 | Liste des droits sensibles (salaires, prix d'achat, CA, export, données personnelles…) et double authentification : pour qui ? | Aucun droit sensible aujourd'hui (F011) | 1g |
| Q15 | RIB, n° IPRES et CSS portés par l'employé (et non la grille) : confirmé ? | Copiés depuis la grille vers le bulletin (`CalculPaieService.orchestrer`) | 1f |
| Q16 | Accès au VPS, et stockage hors serveur pour les sauvegardes (où, chiffrement, qui détient la clé) ? | Hébergeur non trouvé ; proxy de prod non visible (lot 0 C5) | 1a (prod), 1l, §3.7 |

**Questions pour vous** (pas pour la Direction) :
- Q-A : périmètre F009 / F014 (§0.3) : un registre serveur minimal et un Mon espace en consultation limité au lot 1, cela vous convient-il ?
- Q-B : ce document est placé dans le dépôt backend (`docs/refonte/`), l'audit restant dans le dépôt front. Faut-il le déplacer ou le dupliquer ?
- Q-C : Mongock (nouvelle dépendance) ou l'exécuteur maison d'emblée ?

---

## 6. Résumé

1. Le lot 1 commence par un socle technique (1a) : replica set, `MongoTransactionManager`, `@Version`, soldes atomiques, Mongock, index versionnés.
2. Une pièce commune (1b) reçoit son numéro par type et par exercice **à la validation, dans la transaction**, donc sans trou ; les 39 DELETE deviennent des annulations tracées.
3. Les effets passent par une outbox Mongo avec des consommateurs idempotents : pas d'appel croisé entre modules, base des futures écritures comptables.
4. Toute règle chiffrée devient un paramètre daté lu « à la date du fait », et le front ne garde plus aucun seuil.
5. Les droits passent à 5 niveaux par sous-rubrique, plus des droits sensibles, avec masquage central par `@Sensible`. Migration testée « décisions identiques avant et après ».
6. Sept référentiels ont chacun un propriétaire. `stockv2_produits` devient `articles` (ids conservés), la chimie et le phyto s'y rattachent, et le solde chimie reste provisoirement dans son module.
7. Chaque fusion (employés, sites par nom, articles, tiers en texte libre, fichiers) produit un rapport de rejet et se pilote par fichier de décisions, sans jamais fusionner en silence.
8. Les migrations se répètent sur une copie de production pseudonymisée de façon déterministe, sur un poste unique sécurisé.
9. 13 sous-branches, avec un ordre corrigé : droits avant référentiels, notifications avant circuits, connexion agent après l'import. Le test de recette du lot est automatisé (`RecetteLot1IT`).
10. 16 questions pour la Direction (dont Q1 Direction, Q3 SMS, Q5/Q6 magasins et sites, Q16 accès VPS) et 3 pour vous. **J'attends votre validation avant de créer 1a.**
