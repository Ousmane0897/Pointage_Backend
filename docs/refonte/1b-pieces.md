# Lot 1 — Sous-branche 1b : pièces, outbox, audit, registre

Branche `refonte/v3-1b-pieces` (depuis `refonte/v3`), backend. Conception : `docs/refonte/lot-1-conception.md`, décisions D4, D5, D6 (journal d'audit seulement ; le journal des accès est en 1g), §4 « 1b ». Lignes F002, F003, F004 (base de F009).

### Résumé

- **Numérotation unique** (D4) : `NumerotationService`, collection `compteurs_pieces`, numéro `<TYPE>-<EXERCICE>-<NNNN>` pris **dans la transaction de validation**, ni trou ni doublon. Exercice = année civile à Dakar.
- **Outbox** (D5) : `evenements_domaine` écrit avec la pièce, dispatcher toutes les 2 s, consommateurs idempotents, nouvel essai exponentiel, `EN_ERREUR` après 10 tentatives, supervision super-admin.
- **Journal d'audit** (D6) : `journal_audit`, une ligne par transition, écrite dans la transaction, différentiel champ par champ, champs `@Sensible` masqués.
- **Registre des pièces** : `registre_pieces`, alimenté par événement uniquement.
- **Bons de stock et inventaires portés** : numéro à la validation (bons) ou à la clôture (inventaires), plus de suppression. Un brouillon s'**abandonne**, une pièce validée s'**annule** (motif tracé) ; les mouvements inverses sont produits par événement.
- **Garde-fous** : `RoutesSuppressionIT` (aucune route DELETE hors liste blanche), `SuppressionsInterditesArchTest` (aucun appel de suppression hors liste blanche, règle 3 des événements).
- `SuppressionDefinitiveService` et `ExecuteurTransactionnel.apresCommit` **supprimés** ; la compensation manuelle de la clôture d'inventaire est retirée (D1.6).
- Suite complète : **1 106 tests, 0 échec** (1 062 en fin de 1a).

---

## 1. Numérotation (D4)

- `Enum/pieces/TypePiece` : codes du CDC §3 (`DEV`, `FAC`, `BS`, `BE`, `OF`, `LOT`, `DD`, `BC`, `BCA`, `CHQ`) et codes proposés (`CNG`, `INV`, `MVT`, `BUL`, `DSO`, `SAN`, `IMP`). Portés en 1b : `BE`, `BS`, `INV`.
- `entities/pieces/PieceBase`, incorporé sous `piece` : `type`, `numero`, `exercice`, `compteur`, `creePar/creeLe`, `soumisePar/soumiseLe`, `valideePar/valideeLe` (`AuteurInstantane` : identifiant + **instantané** du nom), `annulation {par, le, motif, pieceCorrectiveId}`, `abandon {par, le}`. Interface `Piece` : `statutPiece()`, `siteIdPiece()`, `montantPiece()`, `libelleNumero()` (`BROUILLON-<6 derniers caractères de l'id>` tant qu'il n'y a pas de numéro).
- `services/pieces/NumerotationService.attribuer(type)` : refuse un appel hors transaction ; `findAndModify $inc` sur `_id = "BE-2026"` ; format `%s-%d-%04d`, qui s'allonge au-delà de 9999. `numeroter(piece)` ne fait rien si la pièce a déjà un numéro : **pas de renumérotation de l'historique** (une référence `BE-20260617-001` reste telle quelle, même sur un ancien brouillon validé après 1b).
- ChangeUnit `0106` : crée `compteurs_pieces` avant le trafic (deux premières validations simultanées ne se disputent pas la création de la collection).

## 2. Outbox (D5)

- `EvenementDomaine` (`evenements_domaine`) : champs de D5, plus `auteurNom`, `bailJusquA`, `traiteLe`. `EvenementPublisher.publier(...)` refuse un appel hors transaction.
- `DispatcherEvenements.traiterLot()` : réclamation par `findAndModify` (`A_TRAITER` échu, ou `EN_COURS` à bail expiré) → `EN_COURS` avec bail de 5 min, sûr en multi-instance sans ShedLock ; lots de 50 ; tri par `survenuLe`.
- `ConsommateurEvenement` (`nom`, `types`, `traiter`, `effetsExternes`) : trace `(consommateur, evenementId)` dans `evenements_consommes` (index unique) **dans la transaction du consommateur** ; un consommateur à effets externes (e-mail, WebSocket) tourne hors transaction, trace posée après succès (au moins une fois).
- Échec : délai 1, 2, 4… 32 min puis 1 h ; `EN_ERREUR` à la 10ᵉ tentative ; les consommateurs déjà passés ne sont pas rejoués.
- `PlanificationDispatcher` (`outbox.dispatcher.actif`, `delai-ms: 2000`) ; **désactivé dans les tests** (`src/test/resources/config/application.yml`), qui appellent `traiterLot()`.
- `CorrelationIdFilter` : en-tête `X-Correlation-Id` repris s'il est un UUID, sinon généré, renvoyé dans la réponse ; repris par l'audit et les événements, et posé pendant le traitement d'un événement.
- Supervision : `GET /api/exploitation/evenements?statut&page&size`, `POST /api/exploitation/evenements/{id}/relancer` (`@droits.estSuperAdmin()` ; 409 `TRANSITION_INTERDITE` si l'événement n'est pas `EN_ERREUR`).
- Consommateurs livrés : `registre-pieces`, `notifications-bons-stock` (WebSocket, mêmes types qu'avant + `BON_ANNULE`), `mail-creation-bon-stock` (e-mail de création, qui ne dépend plus du SMTP pendant la requête). Le consommateur `contre-passement-stock` a été retiré en B11 : le stock d'une pièce annulée est rétabli dans la transaction d'annulation (§5).
- ChangeUnit `0107` : 4 index, dont `consommateur_evenement_unique`.

## 3. Journal d'audit (D6)

- `JournalAudit.enregistrer(action, objetType, objetId, numero, avant, apres, motif)`, refusé hors transaction. Différentiel : les deux états rendus en JSON (donc sans `@JsonIgnore` ni `WRITE_ONLY`), aplatis en chemins pointés, listes comparées en bloc ; hors différentiel : `version`, `updatedAt`, `historique`.
- `security/Sensible` : champ écrit `***` avant comme après, objets imbriqués compris. **Aucun champ n'est encore annoté** dans les entités : les pièces portées n'en ont pas ; l'annotation des salaires, RIB et numéros sociaux se fera avec leurs modules (1d, 1f, paie).
- Auteur (`CurrentUserProvider`), IP de la requête, identifiant de corrélation.
- ChangeUnit `0108` : 3 index.

## 4. Registre des pièces

- `LignePieceRegistre` (`registre_pieces`) : `type`, `pieceId`, `numero`, `exercice`, `compteur`, `statut` commun, `siteId`, `montant`, `creeLe`, `valideeLe`, `amont[]`, `aval[]`, `etatAu`. Écrit **seulement** par `RegistrePiecesConsommateur` (et par les migrations 0012/0013 pour l'existant). Un événement plus ancien que l'état enregistré est ignoré.
- `TraceurPieces.tracer(...)` : une transition = une ligne d'audit + un événement au payload commun, dans la transaction de la transition.
- Aucune route en 1b (base de F009 au lot 2). ChangeUnit `0109` : 3 index, dont `(type, pieceId)` et `(type, numero)` uniques.

## 5. Bons de stock

- `StatutBon` + `ANNULE`, `ABANDONNE` ; `ActionWorkflow` + `ANNULATION`, `ABANDON`. Correspondance : BROUILLON → BROUILLON, SOUMIS → SOUMISE, VALIDE/EFFECTIF → VALIDEE, REFUSE → REFUSEE.
- Toutes les transitions (création, modification, soumission, validation, refus, reprise, abandon, annulation) passent par `ExecuteurTransactionnel` et tracent audit + événement.
- **`reference` est nulle en brouillon** ; elle reçoit le numéro à la validation (avant la génération des mouvements, qui la recopient). DTO et Kanban : `libelleNumero`, `annulation`, `abandon`.
- `POST /{id}/abandonner` remplace `DELETE /{id}` (BROUILLON ; créateur, contrôleur ou super-admin, comme avant).
- `POST /{id}/annuler {motif}` remplace `POST /{id}/suppression-definitive` : bon EFFECTIF, super-admin, motif ≥ 10 caractères (400), sinon 409 ; rejouer = 409.
  - L'annulation pose statut + bloc `annulation` et, **dans la même transaction** (B11, écart 5 tranché le 25/11), `ContrePassementStockService` crée un mouvement **inverse** par mouvement (`origine = "ANNULATION"`, `mouvementAnnuleId`, coût repris), rétablit les soldes et marque l'original `contrePasse = true`. Puis `PIECE_ANNULEE` est publié pour les effets secondaires (notification `BON_ANNULE`, registre). Rien n'est supprimé.
  - Bon d'entrée : débit **gardé** de l'entrepôt (`StockBalanceService.debiter`), précédé d'une pré-vérification cumulée par produit pour le message ; marchandise consommée ⇒ 422 `STOCK_INSUFFISANT`, rien n'est écrit (ni statut, ni audit, ni événement, ni mouvement). Une sortie concurrente sur le même stock : une seule des deux passe (`AnnulationConcurrenteIT`).
  - Bon de sortie : recrédit selon la répartition du débit, désormais enregistrée (`quantiteSurSite`, `quantiteSurConsolide`) ; mouvements antérieurs → entrepôt.
- `MouvementsEffectifs` : mouvements annulés **exclus des consommations et des coûts** (`AnalyseSupport`, catégorisation, consommation, dotation, plafonds, chantiers et valorisation des chantiers, approvisionnement, consommations du tableau de bord du stock). Les rejeux de quantités et de valeur gardent tout : l'aller et le retour s'annulent.
- Notifications WebSocket et e-mail de création : consommateurs d'événements ; `ExecuteurTransactionnel.apresCommit` supprimé.
- ChangeUnit `0012` (transactionnelle) : bloc `piece` sur les bons existants (référence = numéro, `exercice` nul), auteurs/dates repris, registre rempli, rapport (rejet signalé : bon EFFECTIF sans référence). Rien d'écrit s'il n'y a rien à porter.

## 6. Inventaires

- `StatutInventaire` + `ANNULE`, `ABANDONNE`. Correspondance : BROUILLON → BROUILLON, COMPTAGE/VALIDATION → SOUMISE, CLOTURE → VALIDEE.
- Numéro `INV-2026-0001` attribué **à la clôture** (référence nulle avant). La clôture passe en transaction : **compensation manuelle retirée** ; échec à mi-parcours ⇒ rien d'écrit, numéro non consommé.
- `POST /{id}/abandonner` remplace `DELETE /{id}` ; `POST /{id}/annuler {motif}` (CLOTURE, super-admin) remplace la suppression définitive ; contre-ajustements **dans la transaction d'annulation** (non gardés, comme la clôture ; même règle que les bons depuis B11) ; ajustements anciens retrouvés par leur commentaire.
- `SuppressionDefinitiveService` et son test supprimés ; `stockv2_suppressions` (entité `SuppressionStockLog`) conservée en lecture, plus écrite.
- ChangeUnit `0013` : comme `0012`, pour les inventaires.

## 7. Garde-fous

- `security/RoutesSuppressionIT` : aucune route DELETE hors liste blanche. **34 routes en sursis**, chacune avec sa sous-branche : 1d (3), 1e (6), 1f (1), 1g (1), 1c (1), 1i (3), et 19 routes des modules métier rattachées à leur lot (RH lot 6 : 12, Terrain lot 2 : 3, Stock lot 3 : 2, Chimie lot 8 : 2). La liste ne peut que diminuer (une route retirée doit en sortir). Les 6 routes retirées en 1b sont vérifiées absentes.
- `architecture/SuppressionsInterditesArchTest` (ArchUnit 1.3.0, dépendance de test ajoutée) :
  - aucun `delete*`, `remove*`, `findAndRemove`, `findAllAndRemove`, `dropCollection` sur un repository ou `MongoTemplate` hors liste blanche ; permanentes : `ResetPasswordService`, paquet `migrations` ; **28 classes en sursis** avec leur sous-branche (même découpage) ;
  - règle 3 : aucune classe de `services.<module>` ne dépend d'un service d'écriture d'un autre module (liste explicite par module : stock, RH, terrain, production chimie). **Aucune violation aujourd'hui.**

## 8. Écarts avec la conception (acceptés le 25/11, reportés dans `lot-1-conception.md`)

1. **Statut et version à la racine du document**, pas dans `PieceBase` : `@Version` n'est reconnu qu'à la racine, et chaque pièce garde son statut métier (contrat front, index). Le statut commun est donné par `statutPiece()`.
2. **Actions d'audit** `ABANDON` et `REPRISE` ajoutées à la liste de D6.
3. **Numéros de ChangeUnits** : `0106` crée `compteurs_pieces` ; les index sont `0107` (outbox), `0108` (audit), `0109` (registre) au lieu de `0106`–`0108`.
4. **Écran de supervision de l'outbox** : seules les routes existent ; l'écran front est reporté.
5. ~~**Annulation asynchrone**~~ — **résolu en B11** (décision du 25/11) : le stock est rétabli dans la transaction d'annulation, comme à la validation (exception D5 étendue). Appliqué aussi aux inventaires, pour une seule règle sur toutes les pièces de stock. Plus de course possible avec une sortie, plus d'`EN_ERREUR`.
6. **CUMP non restauré** à l'annulation d'un bon d'entrée (déjà le cas de l'ancienne suppression définitive).
7. **Registre** : deux événements de la même pièce dans la même milliseconde sont départagés par l'ordre de traitement.
8. **`AuditorAware` / `@CreatedBy`** (D6, pour les référentiels) non mis en place : sans référentiel porté en 1b, rien ne l'utiliserait ; à faire en 1d/1e.
9. La recherche des listes de bons et d'inventaires porte sur `reference` : un brouillon ne se retrouve pas par son libellé `BROUILLON-…`.

## 9. Contrats d'API modifiés (front à adapter, F1/F2)

| Avant | Après |
| --- | --- |
| `reference` toujours renseignée | **nulle en brouillon** ; afficher `libelleNumero` |
| `DELETE /api/stock/bons-entree/{id}`, `/bons-sortie/{id}`, `/inventaires/{id}` | `POST /{id}/abandonner` → DTO (statut `ABANDONNE`) |
| `POST /{id}/suppression-definitive {motif}` → 204 | `POST /{id}/annuler {motif}` → DTO (statut `ANNULE`, `annulation`) ; mouvements inverses et soldes **immédiats** ; 422 `STOCK_INSUFFISANT` si la marchandise d'un bon d'entrée a été consommée |
| `StatutBon` : 5 valeurs | + `ANNULE`, `ABANDONNE` (le Kanban unifié les renvoie : colonnes à filtrer côté front) |
| `StatutInventaire` : 4 valeurs | + `ANNULE`, `ABANDONNE` |
| `ActionWorkflow` | + `ANNULATION`, `ABANDON` |
| `MouvementStockDto` | + `contrePasse`, `mouvementAnnuleId` ; mouvements `origine = "ANNULATION"` |
| Notifications `/topic/stock-validations` | + `BON_ANNULE` ; émises ≈ 2 s après l'action |

Écrans à retester : listes et fiches de bons, Kanban, listes et saisie d'inventaires, PDF et export Excel des bons, liste des mouvements.

## 10. Bilan des tests

| Étape | Commit | Tests | Échecs |
| --- | --- | --- | --- |
| Fin de 1a | — | 1 062 | 0 |
| B2 numérotation | `4c40aa0` | 1 068 | 0 |
| B3 outbox | `2fb47ad` | 1 078 | 0 |
| B4 journal d'audit | `aa54262` | 1 082 | 0 |
| B5 registre | `e3f4f3c` | 1 084 | 0 |
| B6 bons portés | `da1f857` | 1 094 | 0 |
| B7 annulation des bons | `c750529` | 1 098 | 0 |
| B8 inventaires | `36121c2` | 1 103 | 0 |
| B9 garde-fous | `7b7675d` | 1 106 | 0 |
| B10 docs + tableau de bord du stock | `d9c0dc9` | 1 106 | 0 |
| B11 annulation dans la transaction | `33c7626` | 1 107 | 0 |
| B12 conception, routes rattachées | (ce commit) | 1 107 | 0 |

Critères d'acceptation de 1b : `NumerotationIT` (6), `RoutesSuppressionIT`, `SuppressionsInterditesArchTest` (2), `AnnulationBonIT` (6), `OutboxIT` (9), `JournalAuditIT` (4) et les transitions de pièces dans `BonWorkflowServiceIT` ; plus `RegistrePiecesIT`, `AnnulationInventaireIT`, `PiecesBonsExistantsMigrationIT`, `MigrationsIT`, `IndexesAttendusIT` (93 index), `ValidationEchecMiParcoursIT` (3).

Parcours réel par l'API : voir §10 bis.

## 10 bis. Parcours réel (`bootRun`, 25/11)

Replica set de dev du poste (service MongoDB local `rs0`, base `cleanic`, **vide au départ**), `bootRun` sur le commit `eab36e7`, port 8081 (le 8080 était occupé par un conteneur `springboot-backend` étranger au parcours). Super-admin créé par l'amorçage habituel (`SUPERADMIN_INITIAL_PASSWORD` passé au seul processus `bootRun`). Données de test préfixées `PARCOURS-1B`, conservées.

- **Démarrage** : 41 s ; Mongock joue toutes les ChangeUnits sur la base vide (24 entrées `mongockChangeLog`, aucun échec ; `migration_rapports` vide : rien à porter).
- **20 contrôles par l'API, 0 échec** :

| Étape | Résultat |
| --- | --- |
| BE créé | `reference` absente (champ nul non sérialisé), `libelleNumero` `BROUILLON-e2fe1b` |
| BE soumis, validé | **`BE-2026-0001`**, stock 0 → 20 |
| BS créé, soumis, validé | brouillon sans numéro, puis **`BS-2026-0001`**, stock 20 → 12 |
| BS annulé | motif de 5 caractères → 400 ; motif valable → `ANNULE`, bloc `annulation` ; stock **12 → 20 immédiatement** ; seconde annulation → 409 |
| BE consommé (BS-2026-0002 de 15 validé) | annulation → **422 `STOCK_INSUFFISANT`**, BE resté `EFFECTIF`, aucune ligne d'audit ni événement d'annulation |
| BE-2026-0002 (10) validé puis annulé | stock 15 → 5 dans la même requête |
| Brouillon de BE abandonné | `ABANDONNE`, bloc `abandon`, aucun numéro consommé ; `DELETE /{id}` → 405 |
| Inventaire (sélection, 1 produit) | brouillon sans numéro → comptage → validation → clôture **`INV-2026-0001`**, ajustement −2 (stock 5 → 3) |

- **En base** (lecture seule, `mongosh`) :
  - `compteurs_pieces` : `BE-2026` = 2, `BS-2026` = 2, `INV-2026` = 1 — aucun trou (le refus 422 et l'abandon n'ont rien consommé) ;
  - `stockv2_mouvements` : chaque mouvement d'origine `BON` d'une pièce annulée porte `contrePasse: true` et a son inverse `origine: "ANNULATION"` avec `mouvementAnnuleId` ; ajustement d'inventaire `origine: "INVENTAIRE"` rattaché par `inventaireReference` ; solde final 3 (entrepôt) ;
  - `journal_audit` : une ligne par transition (CREATION, SOUMISSION, VALIDATION, ANNULATION, ABANDON ; inventaire : CREATION, SOUMISSION au comptage, MODIFICATION ×2 pour la saisie et le passage en validation, VALIDATION à la clôture) ; la ligne de validation porte déjà le numéro ; différentiel par champs pointés (`piece.numero`, `piece.valideePar.nom`…) ;
  - `registre_pieces` : 6 lignes, statuts communs `VALIDEE` / `ANNULEE` / `ABANDONNEE`, numéros et montants justes ;
  - `evenements_domaine` : 22 événements, **tous `TRAITE`** quelques secondes après (aucun `A_TRAITER` ni `EN_ERREUR`) ; `GET /api/exploitation/evenements` en renvoie 22.
- **Observations** (sans correctif en 1b) :
  - le nom d'auteur instantané du super-admin d'amorçage est son e-mail : le compte n'existe que dans `login`, sans fiche `utilisateur` (nom, prénom). À reprendre avec les comptes (1g) ;
  - un `X-Correlation-Id` qui n'est pas un UUID est remplacé par un UUID neuf : voulu (`CorrelationId.depuisEnTete`, pas de texte libre dans les journaux) ;
  - au passage en `VALIDATION`, l'inventaire trace une `MODIFICATION` (le statut commun reste `SOUMISE`) : cohérent avec la correspondance des statuts.

## 11. Exploitation

Aucune opération serveur propre à 1b : les ChangeUnits `0012`, `0013`, `0106`–`0109` jouent au démarrage du basculement final. Résultats à renvoyer : annexe 1b de `docs/exploitation/basculement-v3.md`.

## 12. Ce qui reste

1. **Front F1/F2** (`pointage-agents`) : modèles, services, permissions, libellés, `libelleNumero`, boutons « Abandonner » / « Annuler le bon », dialogue d'annulation, bandeau du motif, colonnes du Kanban, inventaires.
2. ~~`bootRun` et parcours réel~~ : fait le 25/11 (§10 bis), 20 contrôles sur 20.
3. ~~Écart 5~~ : résolu en B11.
4. Routes et classes en sursis : à retirer dans leurs sous-branches ; les 19 routes des modules métier sont rattachées à leur lot (RH lot 6, Terrain lot 2, Stock lot 3, Chimie lot 8 ; `lot-1-conception.md` §4, sous 1b).
5. Écran de supervision de l'outbox.
