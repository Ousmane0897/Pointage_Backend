# Module Exploitation Terrain (5.2)

Backend Spring Boot du module **Exploitation Terrain** : gestion des agents de propreté / entretien phytosanitaire sur sites clients. Module **autonome** (collections dédiées), consommé par un frontend Angular 19 déjà écrit. Toutes les routes sont sous `/api/terrain/...` et exigent un JWT Bearer.

## Conventions

- **Pagination** : `?page=0&size=N` (0-based) → réponse `{ "content": [...], "totalElements": N }` (record `util/PageResponse<T>`).
- **Dates** : ISO 8601. `LocalDate` → `yyyy-MM-dd`, `LocalDateTime` → `yyyy-MM-ddTHH:mm:ss` (sérialisation ISO par défaut via `JavaTimeModule`). Les query params `dateDebut`/`dateFin` de période sont des `yyyy-MM-dd`.
- **Erreurs** : `TerrainExceptionHandler` (advice scopé `controllers.terrain`, priorité max) → corps `{ "message", "timestamp", "status" }`. 400 (validation / `IllegalArgumentException`), 404 (`ResourceNotFoundException`), 409 (`TerrainConflitException` : code dupliqué, conflit planning), 401 (sécurité).
- **Montants** : entiers FCFA (`long`). Coordonnées GPS, notes, doses : décimaux.
- **Fichiers** : stockés **inline en `byte[]`** (`@JsonIgnore`) + métadonnées + endpoint de streaming (même convention que Production Chimie ; pas de GridFS). L'URL de streaming est calculée par le mapper.
- **Unicité (index + 409)** : `sites_clients.code`, `terrain_materiel.code`, `produits_phyto.numeroHomologation`.
- **Compteurs quotidiens atomiques** (`findAndModify` upsert, collection `compteurs_terrain`) : `INT-AAAAMMJJ-NNN` (interventions), `PHYTO-AAAAMMJJ-NNN` (applications).
- **Audit** : `createdAt`/`updatedAt` remplis automatiquement par les services.

## Dépendance RH (lecture seule)

Les agents ne sont **pas** gérés par ce module. Chaque entité référence un agent via `employeId = DossierEmploye.id`. À l'écriture, `ReferentielRhService` valide que l'employé existe **et** appartient au département `Exploitation` (sinon 400), puis dénormalise `employeMatricule` + `employeNom` (= `prenom + " " + nom`). **Aucune écriture** dans `dossiers_employes`.

## Collections MongoDB

`sites_clients`, `terrain_affectations`, `terrain_pointages`, `terrain_alertes`, `terrain_parametres_escalade` (singleton), `terrain_interventions`, `terrain_grilles_evaluation`, `terrain_controles`, `terrain_materiel`, `terrain_evenements_materiel`, `terrain_maintenances_programmees`, `produits_phyto`, `applications_phyto`, `compteurs_terrain`.

## Endpoints

### 1. Sites clients — `/api/terrain/sites-clients`
| Méthode | Chemin | Notes |
| --- | --- | --- |
| GET | `` | paginé `q,ville,frequencePassage,actif` |
| GET | `/actifs` | liste légère (actif=true) |
| GET | `/{id}` | |
| POST / PUT | `` / `/{id}` | multipart : part `site` (JSON) + `cahierDesCharges` (PDF, optionnel) |
| DELETE | `/{id}` | 204 |
| GET | `/{id}/cahier-charges` | PDF binaire |

### 2. Planning — `/api/terrain/planning`
| Méthode | Chemin | Notes |
| --- | --- | --- |
| GET | `/affectations` | paginé `dateDebut,dateFin,employeId,siteId,statut` |
| GET | `/affectations/periode` | vue calendrier |
| GET | `/affectations/conflits` | `dateDebut,dateFin` → conflits groupés par agent |
| GET | `/affectations/{id}` | |
| POST / PUT | `/affectations[/{id}]` | 409 si chevauchement ; remplacement → initiale `REMPLACEE` |
| DELETE | `/affectations/{id}` | 204 |

### 3. Pointage GPS — `/api/terrain/pointages`
| Méthode | Chemin | Notes |
| --- | --- | --- |
| POST | `` | re-validation serveur (Haversine, statut, alerte HORS_ZONE) |
| GET | `/jour?employeId` | pointages du jour de l'agent |
| GET | `/aujourd-hui` | tous agents |
| GET | `/historique` | paginé |
| GET | `/{id}` | |

