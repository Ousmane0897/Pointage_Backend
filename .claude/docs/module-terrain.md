# Module Exploitation Terrain (5.2)

Backend Spring Boot du module **Exploitation Terrain** : gestion des agents de propreté / entretien phytosanitaire sur sites clients. Module **autonome** (collections dédiées), consommé par un frontend Angular 19 déjà écrit. Toutes les routes sont sous `/api/terrain/...` et exigent un JWT Bearer.

## Conventions

- **Pagination** : `?page=0&size=N` (0-based) → réponse `{ "content": [...], "totalElements": N }` (record `util/PageResponse<T>`).
- **Dates** : ISO 8601. `LocalDate` → `yyyy-MM-dd`, `LocalDateTime` → `yyyy-MM-ddTHH:mm:ss` (sérialisation ISO par défaut via `JavaTimeModule`). Les query params `dateDebut`/`dateFin` de période sont des `yyyy-MM-dd`.
- **Erreurs** : `TerrainExceptionHandler` (advice scopé `controllers.terrain`, priorité max) → corps `{ "message", "timestamp", "status" }`. 400 (validation / `IllegalArgumentException`), 404 (`ResourceNotFoundException`), 409 (`TerrainConflitException` : code dupliqué, conflit planning, transition d'état interdite).
- **Sécurité** : `/api/terrain/**` est `.authenticated()` dans `SecurityConfig`. Depuis 2026-07-21, **toute requête non authentifiée → 401** (token absent comme token invalide/expiré), grâce à l'`authenticationEntryPoint` global ajouté dans `SecurityConfig` ; auparavant le token absent donnait 403. Vérifié par `PlanningSecuriteIT`. Le gating fin par module reste **frontend uniquement** (pas de `@PreAuthorize`, et les sous-flags `ModulesAutorises.terrain.*` n'existent toujours pas).
- **Indexes** : `TerrainIndexesConfig` (pattern `ProductionChimieIndexesConfig`, car `auto-index-creation` est désactivé → les annotations `@Indexed`/`@CompoundIndex` sont inopérantes dans ce projet). `terrain_affectations` : `statut_debut_fin`, `employe_id`, `site_statut`.
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
| GET | `/affectations/stats` | `dateDebut?,dateFin?` → `{PLANIFIEE,EN_COURS,EFFECTUEE,ANNULEE,REMPLACEE}` : les 5 clés toujours présentes (0 inclus) |
| GET | `/affectations/{id}` | |
| POST / PUT | `/affectations[/{id}]` | 409 si chevauchement ; remplacement → initiale `REMPLACEE` |
| POST | `/affectations/{id}/annuler` | annulation motivée, body `{motif}` → l'affectation à jour |

⚠️ **`DELETE /affectations/{id}` a été supprimé (2026-07-21).** Une affectation ne se supprime pas, elle s'annule : le hard delete permettait d'effacer une ligne sans laisser de trace, contournant la traçabilité exigée par le métier. Toute suppression passe désormais par `/annuler`. La route ne répond plus (405) — verrouillé par `PlanningControllerTest.delete_affectation_n_existe_plus`.

**Annulation motivée** (`POST /affectations/{id}/annuler`, ajouté 2026-07-21) — conserve la ligne en historique au lieu de la supprimer :

- Body `{ "motif": "..." }` — **obligatoire, ≥ 5 caractères après `trim`**, sinon **400**. Le motif est stocké trimé.
- **Garde de statut** : autorisée seulement depuis `PLANIFIEE` ou `EN_COURS`. Depuis `EFFECTUEE` / `ANNULEE` / `REMPLACEE` (terminaux) → **409** `TerrainConflitException`. Rejouer l'appel sur une affectation déjà annulée retombe donc sur cette garde (idempotence métier), le motif initial n'est jamais écrasé.
- Renseigne `statut=ANNULEE` + 3 champs de traçabilité sur `AffectationAgent` : `motifAnnulation`, `dateAnnulation`, `annuleParNom`. **`annuleParNom` est déduit du JWT** (`CurrentUserProvider.currentUserNom()`), jamais lu du corps — sinon l'auteur serait usurpable.
- Les 3 champs sont exposés dans **toutes** les réponses affectation (`toDto` 1:1) mais **ignorés en écriture** dans `AffectationAgentMapper.toEntity` / `updateEntityFromDto` : un `POST`/`PUT /affectations` ne peut pas les poser (même pattern que `ProduitStockMapper` en Stock 7.6).
- Opération mono-document → atomique nativement, pas de `@Transactional`. `AffectationStatutScheduler` ne cible que les statuts source `PLANIFIEE`/`EN_COURS`, une annulée n'est donc jamais « ressuscitée ». Effet de bord voulu : `EffectifSiteService` comptant `statutNot(ANNULEE)`, l'annulation libère une place sur le site.
- ⚠️ **Course avec le job de bascule, assumée — ne pas assouplir la garde.** Si le job passe une affectation en `EFFECTUEE` entre le chargement de la page et le clic « Annuler », l'utilisateur reçoit un **409**. C'est correct : on n'annule pas rétroactivement une prestation terminée. Ajouter `EFFECTUEE` aux `STATUTS_ANNULABLES` pour « corriger » ce 409 rouvrirait exactement le trou de traçabilité qui a motivé la suppression du `DELETE`.

