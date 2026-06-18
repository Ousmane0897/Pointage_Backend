# Module Stock v2 (7.3 + 7.4) — « Stocks & Approvisionnement » / « Contrôle des mouvements »

Backend complet des sous-modules 7.3 (catalogue/mouvements/inventaires/synthèse/appro/dashboard) et 7.4 (bons à workflow + catégorisation + plafonds + dotation + consommation). Module **autonome**, collections `stockv2_*`, 16 contrôleurs sous `/api/stock/`. Frontend Angular **figé** : tout écart de contrat (chemin, méthode, query param, nom de champ JSON, enum, code HTTP) casse l'app. Voir CLAUDE.md § « Module Stock v2 » pour la vue d'ensemble. La section 7.4 est documentée en bas de ce fichier.

## Conventions transverses du module

- **Pagination** : toute liste paginée renvoie `util/PageResponse<T>` = `{ content: [...], totalElements: <number> }`. Query communs `page` (def 0), `size` (def 20).
- **Dates** : `LocalDate` → `@JsonFormat("yyyy-MM-dd")` ; `LocalDateTime` ISO natif ; `mois` = `String` `yyyy-MM`.
- **Montants** FCFA en `long` (entiers) ; **quantités** en `double`.
- **Utilisateur** créateur déduit du JWT (`CurrentUserProvider`), jamais envoyé par le client.
- **Champs dénormalisés** remplis côté serveur en lecture : `produitCode`, `produitLibelle`, `unite`, `categorieLibelle`, `siteSourceNom`, `siteDestinationNom`, `siteNom`, `quantiteTotale`, URLs binaires.
- **Sites** consommés en lecture seule depuis Terrain (`ReferentielSiteService` → `sites_clients`). `siteSourceId`/`siteDestinationId`/`siteId` stockent un `SiteClient.id`.

## Enums (`Enum/stockv2`, valeurs EXACTES en MAJUSCULES)

| Enum | Valeurs |
| --- | --- |
| `TypeProduit` | `PRODUIT_FINI, MATIERE_PREMIERE, CONSOMMABLE, EPI, MATERIEL` |
| `UniteStock` | `KG, G, L, ML, PIECE, M2, M3, METRE, CARTON, LOT` |
| `TypeMouvement` | `ENTREE, SORTIE, TRANSFERT` |
| `MotifMouvement` | `ACHAT, PRODUCTION, CONSOMMATION, VENTE, TRANSFERT, AJUSTEMENT, RETOUR, PERTE` |
| `StatutStock` | `RUPTURE` (qté ≤ 0), `CRITIQUE` (qté ≤ seuilAlerte), `OK` |
| `StatutInventaire` | `BROUILLON, COMPTAGE, VALIDATION, CLOTURE` |
| `PerimetreInventaire` | `TOUS, CATEGORIE, SELECTION` |

## Entités / collections / index

| Entité (`entities/stockv2`) | Collection | Index | Rôle |
| --- | --- | --- | --- |
| `ProduitStock` | `stockv2_produits` | `code` unique ; `categorieId`, `actif` | catalogue ; binaire photo + fiche technique inline ; `quantiteTotale` **non stockée** |
| `CategorieStock` | `stockv2_categories` | `parentId` | arborescence (`niveau` 0 = racine) |
| `MouvementStock` | `stockv2_mouvements` | `reference` unique ; `produitId`, `date`, `siteSourceId`, `siteDestinationId` | journal des mouvements |
| `StockParSite` | `stockv2_etats_stock` | **composé unique `(produitId, siteId)`** | **solde = source de vérité des quantités** ; `seuilAlerteOverride?` |
| `Inventaire` (+ `LigneInventaire` inline) | `stockv2_inventaires` | `reference` unique ; `statut`, `siteId`, `datePlanifiee` | workflow d'inventaire |
| `CompteurStock` | `stockv2_compteurs` | `_id` = `{PREFIXE}-yyyyMMdd` | séquences atomiques |

Solde consolidé d'un produit = somme des `StockParSite` sur tous les sites (bucket `siteId=null` inclus = stock initial d'import sans site).

## Endpoints détaillés

