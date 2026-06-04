# Module RH — détails d'implémentation

> Document chargé à la demande quand une tâche touche au module RH (sections 6.1 à 6.4). Le résumé court et les pointeurs vivent dans `CLAUDE.md` à la racine.

## Module RH — ✅ Backend terminé

Les 4 sous-modules RH sont livrés. Le frontend Angular (repo séparé) consomme directement les API REST exposées ici, authentifiées par JWT (`AuthInterceptor`) et soumises au fallback `.anyRequest().authenticated()` de `SecurityConfig`.

**Résumé quantitatif** : 4 sous-modules × ~10 entités MongoDB × ~5 controllers × ~5 services chacun, plus un tableau de bord RH qui agrège les 4 sources.

### Résumé global du backend RH

#### Collections MongoDB

| Sous-module | Collection(s) |
| --- | --- |
| 6.1 Personnel | `dossiers_employes` (source de vérité RH), `contrats`, `periodes_essai`, `demandes_validation_periode_essai`, `documents_employes`. `employes` + `employes_complet` restent pour le pointage mobile/legacy. |
| 6.2 Temps & Présences | `rh_absences`, `conges`, `heures_supplementaires` (réutilise aussi `pointages` pré-RH) |
| 6.3 Paie | `categories_professionnelles`, `bulletins_paie`, `declarations_sociales`, `parametres_paie` |
| 6.4 Développement RH | `formations`, `sessions_formation`, `participations_formation`, `evaluations_formation`, `besoins_formation`, `grilles_evaluation`, `evaluations_periodiques`, `sanctions` |

#### Services

| Sous-module | Services |
| --- | --- |
| 6.1 | `DossierEmployeService` (CRUD RH, source de vérité), `ContratService` (multipart + fichier PDF), `PeriodeEssaiService` + `DemandeValidationPeriodeEssaiService` (workflow Manager → RH → Confirmation), `DocumentEmployeService` (pièces administratives + workflow validation). `EmployeServices`, `EmployeCompletService`, `OrganigrammeService`, `RhEmployeService` subsistent pour le pointage mobile / legacy. |
| 6.2 | `PointageCentraliseService`, `RhAbsenceService`, `DemandeCongeService`, `HeureSupplementaireService`, `RecapitulatifMensuelService` |
| 6.3 | `CategorieProfessionnelleService`, `BulletinPaieService`, `CalculPaieService` (moteur de paie : IPRES/CSS/AT-MP/TRIMF/IR), `DeclarationSocialeService`, `ParametresPaieService` |
| 6.4 | `FormationService`, `EvaluationPeriodiqueService`, `SanctionService`, `BesoinFormationService`, `TableauBordRhService` |

#### Controllers

Voir la table « Controller URL map » de `CLAUDE.md`, sections RH 6.1 à 6.4.

#### Points d'implémentation à connaître

- **Principe d'indépendance du module RH** (depuis 2026-04) : les services RH — `ContratService`, `RhAbsenceService`, `DemandeCongeService`, `HeureSupplementaireService`, `BesoinFormationService`, `SanctionService`, `EvaluationPeriodiqueService`, `FormationService` (addParticipant), `TableauBordRhService` (KPIs effectif/turnover/répartitions) — valident et dénormalisent depuis `DossierEmployeRepository` ; ne plus les brancher sur `EmployeCompletRepository`. **Exceptions maintenues** : `PointageCentraliseService`, `RecapitulatifMensuelService` et `CalculPaieService` utilisent encore `EmployeComplet` car ils font la jointure avec `Pointage` via `agentId`/`codeSecret` et lisent `heureDebut` pour le calcul des retards.
- **`TableauBordRhService` agrège en parallèle** via `CompletableFuture.supplyAsync` plutôt qu'un `$lookup` géant — les collections sources n'ont pas de clé de jointure commune, chaque KPI reste testable isolément. `calculerPersonnel` lit `DossierEmploye` ; `calculerTempsPresence` passe par `PointageCentraliseService`.
- **Source de vérité pour les présences/retards** : `PointageCentraliseService` (6.2) — parse `HH:mm` de `EmployeComplet.heureDebut` vs `Pointage.heureArrive` pour calculer `retardMinutes`. Ne pas réimplémenter ailleurs.
- **Département** : sur `DossierEmploye` c'est un champ de 1er niveau. Côté `EmployeComplet` legacy on utilise `agence[0]`. Les collections RH récentes (`Sanction`, `RhAbsence`, `DemandeConge`, `EvaluationPeriodique`, `BulletinPaie`) portent un vrai `departement` dénormalisé.
- **Récidive disciplinaire** : `SanctionService.estRecidiviste(employeId, type, dateRef)` via `countByEmployeIdAndTypeAndDateSanctionBetween` sur 12 mois glissants, seuil ≥ 2. `alertesRecidive()` élargit à tous types confondus.
- **Workflow évaluation périodique** : `BROUILLON → AUTO_EVALUATION → EVALUATION_MANAGER → VALIDE`. La note globale est la moyenne pondérée `Σ(note×poids)/Σpoids` selon la `GrilleEvaluation` référencée ; mapping alphabétique A≥4.5, B≥3.5, C≥2.5, sinon D. À la validation, les `BesoinFormation` portés par la `ValidationEvaluationRequest` sont créés automatiquement (`source=EVALUATION`).
- **Procédure disciplinaire sénégalaise** : `Sanction.dureeMiseAPied` plafonnée à 8 jours (Code du Travail), délai de respect calculé automatiquement entre `dateConvocation` et `dateEntretien`.
- **Grille d'évaluation par défaut** : seedée au démarrage (`DataLoader`) avec 5 critères (Expertise technique 30, Autonomie 20, Communication 15, Travail en équipe 15, Atteinte des objectifs 20 — total 100).