**Transitions automatiques de statut** (`AffectationStatutScheduler`, réécrit 2026-07-21) — la colonne `statut` est la **seule vérité** (la vue frontend à onglets filtre et compte dessus côté serveur via `list`/`stats`) ; ce job la maintient alignée sur la réalité temporelle du créneau, sans aucun calcul à la volée ni changement côté front.

- **Deux transitions, dans cet ordre** : `PLANIFIEE + dateDebut <= now → EN_COURS`, puis `EN_COURS + dateFin <= now → EFFECTUEE`. **L'ordre est significatif** : une affectation au créneau entièrement passé (créée en retard, ou job interrompu plusieurs heures) est promue par la 1ʳᵉ requête puis captée par la 2ᵈᵉ → elle atteint `EFFECTUEE` en **une seule passe**, sans rester bloquée un tour sur `EN_COURS`.
- **Mises à jour ensemblistes** : deux `mongoTemplate.updateMulti` (`$set` ciblé sur `statut` + `updatedAt`), jamais de chargement en mémoire suivi de `save`. L'implémentation précédente (`findByStatutIn` → boucle → `saveAll`) réécrivait le document entier depuis un snapshot et pouvait écraser une annulation concurrente en last-write-wins.
- **Statuts terminaux protégés sans liste d'exclusion** : `ANNULEE` / `REMPLACEE` / `EFFECTUEE` ne sont source d'aucune transition, donc jamais matchés par la clause `statut = source`.
- **Idempotence et concurrence** : rejouer le job matche 0 document. Deux instances simultanées ne peuvent pas se doubler pour la même raison → **pas de ShedLock** (choix explicite, le verrou n'apporterait rien ici ; à réévaluer seulement si un besoin non idempotent s'ajoute au job).
- **Fréquence** : cron `0 * * * * *` zone `Africa/Dakar` (1 min, et non 5 — meilleure réactivité pour la vue à onglets, coût négligeable en ensembliste). **Rattrapage au boot** via `@EventListener(ApplicationReadyEvent.class)` pour régulariser le stock historique périmé.
- **Fuseau** : `TimeConfig.clock()` est ancré sur `Africa/Dakar` (`Clock.system(ZONE_METIER)`), **pas** `systemDefaultZone()`. Les entités sont datées en `LocalDateTime` : sur une JVM hors Dakar, un clock par défaut décalerait silencieusement toutes les comparaisons de créneau. L'attribut `zone` de `@Scheduled` ne corrige pas ce point — il ne pilote que l'heure de déclenchement, jamais la valeur de `now`.
- **Log par exécution** : `Transitions affectations : {n} → EN_COURS, {n} → EFFECTUEE` — un job devenu silencieux ou bloqué à 0 pendant des jours doit rester détectable.
- **Indexes** : `statut_debut_fin` couvre la 1ʳᵉ requête par son préfixe `(statut, dateDebut)` ; la 2ᵈᵉ n'exploite que le préfixe `statut` (acceptable, l'ensemble `EN_COURS` est borné par les créneaux simultanés). Ajouter `(statut, dateFin)` seulement si un `explain()` le justifie sur données réelles.
- **Point ouvert (métier, non tranché)** : `EFFECTUEE` signifie ici **« le créneau est écoulé »** — pure horloge, sans lien avec le travail réellement accompli, alors que le module Pointage remonte les entrées/sorties GPS. La condition est isolée dans `conditionPassageEffectuee(now)`, **seul point à changer** si le métier redéfinit `EFFECTUEE` comme « l'agent a pointé sa sortie » ; il faudra alors décider du sort des créneaux passés **sans** pointage (traitement d'absence plutôt que `EFFECTUEE`).
- **Effet de bord à connaître** : remettre manuellement en `PLANIFIEE` une affectation passée ne la fige pas — le job la fera ré-avancer à la minute suivante. Les seuls états hors du flux temporel sont `ANNULEE` et `REMPLACEE`.
- **Tests** : `AffectationStatutSchedulerIT` (Testcontainers — la règle vit désormais dans la requête Mongo, un test à repository mocké ne prouverait plus rien) : les 2 transitions, le rattrapage en une passe, les statuts terminaux intacts, l'affectation future, le `$set` ciblé (autres champs préservés), l'idempotence (`updatedAt` figé au 2ᵈ appel) et 2 exécutions concurrentes.

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