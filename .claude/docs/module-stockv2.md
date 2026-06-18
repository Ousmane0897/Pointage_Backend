# Module Stock v2 (7.3) — « Stocks & Approvisionnement »

Backend complet du sous-module 7.3. Module **autonome**, collections `stockv2_*`, 8 contrôleurs sous `/api/stock/`. Frontend Angular **figé** : tout écart de contrat (chemin, méthode, query param, nom de champ JSON, enum, code HTTP) casse l'app. Voir CLAUDE.md § « Module Stock v2 (7.3) » pour la vue d'ensemble.

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