### 1. Catalogue — `/api/stock/produits` (`ProduitStockController`)
- `GET ?page&size&q&typeProduit&categorieId&fournisseur&sousSeuil&actif` → `PageResponse<ProduitDto>`. `q` cherche code/libellé/fournisseur. `sousSeuil=true` → `quantiteTotale ≤ seuilAlerte` (filtre + pagination en mémoire car dérivé).
- `GET /actifs` → `ProduitDto[]` (léger, `actif=true`).
- `GET /{id}` → `ProduitDto`.
- `POST` multipart : part `produit` (JSON), `photo` (jpeg/png/webp, opt), `ficheTechnique` (pdf, opt) → 201. **409** si `code` existe.
- `PUT /{id}` multipart + flags texte `supprimerPhoto`/`supprimerFicheTechnique`. **409** si `code` en conflit.
- `DELETE /{id}` → 204.
- `GET /{id}/photo`, `GET /{id}/fiche-technique` → binaire (protégé JWT).
- `POST /bulk` : import transactionnel all-or-nothing — voir § Transactionnalité.

### 2. Catégories — `/api/stock/categories` (`CategorieStockController`)
- `GET /racines` (parentId null), `GET /enfants?parentId`, `GET /` (plat), `GET /{id}` → `CategorieStockDto` avec `nbEnfants`/`nbProduits` calculés.
- `POST` / `PUT /{id}` (payload `{libelle, parentId?, description?}`) ; `niveau` = `parent.niveau + 1`.
- `DELETE /{id}` → 204, **409** (`EntiteReferenceeException`) si la catégorie a des enfants ou des produits.

### 3. Mouvements — `/api/stock/mouvements` (`MouvementStockController`)
- `GET ?page&size&q&produitId&type&motif&siteId&dateDebut&dateFin` → `PageResponse`, tri `date` desc. `siteId` = source OU destination.
- `GET /{id}`.
- `POST` (payload `{produitId, type, motif, quantite, siteSourceId?, siteDestinationId?, date, commentaire?}`) → 201. Règles : ENTREE → destination requise (pas de source) ; SORTIE → source requise ; TRANSFERT → les deux, différentes. Génère `reference`, applique l'impact sur `StockParSite`, **422** si stock insuffisant (SORTIE/TRANSFERT). Validation de site invalide → **400**.

### 4. État du stock — `/api/stock/etat-stock` (`EtatStockController`)
- `GET ?page&size&q&categorieId&typeProduit&siteId&statut&parSite` → `PageResponse<EtatStockDto>`. `parSite=true` → 1 ligne par couple produit/site ; sinon consolidé (`siteId`/`siteNom` absents). Calcule `statut`, `valeur = quantite × prixUnitaire`, `dateMaj`.
- `PUT /seuils` (payload `{produitId, siteId?, seuilAlerte}`) → seuil global produit, ou override couple produit/site si `siteId`. Renvoie la ligne recalculée.

### 5. Inventaires — `/api/stock/inventaires` (`InventaireController`)
Workflow `BROUILLON → COMPTAGE → VALIDATION → CLOTURE` :
- `GET ?page&size&q&statut&siteId&dateDebut&dateFin`, `GET /{id}`.
- `POST` (planif) → `BROUILLON`. Construit `lignes` selon `perimetre` (TOUS = tous produits ; CATEGORIE = `categorieId` ; SELECTION = `produitIds`). `qteTheorique` = 0 à ce stade.
- `PUT /{id}` / `DELETE /{id}` : **uniquement si BROUILLON** sinon **409**.
- `POST /{id}/comptage` : `BROUILLON→COMPTAGE`, **fige** `qteTheorique` = stock système courant (par site si `siteId`, sinon consolidé).
- `PUT /{id}/comptage` (payload `{lignes:[{produitId, qtePhysique, justification?}]}`) : enregistre, recalcule `ecart = qtePhysique − qteTheorique` ; reste COMPTAGE.
- `POST /{id}/validation` : `COMPTAGE→VALIDATION`, **422** si une ligne `|ecart| > seuilEcartJustification` sans justification.
- `POST /{id}/cloture` : `VALIDATION→CLOTURE`, applique les écarts via mouvements `AJUSTEMENT` (compensation manuelle), `dateCloture` renseignée.