**Anti-spoof** : `distanceM` recalculé (Haversine, R=6 371 000 m), `datePointage` = horodatage serveur. Statut : `precisionM > 50` → `GPS_IMPRECIS` ; sinon `distanceM ≤ (rayonToleranceM ?? 100)` → `SUR_SITE` ; sinon `HORS_ZONE` (+ alerte temps réel). Positions/coordonnées manquantes → `GPS_INDISPONIBLE`.

### 4. Alertes & escalade — `/api/terrain/alertes`
| Méthode | Chemin | Notes |
| --- | --- | --- |
| GET | `` | paginé `dateDebut,dateFin,type,statut,niveauActuel,employeId,siteId` |
| GET | `/courantes` | OUVERTE/NOTIFIEE/ESCALADEE |
| GET | `/{id}` | |
| POST | `/{id}/traiter` | `{commentaire}` → TRAITEE |
| POST | `/{id}/justifier` | `{motif}` → JUSTIFIEE |
| POST | `/{id}/escalader` | `{motif?}` → niveau suivant |
| GET | `/recap-quotidien?date?` | récap du jour |
| GET / PUT | `/parametres` | `ParametresEscalade` (singleton) |

**Job planifié** (`DetectionAlertesJob`, `@Scheduled` toutes les 5 min) — périmètre Exploitation : `RETARD` (pas d'`ENTREE` après `delaiRetardMinutes`), `ABSENCE` (après `delaiAbsenceMinutes`), `DEPART_PREMATURE` (`SORTIE` avant `dateFin`). Escalade temporelle : `SUPERVISEUR → RESPONSABLE_OPERATIONNEL → DIRECTION_GENERALE`.

### 5. Fiches d'intervention — `/api/terrain/interventions`
| Méthode | Chemin | Notes |
| --- | --- | --- |
| GET | `` | paginé |
| GET | `/{id}` | |
| POST / PUT | `` / `/{id}` | multipart : part `fiche` (JSON + `photosMeta[]` + `photosConservees[]`) + `photos` (0..N fichiers) |
| DELETE | `/{id}` | 204 |
| GET | `/{id}/photos/{index}` | image binaire |
| GET | `/{id}/pdf` | **PDF serveur** (OpenPDF) |

`numero = INT-AAAAMMJJ-NNN`. `photos[]` final = (existantes ∈ `photosConservees`) + (nouvelles avec `moment`/`legende` de `photosMeta`). `duree` (min) = `dateFin − dateDebut`. `signatureClient.dataUrl` = PNG base64 stocké tel quel.

### 6. Contrôle qualité — `/api/terrain/controles-terrain`
| Méthode | Chemin | Notes |
| --- | --- | --- |
| GET/POST/PUT/DELETE | `/grilles[/{id}]` | |
| GET | `/grilles/pour-site/{siteId}` | grille du site, sinon générique, sinon `null` (200) |
| GET | `` | paginé `dateDebut,dateFin,siteId,decision,controleurEmployeId` |
| GET | `/{id}` | |
| POST | `` | multipart : `controle` (JSON) + `photos` |
| GET | `/{id}/photos/{index}` | image binaire |
| GET | `/historique/{siteId}?nbPoints=12` | `EvolutionNotePoint[]` chronologique |

`noteGlobale` recalculée serveur = moyenne pondérée normalisée (`Σ note·poids / Σ poids`). `decision` : `RESERVES` si fournie, sinon `≥ noteSeuilConformite (défaut 3.5)` → `CONFORME`, sinon `NON_CONFORME`. `commentaire` **obligatoire (400)** si `NON_CONFORME`/`RESERVES`.

### 7. Matériel — `/api/terrain/materiel`
| Méthode | Chemin | Notes |
| --- | --- | --- |
| GET | `` | paginé `q,type,statut,siteAffecteId` |
| GET/POST/PUT/DELETE | `[/{id}]` | code unique (409) |
| POST | `/{materielId}/affecter` | `{siteId,commentaire?}` → événement AFFECTATION |
| GET | `/{materielId}/historique` | événements |
| POST | `/{materielId}/panne` | `{description}` → statut EN_PANNE |
| POST | `/{materielId}/maintenance` | body `EvenementMateriel` → recalcule `prochaineMaintenance` |
| GET/POST | `/maintenance-programmee` | `dateDebut,dateFin` |
| GET | `/alertes` | alertes de maintenance |

**Alertes maintenance** : émises seulement si `joursRestants ≤ 30`. Niveau : `< 0` (en retard) → `CRITIQUE`, `0..7` → `ATTENTION`, `8..30` → `INFO`.

### 8. Phytosanitaire — `/api/terrain/phytosanitaire`
| Méthode | Chemin | Notes |
| --- | --- | --- |
| GET/POST/PUT/DELETE | `/produits[/{id}]` | `numeroHomologation` obligatoire + unique (409) |
| GET | `/applications` | paginé `dateDebut,dateFin,siteId,employeId,produitId,categorie,statut` |
| GET | `/applications/periode` | calendrier |
| GET/POST/PUT/DELETE | `/applications[/{id}]` | `numero = PHYTO-AAAAMMJJ-NNN` |
| GET | `/registre/pdf?dateDebut&dateFin` | **PDF serveur** (registre réglementaire) |
| GET | `/alertes-delais` | réentrée / nouvelle application interdite |

À l'enregistrement : `dateFinReentree = dateApplication + delaiReentreeHeures`, `dateProchaineApplicationAutorisee = dateApplication + delaiAvantNouvelleApplicationJours`.

### 9. Tableau de bord — `/api/terrain/tableau-bord`
Tous : `?dateDebut&dateFin&siteId?&employeId?&typeIntervention?`. `/rapport`, `/kpis`, `/interventions-par-site`, `/evolution-couverture`, `/incidents-par-site`, `/evolution-satisfaction`, `/satisfaction-par-site`, `/comparaison-periodes`.

- `tauxCouverture = nbInterventionsRealisees / nbAffectationsPlanifiees` (garde-fou /0 → 0). **Réalisées** = interventions `TERMINEE` ou `VALIDEE` ; **planifiées** = affectations de la période hors `ANNULEE`.
- `satisfactionMoyenne` = moyenne `noteGlobale` des contrôles (0..5). `nbControlesConformes` = décision `CONFORME`.
- `nbIncidents` = alertes `ABSENCE + DEPART_PREMATURE + POINTAGE_HORS_ZONE`. `nbAlertesEscaladees` = statut `ESCALADEE`.
- Séries `evolution-*` : bucket par **jour** si amplitude ≤ 62 jours, sinon par **mois** (`yyyy-MM`).
- `comparaison-periodes` : KPIs recalculés sur la période précédente de même durée + deltas (`deltaCouverturePoints`, `deltaInterventionsPourcent`, `deltaSatisfactionPoints`, `deltaIncidentsPourcent`).

## WebSocket (STOMP over SockJS)

Endpoint `/ws` (SockJS), broker `/topic` + `/queue`, prefix `/app` (config existante réutilisée). Payloads JSON :

| Destination | Quand | Payload |
| --- | --- | --- |
| `/topic/alertes-terrain` | création / escalade / résolution d'alerte | `AlerteTerrain` |
| `/topic/pointages-terrain` | chaque pointage | `PointageTerrain` |
| `/user/queue/notifications-terrain` | au destinataire courant de l'alerte | `NotificationTerrain` (`type`, `titre`, `message`, `alerteId`, `niveau`, `dateEmission`) |

## Hypothèses

- **Identité utilisateur courant** : le JWT porte l'email (subject) ; `CurrentUserProvider` résout l'`id` et le nom complet via la collection `utilisateur` (repli sur l'email si introuvable). Sert à remplir `resoluPar*`, `controleur*`, `updatedBy` quand non fournis par le corps.
- **Escalade automatique** : délais cumulés depuis `dateDetection` — niveau 2 à `delaiEscaladeNiveau1Minutes`, niveau 3 à `delaiEscaladeNiveau1Minutes + delaiEscaladeNiveau2Minutes`. Destinataire = 1er id de la liste du niveau dans `ParametresEscalade`.
- **Seuils par défaut (seed)** : escalade retard 15 / absence 60 / N1 30 / N2 120 (min) ; seuil de conformité contrôle 3.5 ; tolérance GPS site 100 m ; précision GPS max 50 m.
- **Stockage fichiers** : `byte[]` inline (pas de GridFS), transparent pour le frontend (URLs de streaming).
- **Date signature** : `signatureClient.date` conservée en `String` (valeur du client telle quelle).

## Seed (`TerrainDataLoader`)

Idempotent : 2 sites (Dakar, Thiès), 1 grille générique (seuil 3.5), 1 produit phytosanitaire homologué, 1 `ParametresEscalade`.