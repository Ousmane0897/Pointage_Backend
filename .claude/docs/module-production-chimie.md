# Module Production Chimie (5.1) — détails d'implémentation

> Document chargé à la demande quand une tâche touche au module Production Chimie. Le résumé court et les pointeurs vivent dans `CLAUDE.md` à la racine.

## Module Production Chimie (5.1) — ✅ Backend terminé

Backend du module **Exploitation v2 / Production Chimie** mergé dans `main` (branche `feature/exploitation-v2-production-chimie` supprimée après merge). Module **autonome** : ne partage **aucune collection** avec le module Stock historique (les MP chimie vivent dans `production_chimie_matieres_premieres`, séparées de `produits`). Lecture seule possible sur le module RH si jamais on veut résoudre le nom d'un opérateur d'OF (non utilisé actuellement — le front envoie déjà `operateurResponsableNom` dénormalisé).

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

### Codes d'erreur HTTP spécifiques au module

`GlobalExceptionHandler` les renvoie automatiquement :

- `StockChimieInsuffisantException` → **409 STOCK_CHIMIE_INSUFFISANT**
- `TransitionOfInterditeException` → **409 TRANSITION_OF_INTERDITE**
- `ControleQualiteInvalideException` → **400 CONTROLE_QUALITE_INVALIDE**
- `EntiteReferenceeException` → **409 ENTITE_REFERENCEE**
- `ProductionException` (fallback) → **409 PRODUCTION_ERROR**

**Bean Validation** (`@Valid`, `@NotBlank`, etc.) : `MethodArgumentNotValidException` → 400 `VALIDATION_ERROR` avec body `{error, message, errors: [{field, message}]}` — handler ajouté en même temps que le module 5.1, applicable à tous les nouveaux modules.

### Dette technique connue (à traiter dans des lots dédiés)

- **RBAC fin différé** : aucun contrôle backend des sous-permissions `productionChimie.formulations`, `.ordresFabrication`, `.lots`, etc. La sidebar Angular filtre déjà l'UI, mais un appel direct avec un JWT valide (même sans la permission) passera. Approche recommandée si on veut combler : annotation custom `@RequireModule("productionChimie.formulations")` + Aspect AOP qui décode le JWT (besoin d'ajouter `JwtUtil.extractModules(token)` qui n'existe pas encore — `generateToken` embarque `claim("modules", modules)` mais aucune méthode publique ne le décode).
- **Réconciliation `quantiteEnStock` ↔ somme des mouvements** : pas d'endpoint admin de check/reconcile. Si une réception/ajustement échoue entre l'insert du mouvement et l'update de la MP, on a une dérive. À prévoir si pertinent en prod.
- **Photos CQ** : indexées par position dans la liste (`/controles/{id}/photos/{index}`). Suppression d'une photo individuelle non implémentée — il faut DELETE + re-POST le contrôle.

### Conventions (rappels spécifiques au module)

- Volumes normalisés en litres dans le tableau de bord (`L` = 1, `ML` = 0.001, autres unités prises brutes — la majorité des produits chimie sont liquides).
- `dureePeremptionJours` est en **jours** (vs `dureeEssaiMois` en mois côté RH — attention à ne pas mélanger).
- Le n° d'OF utilise `LocalDate.now()` côté serveur ; si plusieurs OF sont créés à cheval sur minuit UTC vs `Africa/Dakar`, le compteur peut « rebaser » d'un jour à l'autre — c'est attendu (clé `_id` = date).