### 6.1 Gestion du personnel — ✅ Terminé

Endpoints livrés :

- **Dossier employé RH (nouvelle source de vérité, depuis 2026-04)** :
  CRUD multipart `/api/gestion-personnel/employes` (part JSON `dossier` + `photo` optionnelle).
  Champs : matricule (unique), identité (nom, prénom, genre HOMME/FEMME, dateNaissance,
  nationalité, numeroIdentification, situationMatrimoniale CELIBATAIRE/MARIE,
  nombreEnfants conditionnel), poste, département, siteAffecte, dateEntrée,
  statut `ACTIF | EN_PERIODE_ESSAI | SUSPENDU | SORTI`, superieurHierarchiqueId/Nom,
  dureeEssaiMois (stocké uniquement si statut=EN_PERIODE_ESSAI, remis à null à la
  titularisation), contacts (tél, email, adresse), contactUrgence (sous-doc
  {nom, lienParente, telephone}), photo (byte[]). Champs paie techniques
  (categorieCode, numeroIpres, numeroCss, rib, banque) également portés sur l'entité.
  Endpoints additionnels : `GET /{id}/photo` (permitAll), `PUT /{id}/statut`,
  `PUT /{id}/titulariser`, `GET /alertes-essai`.
- **Import bulk de dossiers employés** : `POST /api/gestion-personnel/employes/bulk`
  (JSON pur, sans photos — le CRUD unitaire reste utilisé ensuite pour les photos).
  Remplace la boucle d'appels unitaires que faisait le frontend lors d'un import Excel.
  Body : `{ employes: DossierEmployeBulkLigneDto[], strategieErreurs?: "TOUT_OU_RIEN"
  | "IMPORTER_LIGNES_VALIDES" }`. Le champ `strategieErreurs` est **optionnel**
  depuis 2026-04 : si absent ou null, le défaut **TOUT_OU_RIEN** est appliqué par
  le record `DossierEmployeBulkImportRequest` (rétro-compatibilité frontend).
  Réponse : `{ total, inserted, failed, insertedIds, errors: [{ index, matricule,
  field, code, message }] }`. Codes : **200** tout inséré, **207** partiel
  (IMPORTER_LIGNES_VALIDES avec erreurs), **422** TOUT_OU_RIEN avec ≥ 1 erreur
  (aucune insertion), **400** payload invalide (vide / trop grand). Limite
  configurable `rh.import.bulk.max-size: 1000`.
  Codes d'erreur métier : `CHAMP_OBLIGATOIRE`, `VALEUR_INVALIDE`,
  `MATRICULE_DUPLIQUE_PAYLOAD`, `MATRICULE_DUPLIQUE_BASE`, `SUPERIEUR_INEXISTANT`,
  `VALIDATION_CONDITIONNELLE`, `REFERENCE_CIRCULAIRE` (cycle de supérieurs détecté
  par DFS 3-couleurs sur le graphe intra-payload). Le supérieur hiérarchique peut
  être référencé via `superieurHierarchiqueMatricule` (champ spécifique au DTO bulk) :
  résolu en deux passes — soit dans le payload lui-même (pass B d'`updateFirst`
  post-`saveAll()`), soit en base (pass A).
  **Atomicité** : MongoDB en standalone ne supportant pas les transactions multi-
  documents, l'atomicité `TOUT_OU_RIEN` est garantie par validation préalable
  complète du batch avant tout `saveAll()`. Si `saveAll()` échoue à mi-parcours
  (race condition sur l'index unique `matricule`, erreur I/O), une
  `BulkInsertPartialFailureException` est levée → HTTP 500 avec body
  `{error: "BULK_INSERT_PARTIAL_FAILURE", message, idsDejaInseres: [...]}` ;
  aucun rollback compensatoire n'est tenté.
  **RBAC fine-grained sur l'import bulk à traiter dans une PR dédiée** — pour
  l'instant l'endpoint tombe sous l'authentification JWT standard
  (`.anyRequest().authenticated()` dans `SecurityConfig`).
  Validation plus stricte qu'au CRUD unitaire sur un point : `situationMatrimoniale
  = MARIE` exige `nombreEnfants` non null dans le bulk (alors que `create()` se
  contente de nullifier `nombreEnfants` hors MARIE).
- **Contrats** : CRUD multipart `/api/contrats` (part JSON `contrat` + `fichier` optionnel).
  `Contrat.fichierContrat` (byte[]) + `fichierContratNom` + `fichierContratMimeType`.
  Nouveaux endpoints `GET /api/contrats/{id}/fichier` (télécharge le PDF),
  `DELETE /api/contrats/{id}/fichier`. GET `/api/contrats/alertes`.
  `ContratService` valide désormais l'employé via `DossierEmployeRepository`.
  `TypeContratRh` : **ALTERNANCE remplacé par PRESTATION** ({CDI, CDD, STAGE, PRESTATION}).
  `ContratAlternanceMigrationRunner` (CommandLineRunner, `@Order(1000)`) migre
  les documents existants au démarrage via `mongoTemplate.updateMulti` — idempotent.
  Champ optionnel `dureeEssaiMois` (Integer, **convention RH en mois**) sur
  `Contrat` / `ContratDto` : pris en compte s'il est > 0 (override explicite).
  **Le modèle Angular `Contrat` n'expose pas ce champ pour l'instant** : à
  défaut, `ContratService.resoudreDureeEssaiMois` dérive la durée depuis
  `DossierEmploye.dureeEssaiMois` quand l'employé est en `EN_PERIODE_ESSAI`.
  Si une durée résolue > 0 est trouvée,
  `PeriodeEssaiService.seedFromContrat(contrat, mois)` crée automatiquement
  une `PeriodeEssai` (statut EN_COURS, alertes par défaut à 30 / 15 / 7
  jours). La conversion mois → jours pour `PeriodeEssai.dureeJours` se fait
  via `dateDebut.plusMonths(mois)` (calendaire-correct, p.ex. 3 mois ≠ 90
  jours fixes selon les mois traversés).
- **Période d'essai (depuis 2026-04-29)** : nouvelle source de vérité dans la
  collection `periodes_essai` (entité `PeriodeEssai`, **liée à un Contrat** via
  `contratId`). 7 endpoints sous `/api/gestion-personnel/periodes-essai` (consommés
  directement par le frontend `PeriodeEssaiService` Angular) :
  - `GET /` (paginé, filtre `statut`), `GET /{id}`,
    `PUT /{id}/prolonger {nouvelleDateFin, commentaire}` →
    statut `PROLONGE`, recalcul des alertes, append d'une `DecisionPeriodeEssai`.
  - `GET /alertes` : retourne les périodes EN_COURS / PROLONGE dont au moins une
    alerte non envoyée a une `dateAlerte ≤ today`.
  - `GET /validations?statut=`, `POST /{periodeEssaiId}/validations {commentaire}`,
    `PUT /validations/{demandeId} {decision, commentaire}` —
    workflow `EN_ATTENTE_MANAGER → VALIDEE_MANAGER → VALIDEE_RH → CONFIRMEE`,
    ou `REFUSEE` à n'importe quelle étape (collection
    `demandes_validation_periode_essai`). `ActionValidation` accepté :
    `VALIDER`, `CONFIRMER`, `REFUSER`. Transitions illégales → 400
    `VALIDATION_ERROR`. Doublon de demande active sur la même période → 409
    `DEMANDE_VALIDATION_CONFLICT` (nouvelle exception
    `DemandeValidationConflictException`).
    À l'étape `CONFIRMEE`, `applyTitularisation` bascule
    `PeriodeEssai.statut → TITULARISE` (avec `DecisionPeriodeEssai` de trace) et
    `DossierEmploye.statut → ACTIF`, `dureeEssaiMois → null`.
  - **Pas d'endpoint POST direct sur `periodes-essai`** : la création est
    déclenchée uniquement à la création d'un `Contrat` portant
    `dureeEssaiJours > 0` (rétrocompat : un contrat sans ce champ ne crée pas de
    période). Pas de seed rétroactif sur les contrats existants.
  - `DossierEmploye.dureeEssaiMois` reste lisible en **lecture-only legacy** ;
    la source de vérité pour la durée et la date de fin est désormais
    `PeriodeEssai`. L'endpoint
    `GET /api/gestion-personnel/employes/alertes-essai` (sur `DossierEmploye`)
    subsiste pour rétrocompat — préférer
    `GET /api/gestion-personnel/periodes-essai/alertes` côté frontend.
- **Documents employé (depuis 2026-04-29)** : nouvelle collection
  `documents_employes` portant toutes les pièces administratives génériques
  (CNI, diplôme, certificat, attestation, contrat scanné, autre) attachées à
  un employé, en complément des cas spécifiques déjà gérés (contrat PDF dans
  `Contrat`, photo dans `DossierEmploye`, justificatif dans `RhAbsence`).
  Endpoints `/api/gestion-personnel/documents` (consommés par
  `DocumentEmployeService` Angular) :
  - `GET /` paginé avec filtres `employeId` et `categorie`,
    `GET /employe/{employeId}` (raccourci non paginé),
    `GET /{id}`, `GET /{id}/telecharger` (flux binaire,
    `Content-Disposition: attachment`).
  - `POST /` multipart (`document` JSON + `fichier`) : 404 si employé
    inexistant, 400 si `nom`/`categorie`/`fichier` manquant. À l'upload,
    snapshot `employeNom/Prenom` depuis `DossierEmploye`,
    `dateUpload = now()`, `statut = EN_ATTENTE`. Limite multipart globale
    10MB (`spring.servlet.multipart.max-file-size`).
  - `PUT /{id}` JSON (métadonnées only) : `nom`, `categorie`,
    `dateExpiration`, `commentaire` modifiables ; `statut` et `fichier`
    NE SONT PAS éditables ici (statut → `/valider`, fichier → DELETE +
    re-POST).
  - `PUT /{id}/valider` body `{statut: VALIDE|REFUSE, commentaire?}` :
    workflow d'approbation RH. La **révision est autorisée** (passage
    VALIDE ↔ REFUSE possible) tant que le document n'est pas EXPIRE,
    pour permettre au RH de corriger une décision erronée.
  - `DELETE /{id}` supprime le document (et son `byte[]`).
  - **Statut `EXPIRE` dérivé à la lecture, jamais persisté** : si
    `dateExpiration < today` ET statut stocké ∈ {VALIDE, EN_ATTENTE},
    le DTO renvoie `EXPIRE`. `REFUSE + dateExpiration passée` reste
    `REFUSE` (état terminal). Logique implémentée dans
    `DocumentEmployeService.deriverStatutAffiche`.
  - Catégories : `CNI, DIPLOME, CERTIFICAT, ATTESTATION, CONTRAT, AUTRE`
    (enum `CategorieDocument`).
- **Legacy / coexistence** : CRUD `/api/employe-complet` (EmployeComplet, photo + contrat PDF),
  `/api/employes` et `/api/rh-employes` (RhEmployeController sur EmployeComplet),
  `/api/organigramme` reste en fonction pour le pointage mobile et les flux
  legacy. **`/api/periodes-essai` (ancien `PeriodeEssaiController` sur
  `EmployeComplet`) a été supprimé** au profit de
  `/api/gestion-personnel/periodes-essai`.

### 6.2 Temps & Présences — ✅ Terminé

- Pointage centralisé : GET `/api/pointage-centralise?date=&departement=&site=&statut=&q=&page=&size=`, GET `/api/pointage-centralise/resume?date=`
- **Absences** : CRUD `/api/rh-absences`, POST `/api/rh-absences/{id}/justificatif` (upload).
  Champ `typeAutrePrecision` obligatoire quand `type=AUTRE` (validation dans
  `RhAbsenceService.validerCoherenceType` → 400 `VALIDATION_ERROR` via le nouveau
  handler `IllegalArgumentException`). Nettoyage à null si `type != AUTRE` pour
  éviter les résidus incohérents. Validation employé via `DossierEmployeRepository`.
- Congés : CRUD `/api/conges`, PUT `/api/conges/{id}/approuver`, PUT `/api/conges/{id}/refuser`, GET `/api/conges/solde/{employeId}`. Validation employé via `DossierEmployeRepository`.
- Heures supplémentaires : CRUD `/api/heures-supplementaires`, PUT `/api/heures-supplementaires/{id}/valider`. Validation employé via `DossierEmployeRepository`.
- Récapitulatif mensuel : GET `/api/recapitulatif-mensuel?mois=&annee=&departement=`, exports Excel/PDF. Reste sur `EmployeComplet` (jointure Pointage).

### 6.3 Paie — ✅ Terminé

- **Grille salariale** (catégories professionnelles) : CRUD `/api/grille-salariale`.
  `CategorieProfessionnelle` porte désormais 3 listes embedded supplémentaires
  (spec frontend 2026-04) : `prets` (`{libelle, montant, dureeMois}`),
  `avancesSurSalaire` (idem), `retenues` (`{libelle, montant}` sans durée).
  Ces rubriques sont des retenues personnelles qui **diminuent le net POST-cotisations**
  — elles n'impactent **pas** les assiettes IPRES/CSS/IR (restent sur le brut).
- **Bulletin de paie** : POST `/api/bulletins-paie/calculer`, CRUD `/api/bulletins-paie`,
  workflow `/valider` `/payer` `/annuler`, GET `/{id}/pdf`, GET `/historique?employeId=&annee=`.
  `CalculPaieService` expose 3 méthodes publiques testables :
  `calculerTotalPrets(List<PretCategorie>)`, `calculerTotalAvances(...)`,
  `calculerTotalRetenues(...)`. Formule du net :
  `netAPayer = brut - totalCotisSalariales - IR - TRIMF - totalPrets - totalAvances - totalRetenues`.
  `BulletinPaie` snapshot les rubriques appliquées (`pretsAppliques`,
  `avancesAppliquees`, `retenuesAppliquees`, `totalPrets/Avances/Retenues`) pour
  traçabilité — une modification ultérieure de la grille n'affecte pas les bulletins
  déjà générés. Le PDF (OpenPDF) affiche une **section conditionnelle « Retenues
  personnelles »** uniquement si au moins une rubrique est présente.
- Déclarations sociales : GET `/api/declarations-sociales/ipres?periode=`, GET `/api/declarations-sociales/css?periode=`, exports PDF/Excel, PUT `/{id}/transmettre`. **Aucun impact** des nouvelles retenues (agrégation somme `totalCotisationsSalariales` et `totalCotisationsPatronales` des bulletins, tous deux calculés sur le brut).
- Paramètres de paie : GET/PUT `/api/parametres-paie` (taux IPRES/CSS/AT-MP/TRIMF et barème IR — stockés en Mongo, pas en dur).

### 6.4 Développement RH — ✅ Terminé

Endpoints livrés :

- **Formations** : CRUD `/api/formations`, CRUD `/api/formations/{id}/sessions` et `/api/formations/sessions/{sessionId}`, GET/POST `/api/formations/sessions/{sessionId}/participants`, PUT `/api/formations/participations/{id}/presence` et `/completion`, GET/POST `/api/formations/sessions/{sessionId}/evaluations` (évaluations à chaud).
- **Évaluations périodiques** : CRUD `/api/evaluations`, workflow `PUT /{id}/auto-evaluer` `PUT /{id}/evaluer-manager` `PUT /{id}/valider`.
- **Sanctions** : CRUD `/api/sanctions`, GET `/api/sanctions/historique/{employeId}`, GET `/api/sanctions/alertes-recidive`, PUT `/api/sanctions/{id}/statut`.
- **Besoins de formation** : CRUD `/api/besoins-formation`, GET `/api/besoins-formation/employe/{employeId}`, PUT `/api/besoins-formation/{id}/statut`.
- **Tableau de bord RH** : GET `/api/tableau-bord-rh?dateDebut=&dateFin=&departement=&site=` (agrège effectif + turnover + absentéisme + retards moyens + solde congés + masse salariale + formations + sanctions).

Déviations par rapport à la spec initiale du frontend :

- `/api/formations/{id}/evaluations` → `/api/formations/sessions/{sessionId}/evaluations` (sessionId required dans le TS).
- `/api/tableau-bord-rh?periode=&departement=` → `?dateDebut=&dateFin=&departement=&site=` (conforme à `FiltreTableauBord` du frontend).
- Ajout de `/api/besoins-formation` (non listé dans la spec initiale, nécessaire pour l'écran besoins).