### 6. Synthèse mensuelle — `/api/stock/synthese-mensuelle` (`SyntheseMensuelleController`)
- `GET ?mois=yyyy-MM&siteId&categorieId` → `SyntheseMensuelleDto`. **Calcul depuis l'historique des mouvements** : `stockInitial` = somme des impacts strictement avant le 1er du mois ; `entrees`/`sorties` = impacts du mois ; `stockFinal = initial + entrées − sorties` ; `valeurFinale = stockFinal × prixUnitaire`. En consolidé (`siteId` absent), un TRANSFERT est neutre.

### 7. Approvisionnement — `/api/stock/approvisionnement` (`ApprovisionnementController`)
- `GET /suggestions?nMois&siteId&categorieId&fournisseur` → `SuggestionApproDto[]`. `consommationMoyenne` = moyenne des SORTIES mensuelles sur `nMois` (défaut **3**) ; `consommationPrevisionnelle` = `consommationMoyenne` (horizon 1 mois) ; `besoin = seuilAlerte + consommationPrevisionnelle − stockActuel` ; ne renvoie que `besoin > 0` ; `quantiteSuggeree = ceil(besoin)` ; `montantEstime = quantiteSuggeree × prixUnitaire`. Pas d'endpoint PDF (généré côté client).

### 8. Tableau de bord — `/api/stock/tableau-bord` (`TableauBordStockController`)
- `GET ?dateDebut&dateFin&siteId&categorieId&moisDormance` → `RapportTableauBordStockDto`. KPIs (`valeurTotale`, `nbProduits`, `nbRupture`, `nbAlerte` = sous seuil hors rupture, `tauxRotationMoyen` = moyenne(sortiesPériode / stockActuel), `nbDormants`), `valeurParCategorie`, `evolutionValeur` (valeur de stock à la fin de chaque mois, reconstruite depuis l'historique), `topConsommations` (top 10 SORTIES), `produitsDormants` (sans mouvement depuis `moisDormance` mois, défaut **3**).

## Transactionnalité (MongoDB standalone, pas de transaction multi-doc)

- **`POST /bulk`** : (1) pré-validation complète de toutes les lignes — aucune écriture ; (2) si erreurs → réponse 422, 0 créé ; (3) sinon insertion (résout/crée catégories par `categorieLibelle`, crée produits, mouvement `ENTREE`/`AJUSTEMENT` initial si `stockInitial`) en traquant tout ce qui est créé ; (4) sur exception → **rollback total** (suppression produits/catégories/mouvements/soldes créés). Corps identique en 200/422 : `{ total, inserted, failed, insertedIds, errors:[{numeroLigne, lineNumber, code, field, message}] }`.
- **Clôture d'inventaire** : applique les écarts ligne par ligne ; sur échec à mi-parcours, inverse les ajustements déjà appliqués et supprime les mouvements créés.

## Codes d'erreur spécifiques

| Situation | Exception | HTTP |
| --- | --- | --- |
| Stock insuffisant (SORTIE/TRANSFERT), écart d'inventaire non justifié, transition de workflow invalide | `StockOperationException` | **422** `STOCK_OPERATION_ERROR` |
| Code produit déjà utilisé | `StockConflitException` | **409** `STOCK_CONFLICT` |
| Catégorie non vide à la suppression | `EntiteReferenceeException` | **409** `ENTITE_REFERENCEE` |
| Entité introuvable | `ResourceNotFoundException` | 404 `NOT_FOUND` |
| Validation métier (sites, périmètre, mois invalide…) | `IllegalArgumentException` | 400 `VALIDATION_ERROR` |
| Bulk all-or-nothing en échec | (corps métier) | **422** (même corps que 200) |

## Tests

- IT Testcontainers (`services/stockv2/*IT`, `@SpringBootTest` + `MongoTestContainer`) : `CategorieStockServiceIT`, `ProduitStockBulkServiceIT`, `MouvementStockServiceIT`, `EtatStockServiceIT`, `InventaireWorkflowServiceIT`.
- Slices (`controllers/stockv2`, `@WebMvcTest`) : `ProduitStockControllerTest` (bulk 200/422, create 201), `MouvementStockControllerTest`.
- Lancer : `./gradlew test --tests "*stockv2*"`.

## Dette / à faire

- Activation des sous-flags `modules.stock.*` par l'admin via le flux existant `/api/superadmin` (gating frontend).
- L'`evolutionValeur` du dashboard recalcule le cumul produit×mois en mémoire (O(mois × mouvements)) — acceptable au volume actuel, à surveiller si l'historique grossit.
- Smoke tests manuels Swagger/curl avec JWT (cycle mouvement → état → inventaire → clôture).

---

# Sous-module 7.4 — « Contrôle des mouvements »

Ajoute, **par-dessus 7.3**, un document **`Bon` multi-lignes porteur d'un workflow de validation**. Créé sur `feature/stock` (2026-06-18). Code dans les mêmes sous-packages `stockv2`.

## Principe central (ne pas dupliquer la notion de mouvement)

Le `MouvementStock` 7.3 reste l'**effet instantané** en stock. Un `Bon` (entrée ou sortie) porte le workflow. **Aucun mouvement n'affecte le stock tant que le bon n'est pas validé.** Le passage en **EFFECTIF** génère un `MouvementStock` 7.3 **par ligne** (`MouvementBonGenerator`), qui met à jour `StockParSite` via le mécanisme 7.3 existant (`StockBalanceService`).

`MouvementStock` a reçu 5 champs **optionnels** (rétro-compatibles, nuls pour l'historique) : `origine` (`"DIRECT"` = saisie 7.3 / `"BON"` = généré par un bon), `bonId`, `bonReference` (`BE-`/`BS-…`), `categorieEntree` (`TypeEntree`), `categorieSortie` (`TypeSortie`).

## Enums 7.4 (`Enum/stockv2`, valeurs EXACTES)

| Enum | Valeurs |
| --- | --- |
| `TypeEntree` | `ACHAT_FOURNISSEUR, RETOUR_PRODUCTION, TRANSFERT_INTER_SITES, REINTEGRATION` |
| `TypeSortie` | `DISTRIBUTION_AGENCE_SITE_CLIENT, DISTRIBUTION_CHANTIER, VENTE_PRODUIT, CONSOMMATION_INTERNE` |
| `TypeDestinataire` | `SITE, AGENT, CLIENT` |
| `StatutBon` | `BROUILLON, SOUMIS, VALIDE, EFFECTIF, REFUSE` |
| `ActionWorkflow` | `CREATION, MODIFICATION, SOUMISSION, VALIDATION, REFUS, EFFECTIF` |
| `SensBon` | `ENTREE, SORTIE` |
| `GranularitePlafond` | `PRODUIT, CATEGORIE` |
| `SensEcartDotation` | `SUR_CONSOMMATION, SOUS_CONSOMMATION, CONFORME` |
| `TypeRapportConsommation` | `PAR_SITE, PAR_PRODUIT, PAR_PERIODE` |

## Entités / collections 7.4

| Entité (`entities/stockv2`) | Collection | Notes |
| --- | --- | --- |
| `BonEntree` (+ `LigneBon`, `EntreeHistorique` inline) | `stockv2_bons_entree` | `reference` `BE-AAAAMMJJ-NNN` (unique sparse) ; `siteDestinationId`/`Nom` ; `historique[]` ; `montantTotal` |
| `BonSortie` (+ `LigneBon`, `DestinataireBon`, `EntreeHistorique` inline) | `stockv2_bons_sortie` | `reference` `BS-…` ; `siteSourceId`/`Nom` ; `destinataire` ; `motif` |
| `Plafond` | `stockv2_plafonds` | `siteId` (indexé), `granularite`, `cibleId` (produitId si PRODUIT / categorieId si CATEGORIE), `plafondMensuel` (`long`), `actif` |

`LigneBon` : le client n'envoie que `produitId` + `quantite` ; `produitCode`/`produitLibelle`/`unite`/`prixUnitaire`/`montant` sont **figés à la création/modification** (prix courant du produit, `montant = round(quantite × prixUnitaire)`). Compteurs réutilisent `CompteurStockService.genererReference("BE"|"BS")`.

## Dénormalisations & règles communes

- Demandeur/validateur : lus en **lecture seule** depuis `DossierEmploye` (`ReferentielEmployeStockService`). `demandeurId` optionnel → si absent, `demandeurNom` = utilisateur JWT. Validateur = utilisateur courant à la décision. « Responsable Achats » repéré par `poste`/`departement` (best-effort, cible WebSocket).
- Destinataire (sortie) : `SITE` → `siteId` requis (site lu depuis Terrain) ; `AGENT` → `agentId` requis (DossierEmploye) ; `CLIENT` → `clientNom` requis. Nom dénormalisé.
- `montant ligne = quantite × prixUnitaire` ; `montantTotal = Σ montants` ; FCFA en `long`.

## Endpoints 7.4

### Bons d'entrée — `/api/stock/bons-entree` (`BonEntreeController`)
- `GET ?page&size&q&statut&type&siteId&dateDebut&dateFin` → `PageResponse<BonEntreeDto>` (`q` = référence/fournisseur ; `siteId` = site destination ; tri date desc).
- `GET /{id}`.
- `POST` (`BonEntreePayload`) → **201**, statut `BROUILLON`, `reference` attribuée, `historique[CREATION]`.
- `PUT /{id}` → **409** si statut ≠ BROUILLON ; sinon re-dénormalise + `historique[MODIFICATION]`.
- `DELETE /{id}` → **204** ; **409** si ≠ BROUILLON.
- `POST /{id}/soumettre` → `BROUILLON→SOUMIS` (sinon **409**), `historique[SOUMISSION]`, WS `BON_SOUMIS`.
- `POST /{id}/valider` (body `{}` ou `{commentaire?}`) → `SOUMIS→VALIDE→EFFECTIF` (sinon **409**) : **génère les mouvements ENTREE** créditant le site destination, `historique[VALIDATION]`+`[EFFECTIF]`, WS `BON_VALIDE` puis `BON_EFFECTIF`.
- `POST /{id}/refuser` (body `{commentaire}` **requis** sinon **400**) → `SOUMIS→REFUSE`, `motifRefus`, `historique[REFUS]`, WS `BON_REFUSE`.

### Bons de sortie — `/api/stock/bons-sortie` (`BonSortieController`)
Mêmes routes/transitions ; `siteId` du GET = site source. `BonSortiePayload` porte `siteSourceId`, `destinataire`, `motif`. **La validation génère des mouvements SORTIE** débitant le site source et renvoie **422** si le stock est insuffisant (pré-vérification cumulée par produit AVANT toute écriture → rien n'est validé).

### Workflow (Kanban unifié) — `/api/stock/workflow/bons` (`WorkflowStockController`)
- `GET ?statut&sens&q` → `BonWorkflowDto[]` **non paginé** (entrées + sorties agrégées, tri date desc). `libelleType` = libellé du type ; `siteNom` = destination (entrée)/source (sortie) ; `destinataireNom` (sorties).

### Catégorisation — `/api/stock/categorisation/stats` (`CategorisationStockController`)
- `GET ?sens=ENTREE|SORTIE&dateDebut?&dateFin?` → `StatistiqueCategorieDto[]`. Agrège les **mouvements `origine=BON`** du sens donné, groupés par catégorie : `nombre`, `volume` (Σ quantités), `montant` (Σ valorisation au prix courant), `pourcentage` (volume/total×100). **Une entrée par code figé** (4 par sens, 0 si absent).

### Plafonds de dotation — `/api/stock/plafonds` (`PlafondController`)
- `GET ?page&size&q&siteId&granularite&actif` → `PageResponse<PlafondDto>` ; `GET /{id}` ; `POST`/`PUT /{id}` (`PlafondPayload`) ; `DELETE /{id}` → 204.
- `GET /consommation?mois=YYYY-MM (requis)&siteId?` → `ConsommationPlafondDto[]` (jauges) : `consomme` = Σ sorties effectives du mois pour la cible (produit, ou produits de la catégorie) sur le site, `pourcentage = consomme/plafond×100`, `depassement` booléen. **Au dépassement → notification WS** (`/topic/stock-validations`).

### Dotation prévue vs réelle — `/api/stock/dotation/comparatif` (`DotationController`)
- `GET ?mois=YYYY-MM (requis)&siteId?&produitId?` → `ComparatifDotationDto`. Lignes par (site, produit) : `prevu` = plafond mensuel **PRODUIT**, `reel` = sorties effectives, `ecart = reel−prevu`, `pourcentageEcart` (0 si prevu=0), `sens` (`reel>prevu`→SUR_CONSOMMATION, `<`→SOUS_CONSOMMATION, `=`→CONFORME). Union plafonds PRODUIT + produits consommés.

### Consommation — `/api/stock/consommation` (`ConsommationController`)
- `GET /par-destinataire?siteId?&produitId?&dateDebut?&dateFin?` → `ConsommationDestinataire[]`. **Agrège les `BonSortie` EFFECTIFS** (le destinataire n'existe que sur le bon, pas sur `MouvementStock`) : totaux, `nbSorties`, `evolution[]` mensuelle, `lignes[]` par produit.
- `GET /rapport?type=PAR_SITE|PAR_PRODUIT|PAR_PERIODE (requis)&dateDebut (requis)&dateFin (requis)&siteId?&produitId?&categorieId?` → `RapportConsommation`. Sur les **mouvements SORTIE effectifs** (directs + bons). `cle`/`libelle` selon l'axe ; `coutMoyenParMouvement = montantTotal/nbMouvementsTotal`.

## WebSocket (`StockNotificationService`)

`NotificationStockDto { type: BON_SOUMIS|BON_VALIDE|BON_REFUSE|BON_EFFECTIF|INFO, sens: ENTREE|SORTIE, bonId, reference, titre, message, dateEmission: ISO }` publié sur :
- `/topic/stock-validations` (broadcast : chaque soumission + chaque décision + dépassement plafond) ;
- `/user/queue/notifications-stock` (ciblé sur l'email du « Responsable Achats » à la soumission). Tout échec WS est loggé sans casser la transaction métier.

## RBAC 7.4

8 sous-flags ajoutés à `SousModules/Stock` : `categorisation, bonsEntree, bonsSortie, workflowValidation, historiqueDestinataire, plafonds, dotation, rapportsConso`. Sérialisés dans le claim JWT `modules.stock`. **Gating UI frontend uniquement — aucun enforcement backend** (cohérent avec l'archi : pas de `@PreAuthorize`, routes en `.authenticated()`).

## Codes d'erreur 7.4

| Situation | Exception | HTTP |
| --- | --- | --- |
| Stock insuffisant à la validation d'un bon de sortie | `StockOperationException` | **422** `STOCK_OPERATION_ERROR` |
| **Transition de workflow invalide** (PUT/DELETE/soumettre/valider/refuser hors état autorisé) | `StockConflitException` | **409** `STOCK_CONFLICT` ⚠️ |
| Commentaire de refus manquant ; payload/destinataire/site/mois invalide | `IllegalArgumentException` | 400 `VALIDATION_ERROR` |
| Bon / produit / catégorie / employé introuvable | `ResourceNotFoundException` (produit/bon) / `IllegalArgumentException` (site/employé) | 404 / 400 |

> ⚠️ **Spécificité 7.4** : une transition de bon invalide renvoie **409** (contrat frontend figé), alors que 7.3 mappe les « transitions » d'inventaire en 422. Ne pas uniformiser.

## Transactionnalité 7.4 (Mongo standalone)

Génération des mouvements à EFFECTIF (`MouvementBonGenerator`) : pour une sortie, **pré-vérification de toutes les lignes** (cumulées par produit) avant toute écriture. Application ligne par ligne avec **compensation manuelle** — sur échec à mi-parcours, suppression des `MouvementStock` créés puis annulation des deltas de solde, et rethrow. Les mutations du bon (statut, historique) ne sont persistées qu'**après** succès de la génération.

## Tests 7.4

- IT (`services/stockv2/BonWorkflowServiceIT`, Testcontainers) : cycle complet bon d'entrée (→ mouvement `origine=BON` + crédit `StockParSite`), bon de sortie (→ débit), **422** stock insuffisant sans effet, **409** transitions, **400** refus sans commentaire.
- Slices (`controllers/stockv2`) : `BonEntreeControllerTest` (201/422/409/400), `PlafondControllerTest` (201 + consommation).
