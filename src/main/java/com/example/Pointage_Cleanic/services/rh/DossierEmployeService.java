package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.AffectationSiteDto;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeBulkImportRequest;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeBulkImportResponse;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeBulkLigneDto;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeDto;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeImportError;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeStatutRequest;
import com.example.Pointage_Cleanic.Enum.rh.JoursTravail;
import com.example.Pointage_Cleanic.Enum.rh.SituationMatrimoniale;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Enum.StrategieErreursImport;
import com.example.Pointage_Cleanic.Mapper.rh.DossierEmployeMapper;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.exception.AffectationInvalideException;
import com.example.Pointage_Cleanic.exception.BulkInsertPartialFailureException;
import com.example.Pointage_Cleanic.exception.EmployeAlreadyExistsException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.util.AffectationSiteUtils;
import com.example.Pointage_Cleanic.util.SiteAffecteUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DossierEmployeService {

    private final DossierEmployeRepository dossierEmployeRepository;
    private final DossierEmployeMapper mapper;
    private final MongoTemplate mongoTemplate;
    /**
     * Horloge d'{@code Africa/Dakar} ({@code configurations.TimeConfig}) : « une
     * affectation est-elle close ? » se tranche par rapport à une date, et la figer
     * en dur rendrait la règle fausse hors Dakar et intestable.
     */
    private final Clock clock;

    @Value("${rh.import.bulk.max-size:1000}")
    private int bulkMaxSize;

    // agentId = code agent 4 chiffres, clé du pointage
    private static final java.util.regex.Pattern AGENT_ID_PATTERN =
            java.util.regex.Pattern.compile("^\\d{4}$");

    public Page<DossierEmployeDto> list(int page, int size, String q, String departement,
                                        String site, String poste, String statut) {
        Pageable pageable = PageRequest.of(page, size);
        List<Criteria> criterias = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            criterias.add(new Criteria().orOperator(
                    Criteria.where("nom").regex(q, "i"),
                    Criteria.where("prenom").regex(q, "i"),
                    Criteria.where("matricule").regex(q, "i"),
                    Criteria.where("email").regex(q, "i")
            ));
        }
        if (departement != null && !departement.isBlank()) {
            criterias.add(Criteria.where("departement").regex("^" + departement + "$", "i"));
        }
        if (site != null && !site.isBlank()) {
            // Sous-chaîne insensible à la casse (et non plus ancrée ^…$) pour
            // matcher un employé multi-sites dont le siteAffecte concatène
            // plusieurs sites (ex. "Sacré-Coeur - Point E" trouvé par "Point E").
            criterias.add(Criteria.where("siteAffecte").regex(java.util.regex.Pattern.quote(site), "i"));
        }
        if (poste != null && !poste.isBlank()) {
            criterias.add(Criteria.where("poste").regex(poste, "i"));
        }
        if (statut != null && !statut.isBlank()) {
            criterias.add(Criteria.where("statut").is(StatutDossierEmploye.valueOf(statut)));
        }

        Query query = new Query();
        if (!criterias.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criterias.toArray(new Criteria[0])));
        }
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), DossierEmploye.class);
        query.with(pageable);
        List<DossierEmploye> results = mongoTemplate.find(query, DossierEmploye.class);

        return PageableExecutionUtils.getPage(results, pageable, () -> total)
                .map(this::toDtoWithUrl);
    }

    public DossierEmployeDto getById(String id) {
        DossierEmploye d = requireById(id);
        return toDtoWithUrl(d);
    }

    public DossierEmployeDto create(DossierEmployeDto dto, MultipartFile photo) throws IOException {
        if (dto.getMatricule() == null || dto.getMatricule().isBlank()) {
            throw new IllegalArgumentException("Le matricule est obligatoire");
        }
        if (dossierEmployeRepository.existsByMatricule(dto.getMatricule())) {
            throw new EmployeAlreadyExistsException("Un dossier existe déjà avec le matricule " + dto.getMatricule());
        }
        validerAgentId(dto.getAgentId());
        if (dossierEmployeRepository.existsByAgentId(dto.getAgentId())) {
            throw new EmployeAlreadyExistsException("Un dossier existe déjà avec l'agentId " + dto.getAgentId());
        }
        validerCoherenceDureeEssai(dto.getStatut(), dto.getDureeEssaiMois());
        validerCoherenceNombreEnfants(dto.getSituationMatrimoniale(), dto.getNombreEnfants());
        validerJoursTravail(dto.getJoursTravail());

        DossierEmploye entity = mapper.toEntity(dto);
        nettoyerChampsOptionnels(entity);
        appliquerAffectations(entity, dto);

        if (photo != null && !photo.isEmpty()) {
            entity.setPhoto(photo.getBytes());
        }

        DossierEmploye saved = dossierEmployeRepository.save(entity);
        return toDtoWithUrl(saved);
    }

    public DossierEmployeDto update(String id, DossierEmployeDto dto, MultipartFile photo) throws IOException {
        DossierEmploye existing = requireById(id);

        if (dto.getMatricule() != null && !dto.getMatricule().equals(existing.getMatricule())
                && dossierEmployeRepository.existsByMatricule(dto.getMatricule())) {
            throw new EmployeAlreadyExistsException("Un dossier existe déjà avec le matricule " + dto.getMatricule());
        }

        if (dto.getAgentId() != null) {
            validerAgentId(dto.getAgentId());
            if (!dto.getAgentId().equals(existing.getAgentId())
                    && dossierEmployeRepository.existsByAgentId(dto.getAgentId())) {
                throw new EmployeAlreadyExistsException("Un dossier existe déjà avec l'agentId " + dto.getAgentId());
            }
        }

        // Validation de la valeur entrante avant le merge (null = ignoré par le
        // merge null-safe → valeur existante conservée, pas de validation requise).
        validerJoursTravail(dto.getJoursTravail());

        mapper.updateEntityFromDto(dto, existing);
        // Règles de cohérence post-mise-à-jour
        validerCoherenceDureeEssai(existing.getStatut(), existing.getDureeEssaiMois());
        validerCoherenceNombreEnfants(existing.getSituationMatrimoniale(), existing.getNombreEnfants());
        nettoyerChampsOptionnels(existing);
        // ⚠ Ici, et ici seulement, l'ancienne liste (le mapper l'ignore) et la
        // nouvelle coexistent : c'est le seul point où la perte est détectable.
        verifierAffectationsClosesConservees(existing.getAffectations(), dto.getAffectations());
        // Remplacement complet des affectations (le mapper les a ignorées) +
        // re-dérivation de siteAffecte.
        appliquerAffectations(existing, dto);

        if (photo != null && !photo.isEmpty()) {
            existing.setPhoto(photo.getBytes());
        }

        DossierEmploye saved = dossierEmployeRepository.save(existing);
        return toDtoWithUrl(saved);
    }

    public void delete(String id) {
        DossierEmploye existing = requireById(id);
        dossierEmployeRepository.delete(existing);
    }

    public DossierEmployeDto updateStatut(String id, DossierEmployeStatutRequest request) {
        DossierEmploye existing = requireById(id);

        existing.setStatut(request.getStatut());
        if (request.getStatut() == StatutDossierEmploye.EN_PERIODE_ESSAI) {
            existing.setDureeEssaiMois(request.getDureeEssaiMois());
            if (existing.getDureeEssaiMois() == null) {
                throw new IllegalArgumentException("dureeEssaiMois est obligatoire pour le statut EN_PERIODE_ESSAI");
            }
        } else {
            existing.setDureeEssaiMois(null);
        }

        DossierEmploye saved = dossierEmployeRepository.save(existing);
        return toDtoWithUrl(saved);
    }

    public byte[] getPhoto(String id) {
        DossierEmploye d = requireById(id);
        return d.getPhoto();
    }

    public DossierEmploye requireById(String id) {
        return dossierEmployeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier employé introuvable : " + id));
    }

    private DossierEmployeDto toDtoWithUrl(DossierEmploye entity) {
        DossierEmployeDto dto = mapper.toDto(entity);
        if (entity.getPhoto() != null && entity.getPhoto().length > 0) {
            dto.setPhotoUrl("/api/dossier-employe/" + entity.getId() + "/photo");
        }
        return dto;
    }

    private void validerAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("L'agentId est obligatoire");
        }
        if (!AGENT_ID_PATTERN.matcher(agentId).matches()) {
            throw new IllegalArgumentException("L'agentId doit être composé d'exactement 4 chiffres");
        }
    }

    private void validerCoherenceDureeEssai(StatutDossierEmploye statut, Integer dureeEssaiMois) {
        if (statut == StatutDossierEmploye.EN_PERIODE_ESSAI && dureeEssaiMois == null) {
            throw new IllegalArgumentException("dureeEssaiMois est obligatoire pour le statut EN_PERIODE_ESSAI");
        }
    }

    private void validerCoherenceNombreEnfants(
            com.example.Pointage_Cleanic.Enum.rh.SituationMatrimoniale situation, Integer nombreEnfants) {
        if (nombreEnfants != null && nombreEnfants < 0) {
            throw new IllegalArgumentException("nombreEnfants doit être >= 0");
        }
    }

    /**
     * Valide que {@code joursTravail}, s'il est présent, appartient à l'ensemble
     * des valeurs de {@link JoursTravail} ({@code LUN_VEN, LUN_SAM, LUN_DIM}).
     * Champ optionnel / nullable (rétro-compat) : {@code null}/blank est accepté.
     */
    private void validerJoursTravail(String joursTravail) {
        if (joursTravail == null || joursTravail.isBlank()) return;
        if (!estJoursTravailValide(joursTravail)) {
            throw new IllegalArgumentException(
                    "joursTravail invalide : " + joursTravail
                            + " (valeurs autorisées : LUN_VEN, LUN_SAM, LUN_DIM)");
        }
    }

    private boolean estJoursTravailValide(String joursTravail) {
        try {
            JoursTravail.valueOf(joursTravail);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void nettoyerChampsOptionnels(DossierEmploye d) {
        if (d.getStatut() != StatutDossierEmploye.EN_PERIODE_ESSAI) {
            d.setDureeEssaiMois(null);
        }
        // nombreEnfants n'est PAS remis à null hors MARIE : un divorcé, un veuf ou un
        // célibataire peuvent avoir des enfants. Seule la borne >= 0 est contrôlée
        // (validerCoherenceNombreEnfants).
    }

    // =========================================================================
    //  Affectations multi-sites + dérivation siteAffecte
    // =========================================================================

    /**
     * Applique les affectations reçues et maintient {@code siteAffecte} comme
     * champ dérivé (source de vérité serveur).
     * <ul>
     *   <li>Si {@code affectations} est présent/non vide : chaque affectation est
     *       validée (site obligatoire, cohérence horaire), la liste remplace
     *       intégralement l'existante, et {@code siteAffecte} est recalculé en
     *       joignant les noms de sites par {@code " - "} (ordre reçu). Le
     *       {@code siteAffecte} envoyé par le client est ignoré.</li>
     *   <li>Sinon (rétro-compat anciens clients) : on honore le {@code siteAffecte}
     *       reçu tel quel (s'il est fourni) et on en dérive des affectations sans
     *       horaires pour rester cohérent.</li>
     * </ul>
     */
    private void appliquerAffectations(DossierEmploye entity, DossierEmployeDto dto) {
        List<AffectationSiteDto> aff = dto.getAffectations();
        if (aff != null && !aff.isEmpty()) {
            aff.forEach(this::validerAffectation);
            entity.setAffectations(mapper.toAffectationEntities(aff));
            entity.setSiteAffecte(deriverSiteAffecte(entity.getAffectations()));
        } else {
            // Rétro-compat : la chaîne du client fait foi et reste conservée telle
            // quelle, séparateurs d'origine compris — on ne la normalise pas.
            if (dto.getSiteAffecte() != null) {
                entity.setSiteAffecte(dto.getSiteAffecte());
            }
            entity.setAffectations(
                    SiteAffecteUtils.affectationsDepuisSiteAffecte(entity.getSiteAffecte()));
        }
        // Une ligne nouvelle arrive sans id ; une ligne déjà persistée renvoie le sien,
        // que MapStruct a recopié. Les affectations dérivées de `siteAffecte` en
        // reçoivent un ici plutôt que dans SiteAffecteUtils, qui doit rester pure.
        AffectationSiteUtils.assurerIds(entity.getAffectations());
    }

    /**
     * {@code siteAffecte} ne liste que les sites <b>en cours</b>.
     * <p>
     * ⚠ Les affectations closes étant désormais conservées à vie, y joindre tous les
     * sites ferait grossir ce champ indéfiniment — or il alimente le filtre « Site »
     * de {@link #list} (regex sous-chaîne) : « qui travaille à Yoff ? » finirait par
     * remonter des agents partis depuis des années. Un agent dont tous les sites sont
     * clos a donc un {@code siteAffecte} vide, ce qui est exact : il n'est affecté
     * nulle part. Ses affectations structurées, elles, restent intactes.
     */
    private String deriverSiteAffecte(List<AffectationSite> affectations) {
        return AffectationSiteUtils.actives(affectations, LocalDate.now(clock)).stream()
                .map(AffectationSite::getSite)
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(SiteAffecteUtils.SEPARATEUR));
    }

    /**
     * Refuse une écriture qui ferait disparaître une affectation déjà close : elle
     * constitue l'historique de l'agent, et {@link #appliquerAffectations} remplace la
     * liste <b>en bloc</b>.
     * <p>
     * ⚠ Le rapprochement se fait sur la <b>clé naturelle</b> et non sur l'{@code id} :
     * un client qui ne renverrait pas les ids contournerait autrement la garde, et
     * celle-ci doit valoir dès le premier déploiement, avant tout backfill.
     * <p>
     * ⚠ Le cas « payload sans affectations » est couvert par la même règle : la branche
     * {@code else} d'{@link #appliquerAffectations} écrase alors la liste par celle
     * dérivée de {@code siteAffecte}, ce qui est bien une disparition.
     * <p>
     * Posée sur {@code update} seulement — créer un dossier avec un historique (reprise
     * de données) est légitime.
     */
    private void verifierAffectationsClosesConservees(List<AffectationSite> existantes,
                                                      List<AffectationSiteDto> entrantes) {
        List<AffectationSite> closes =
                AffectationSiteUtils.terminees(existantes, LocalDate.now(clock));
        if (closes.isEmpty()) {
            return;
        }

        Set<String> signaturesEntrantes = (entrantes == null ? List.<AffectationSiteDto>of() : entrantes)
                .stream()
                .filter(a -> a != null)
                .map(a -> AffectationSiteUtils.signature(
                        a.getSite(), a.getDateEntree(), a.getDateSortie()))
                .collect(Collectors.toSet());

        for (AffectationSite close : closes) {
            if (!signaturesEntrantes.contains(AffectationSiteUtils.signature(close))) {
                throw new AffectationInvalideException(
                        "L'affectation close sur " + close.getSite()
                                + " (du " + close.getDateEntree() + " au " + close.getDateSortie()
                                + ") ne peut pas être retirée du dossier.");
            }
        }
    }

    private void validerAffectation(AffectationSiteDto affectation) {
        if (affectation == null || affectation.getSite() == null || affectation.getSite().isBlank()) {
            throw new IllegalArgumentException("Le site de l'affectation est obligatoire");
        }
        String debut = affectation.getHoraireDebut();
        String fin = affectation.getHoraireFin();
        boolean debutPresent = debut != null && !debut.isBlank();
        boolean finPresent = fin != null && !fin.isBlank();
        // Contrainte de cohérence uniquement si les DEUX horaires sont présents.
        if (debutPresent && finPresent) {
            LocalTime tDebut = parseHeure(debut, affectation.getSite());
            LocalTime tFin = parseHeure(fin, affectation.getSite());
            if (!tDebut.isBefore(tFin)) {
                throw new IllegalArgumentException(
                        "horaireDebut doit être antérieur à horaireFin pour le site "
                                + affectation.getSite());
            }
        }

        // Semaine ouvrée du site : même oracle que le champ homonyme de l'employé.
        String joursTravail = affectation.getJoursTravail();
        if (joursTravail != null && !joursTravail.isBlank()
                && !estJoursTravailValide(joursTravail)) {
            throw new IllegalArgumentException(
                    "joursTravail invalide pour le site " + affectation.getSite()
                            + " : " + joursTravail
                            + " (valeurs autorisées : LUN_VEN, LUN_SAM, LUN_DIM)");
        }

        // Période de présence sur le site. Les deux bornes restent FACULTATIVES :
        // l'import bulk et SiteAffecteUtils.affectationsDepuisSiteAffecte produisent
        // des affectations sans aucune date, les exiger casserait ces deux flux.
        LocalDate dateEntree = affectation.getDateEntree();
        LocalDate dateSortie = affectation.getDateSortie();
        if (dateEntree != null && dateSortie != null && dateSortie.isBefore(dateEntree)) {
            throw new IllegalArgumentException(
                    "dateSortie doit être postérieure ou égale à dateEntree pour le site "
                            + affectation.getSite());
        }
    }

    private LocalTime parseHeure(String valeur, String site) {
        try {
            return LocalTime.parse(valeur);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Horaire invalide (format attendu HH:mm) pour le site " + site + " : " + valeur);
        }
    }

    // =========================================================================
    //  Import bulk — POST /api/dossier-employe/bulk
    // =========================================================================

    /**
     * Import massif de dossiers employés en un seul appel.
     * <p>
     * Deux stratégies possibles :
     * <ul>
     *   <li>{@link StrategieErreursImport#TOUT_OU_RIEN} : toute erreur de validation
     *       annule l'import (aucune insertion, rapport d'erreurs complet).</li>
     *   <li>{@link StrategieErreursImport#IMPORTER_LIGNES_VALIDES} : insère les
     *       lignes valides, rapporte les autres.</li>
     * </ul>
     * <p>
     * <b>Atomicité :</b> MongoDB en déploiement standalone (sans replica set) ne
     * supporte pas les transactions multi-documents. L'atomicité
     * {@code TOUT_OU_RIEN} est garantie par validation préalable du batch entier
     * avant tout {@code saveAll()}. Si {@code saveAll()} échoue à mi-parcours
     * (race condition sur l'index unique {@code matricule}, erreur I/O Mongo),
     * une {@link BulkInsertPartialFailureException} est levée avec la liste
     * des IDs déjà persistés — aucun rollback compensatoire n'est tenté.
     * <p>
     * Validation plus stricte que le CRUD unitaire sur un point : l'association
     * {@code situationMatrimoniale == MARIE} ⇒ {@code nombreEnfants} non null
     * est enforcée ici (alors que {@link #create} se contente de nullifier
     * {@code nombreEnfants} hors MARIE).
     * <p>
     * <b>Affectations :</b> l'import bulk ne gère PAS les tranches horaires
     * (comme la photo). Les {@code affectations} sont dérivées du
     * {@code siteAffecte} importé (sites sans horaires) via
     * {@link SiteAffecteUtils#affectationsDepuisSiteAffecte}, pour rester
     * cohérent avec le CRUD unitaire.
     *
     * @param request   requête contenant les lignes et la stratégie d'erreurs
     * @param userEmail email de l'utilisateur ayant déclenché l'import (audit log)
     * @return rapport détaillé (total / inserted / failed / insertedIds / errors)
     */
    public DossierEmployeBulkImportResponse importBulk(
            DossierEmployeBulkImportRequest request, String userEmail) {

        long t0 = System.currentTimeMillis();

        validerGardeFousRequete(request);

        List<DossierEmployeBulkLigneDto> lignes = request.employes();
        int total = lignes.size();
        Map<Integer, List<DossierEmployeImportError>> errorsByLine = new LinkedHashMap<>();
        for (int i = 0; i < total; i++) {
            errorsByLine.put(i, new ArrayList<>());
        }

        // Passe 1 : validations unitaires (champs obligatoires, cohérences, valeurs).
        for (int i = 0; i < total; i++) {
            validerLigne(i, lignes.get(i), errorsByLine);
        }

        // Passe 2 : unicité matricule + agentId DANS le payload.
        detecterDoublonsMatriculePayload(lignes, errorsByLine);
        detecterDoublonsAgentIdPayload(lignes, errorsByLine);

        // Passe 3 : unicité matricule + agentId EN BASE (une requête Mongo chacun).
        detecterDoublonsMatriculeBase(lignes, errorsByLine);
        detecterDoublonsAgentIdBase(lignes, errorsByLine);

        // Passe 4 : résolution des supérieurs hiérarchiques + détection cycles.
        Map<String, DossierEmploye> superieursBaseParMatricule =
                resoudreSuperieursEnBase(lignes, errorsByLine);
        Map<Integer, String> supInternesAResoudre = new HashMap<>();
        detecterCyclesSuperieurs(lignes, errorsByLine, superieursBaseParMatricule, supInternesAResoudre);

        // Sélection des lignes valides (sans erreur).
        List<Integer> indicesValides = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            if (errorsByLine.get(i).isEmpty()) {
                indicesValides.add(i);
            }
        }

        List<DossierEmployeImportError> allErrors = errorsByLine.values().stream()
                .flatMap(List::stream).collect(Collectors.toList());

        // Branchement stratégie.
        if (!allErrors.isEmpty() && request.strategieErreurs() == StrategieErreursImport.TOUT_OU_RIEN) {
            long durationMs = System.currentTimeMillis() - t0;
            log.info("Import bulk RH annulé (TOUT_OU_RIEN) : total={}, failed={}, durée={}ms, user={}",
                    total, allErrors.size(), durationMs, userEmail);
            return new DossierEmployeBulkImportResponse(
                    total, 0, allErrors.size(), Collections.emptyList(), allErrors);
        }

        if (indicesValides.isEmpty()) {
            long durationMs = System.currentTimeMillis() - t0;
            log.info("Import bulk RH : aucune ligne valide à insérer, total={}, failed={}, durée={}ms, user={}",
                    total, allErrors.size(), durationMs, userEmail);
            return new DossierEmployeBulkImportResponse(
                    total, 0, allErrors.size(), Collections.emptyList(), allErrors);
        }

        // Préparation des entités à insérer.
        List<DossierEmploye> aInserer = new ArrayList<>(indicesValides.size());
        for (int i : indicesValides) {
            DossierEmployeBulkLigneDto dto = lignes.get(i);
            DossierEmploye entity = mapper.toEntity(dto);
            // Résolution du supérieur en base (connu à cette étape).
            if (dto.getSuperieurHierarchiqueMatricule() != null
                    && superieursBaseParMatricule.containsKey(dto.getSuperieurHierarchiqueMatricule())) {
                DossierEmploye sup = superieursBaseParMatricule.get(dto.getSuperieurHierarchiqueMatricule());
                entity.setSuperieurHierarchiqueId(sup.getId());
                entity.setSuperieurHierarchiqueNom(buildNomComplet(sup.getNom(), sup.getPrenom()));
            }
            nettoyerChampsOptionnels(entity);
            // Import bulk : pas de gestion des horaires (comme la photo). Les
            // affectations sont dérivées du siteAffecte importé (sites sans
            // horaires) pour rester cohérent avec le CRUD unitaire.
            entity.setAffectations(
                    SiteAffecteUtils.affectationsDepuisSiteAffecte(entity.getSiteAffecte()));
            // Troisième point de création d'affectations (avec appliquerAffectations et
            // le backfill) : leur poser un id ici évite d'attendre le prochain démarrage.
            AffectationSiteUtils.assurerIds(entity.getAffectations());
            aInserer.add(entity);
        }

        // Insertion batch.
        List<String> insertedIds;
        try {
            List<DossierEmploye> saved = dossierEmployeRepository.saveAll(aInserer);
            insertedIds = saved.stream().map(DossierEmploye::getId).toList();

            // Pass B : résoudre les supérieurs INTERNES au payload (maintenant que les IDs existent).
            Map<String, DossierEmploye> savedParMatricule = saved.stream()
                    .collect(Collectors.toMap(DossierEmploye::getMatricule, d -> d));
            for (Map.Entry<Integer, String> entry : supInternesAResoudre.entrySet()) {
                int indexLigne = entry.getKey();
                String matriculeSup = entry.getValue();
                DossierEmploye ligneSauvee = savedParMatricule.get(lignes.get(indexLigne).getMatricule());
                DossierEmploye superieurSauve = savedParMatricule.get(matriculeSup);
                if (ligneSauvee == null || superieurSauve == null) continue;
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(ligneSauvee.getId())),
                        new Update()
                                .set("superieurHierarchiqueId", superieurSauve.getId())
                                .set("superieurHierarchiqueNom",
                                        buildNomComplet(superieurSauve.getNom(), superieurSauve.getPrenom())),
                        DossierEmploye.class);
            }
        } catch (RuntimeException ex) {
            // Tentative de récupération des IDs déjà persistés (race / échec partiel).
            List<String> idsDejaInseres;
            try {
                idsDejaInseres = dossierEmployeRepository.findByMatriculeIn(
                                aInserer.stream().map(DossierEmploye::getMatricule).toList())
                        .stream().map(DossierEmploye::getId).toList();
            } catch (RuntimeException lookupEx) {
                idsDejaInseres = Collections.emptyList();
            }
            log.error("Import bulk RH : échec saveAll à mi-parcours, user={}, idsDejaInseres={}",
                    userEmail, idsDejaInseres, ex);
            throw new BulkInsertPartialFailureException(
                    "Échec de l'insertion bulk à mi-parcours : " + ex.getMessage(),
                    idsDejaInseres, ex);
        }

        long durationMs = System.currentTimeMillis() - t0;
        log.info("Import bulk RH terminé : total={}, inserted={}, failed={}, durée={}ms, user={}",
                total, insertedIds.size(), allErrors.size(), durationMs, userEmail);
        if (log.isDebugEnabled() && !allErrors.isEmpty()) {
            allErrors.forEach(e -> log.debug("  erreur: index={} matricule={} field={} code={} msg={}",
                    e.index(), e.matricule(), e.field(), e.code(), e.message()));
        }

        return new DossierEmployeBulkImportResponse(
                total, insertedIds.size(), allErrors.size(), insertedIds, allErrors);
    }

    private void validerGardeFousRequete(DossierEmployeBulkImportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La requête d'import est obligatoire");
        }
        if (request.employes() == null || request.employes().isEmpty()) {
            throw new IllegalArgumentException("Le batch d'import ne peut pas être vide");
        }
        if (request.employes().size() > bulkMaxSize) {
            throw new IllegalArgumentException(
                    "Taille du batch supérieure à la limite autorisée : "
                            + request.employes().size() + " > " + bulkMaxSize);
        }
    }

    private void validerLigne(int index, DossierEmployeBulkLigneDto dto,
                               Map<Integer, List<DossierEmployeImportError>> errorsByLine) {
        if (dto == null) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, null, null, "CHAMP_OBLIGATOIRE", "La ligne est vide"));
            return;
        }
        exigerNonBlank(errorsByLine, index, dto, "matricule", dto.getMatricule());
        if (dto.getAgentId() == null || dto.getAgentId().isBlank()) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, dto.getMatricule(), "agentId", "AGENT_ID_OBLIGATOIRE",
                    "L'agentId est obligatoire"));
        } else if (!AGENT_ID_PATTERN.matcher(dto.getAgentId()).matches()) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, dto.getMatricule(), "agentId", "AGENT_ID_FORMAT_INVALIDE",
                    "L'agentId doit être composé d'exactement 4 chiffres"));
        }
        exigerNonBlank(errorsByLine, index, dto, "nom", dto.getNom());
        exigerNonBlank(errorsByLine, index, dto, "prenom", dto.getPrenom());
        exigerNonBlank(errorsByLine, index, dto, "poste", dto.getPoste());
        if (dto.getDateEmbauche() == null) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, dto.getMatricule(), "dateEmbauche", "CHAMP_OBLIGATOIRE",
                    "La date d'embauche est obligatoire"));
        }
        if (dto.getStatut() == null) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, dto.getMatricule(), "statut", "CHAMP_OBLIGATOIRE",
                    "Le statut est obligatoire"));
        }
        if (dto.getGenre() == null) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, dto.getMatricule(), "genre", "CHAMP_OBLIGATOIRE",
                    "Le genre est obligatoire"));
        }

        if (dto.getNombreEnfants() != null && dto.getNombreEnfants() < 0) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, dto.getMatricule(), "nombreEnfants", "VALEUR_INVALIDE",
                    "nombreEnfants doit être >= 0"));
        }

        if (dto.getStatut() == StatutDossierEmploye.EN_PERIODE_ESSAI
                && dto.getDureeEssaiMois() == null) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, dto.getMatricule(), "dureeEssaiMois", "VALIDATION_CONDITIONNELLE",
                    "dureeEssaiMois est obligatoire lorsque statut = EN_PERIODE_ESSAI"));
        }
        if (dto.getDureeEssaiMois() != null && dto.getDureeEssaiMois() < 1) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, dto.getMatricule(), "dureeEssaiMois", "VALEUR_INVALIDE",
                    "dureeEssaiMois doit être >= 1"));
        }

        if (dto.getSituationMatrimoniale() == SituationMatrimoniale.MARIE
                && dto.getNombreEnfants() == null) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, dto.getMatricule(), "nombreEnfants", "VALIDATION_CONDITIONNELLE",
                    "nombreEnfants est obligatoire lorsque situationMatrimoniale = MARIE"));
        }

        // joursTravail : optionnel (le front ne l'importe pas). Validé seulement
        // s'il est présent, pour ne pas casser le template existant.
        String joursTravail = dto.getJoursTravail();
        if (joursTravail != null && !joursTravail.isBlank() && !estJoursTravailValide(joursTravail)) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, dto.getMatricule(), "joursTravail", "VALEUR_INVALIDE",
                    "joursTravail invalide (valeurs autorisées : LUN_VEN, LUN_SAM, LUN_DIM)"));
        }
    }

    private void exigerNonBlank(Map<Integer, List<DossierEmployeImportError>> errorsByLine,
                                 int index, DossierEmployeBulkLigneDto dto,
                                 String field, String value) {
        if (value == null || value.isBlank()) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, dto.getMatricule(), field, "CHAMP_OBLIGATOIRE",
                    "Le champ " + field + " est obligatoire"));
        }
    }

    private void detecterDoublonsMatriculePayload(List<DossierEmployeBulkLigneDto> lignes,
                                                    Map<Integer, List<DossierEmployeImportError>> errorsByLine) {
        Map<String, List<Integer>> indicesParMatricule = new HashMap<>();
        for (int i = 0; i < lignes.size(); i++) {
            String m = lignes.get(i).getMatricule();
            if (m == null || m.isBlank()) continue;
            indicesParMatricule.computeIfAbsent(m, k -> new ArrayList<>()).add(i);
        }
        indicesParMatricule.forEach((matricule, indices) -> {
            if (indices.size() < 2) return;
            for (int idx : indices) {
                errorsByLine.get(idx).add(new DossierEmployeImportError(
                        idx, matricule, "matricule", "MATRICULE_DUPLIQUE_PAYLOAD",
                        "Le matricule " + matricule + " apparaît plusieurs fois dans le batch"));
            }
        });
    }

    private void detecterDoublonsAgentIdPayload(List<DossierEmployeBulkLigneDto> lignes,
                                                  Map<Integer, List<DossierEmployeImportError>> errorsByLine) {
        Map<String, List<Integer>> indicesParAgentId = new HashMap<>();
        for (int i = 0; i < lignes.size(); i++) {
            String a = lignes.get(i).getAgentId();
            if (a == null || a.isBlank()) continue;
            indicesParAgentId.computeIfAbsent(a, k -> new ArrayList<>()).add(i);
        }
        indicesParAgentId.forEach((agentId, indices) -> {
            if (indices.size() < 2) return;
            for (int idx : indices) {
                errorsByLine.get(idx).add(new DossierEmployeImportError(
                        idx, lignes.get(idx).getMatricule(), "agentId", "AGENT_ID_DUPLIQUE_PAYLOAD",
                        "L'agentId " + agentId + " apparaît plusieurs fois dans le batch"));
            }
        });
    }

    private void detecterDoublonsAgentIdBase(List<DossierEmployeBulkLigneDto> lignes,
                                               Map<Integer, List<DossierEmployeImportError>> errorsByLine) {
        Set<String> agentIdsNonVides = lignes.stream()
                .map(DossierEmployeBulkLigneDto::getAgentId)
                .filter(a -> a != null && !a.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (agentIdsNonVides.isEmpty()) return;

        Set<String> agentIdsEnBase = dossierEmployeRepository
                .findByAgentIdIn(agentIdsNonVides)
                .stream()
                .map(DossierEmploye::getAgentId)
                .collect(Collectors.toSet());

        for (int i = 0; i < lignes.size(); i++) {
            String a = lignes.get(i).getAgentId();
            if (a != null && agentIdsEnBase.contains(a)) {
                errorsByLine.get(i).add(new DossierEmployeImportError(
                        i, lignes.get(i).getMatricule(), "agentId", "AGENT_ID_DUPLIQUE_BASE",
                        "Un dossier existe déjà en base avec l'agentId " + a));
            }
        }
    }

    private void detecterDoublonsMatriculeBase(List<DossierEmployeBulkLigneDto> lignes,
                                                 Map<Integer, List<DossierEmployeImportError>> errorsByLine) {
        Set<String> matriculesNonVides = lignes.stream()
                .map(DossierEmployeBulkLigneDto::getMatricule)
                .filter(m -> m != null && !m.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (matriculesNonVides.isEmpty()) return;

        Set<String> matriculesEnBase = dossierEmployeRepository
                .findByMatriculeIn(matriculesNonVides)
                .stream()
                .map(DossierEmploye::getMatricule)
                .collect(Collectors.toSet());

        for (int i = 0; i < lignes.size(); i++) {
            String m = lignes.get(i).getMatricule();
            if (m != null && matriculesEnBase.contains(m)) {
                errorsByLine.get(i).add(new DossierEmployeImportError(
                        i, m, "matricule", "MATRICULE_DUPLIQUE_BASE",
                        "Un dossier existe déjà en base avec le matricule " + m));
            }
        }
    }

    /**
     * Résout les supérieurs référencés par matricule qui ne sont PAS dans le payload :
     * ils doivent exister en base. Retourne le mapping matricule → DossierEmploye (base).
     * Ajoute une erreur SUPERIEUR_INEXISTANT si un matricule de supérieur n'est trouvé
     * ni dans le payload ni en base.
     */
    private Map<String, DossierEmploye> resoudreSuperieursEnBase(
            List<DossierEmployeBulkLigneDto> lignes,
            Map<Integer, List<DossierEmployeImportError>> errorsByLine) {

        Set<String> matriculesPayload = lignes.stream()
                .map(DossierEmployeBulkLigneDto::getMatricule)
                .filter(m -> m != null && !m.isBlank())
                .collect(Collectors.toSet());

        // Ensemble des matricules de supérieurs qui ne sont pas satisfaits par le payload.
        Set<String> matriculesSupAResoudre = lignes.stream()
                .map(DossierEmployeBulkLigneDto::getSuperieurHierarchiqueMatricule)
                .filter(m -> m != null && !m.isBlank())
                .filter(m -> !matriculesPayload.contains(m))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, DossierEmploye> parMatricule = new HashMap<>();
        if (!matriculesSupAResoudre.isEmpty()) {
            dossierEmployeRepository.findByMatriculeIn(matriculesSupAResoudre)
                    .forEach(d -> parMatricule.put(d.getMatricule(), d));
        }

        for (int i = 0; i < lignes.size(); i++) {
            String supMat = lignes.get(i).getSuperieurHierarchiqueMatricule();
            if (supMat == null || supMat.isBlank()) continue;
            boolean danlePayload = matriculesPayload.contains(supMat);
            boolean enBase = parMatricule.containsKey(supMat);
            if (!danlePayload && !enBase) {
                errorsByLine.get(i).add(new DossierEmployeImportError(
                        i, lignes.get(i).getMatricule(), "superieurHierarchiqueMatricule",
                        "SUPERIEUR_INEXISTANT",
                        "Le supérieur avec matricule " + supMat + " n'existe ni dans le batch ni en base"));
            }
        }
        return parMatricule;
    }

    /**
     * DFS 3-couleurs sur le graphe {ligne → ligne supérieure} pour les arêtes
     * strictement internes au payload. Toute ligne appartenant à un cycle
     * reçoit une erreur {@code REFERENCE_CIRCULAIRE}.
     * Alimente également {@code supInternesAResoudre} avec les arêtes non cycliques
     * qui devront être traitées en Pass B post-insert.
     */
    private void detecterCyclesSuperieurs(
            List<DossierEmployeBulkLigneDto> lignes,
            Map<Integer, List<DossierEmployeImportError>> errorsByLine,
            Map<String, DossierEmploye> superieursBaseParMatricule,
            Map<Integer, String> supInternesAResoudre) {

        Map<String, Integer> indexParMatricule = new HashMap<>();
        for (int i = 0; i < lignes.size(); i++) {
            String m = lignes.get(i).getMatricule();
            if (m != null && !m.isBlank() && !indexParMatricule.containsKey(m)) {
                // En cas de doublon payload, on ne garde que la première occurrence
                // pour le graphe — les doublons sont déjà signalés par ailleurs.
                indexParMatricule.put(m, i);
            }
        }

        // Construction du graphe (arêtes intra-payload uniquement).
        Map<Integer, Integer> parent = new HashMap<>();
        for (int i = 0; i < lignes.size(); i++) {
            String supMat = lignes.get(i).getSuperieurHierarchiqueMatricule();
            if (supMat == null || supMat.isBlank()) continue;
            if (superieursBaseParMatricule.containsKey(supMat)) continue; // arête externe, skip
            Integer idxSup = indexParMatricule.get(supMat);
            if (idxSup == null) continue; // SUPERIEUR_INEXISTANT déjà signalé
            if (idxSup == i) {
                // auto-référence = cycle trivial
                errorsByLine.get(i).add(new DossierEmployeImportError(
                        i, lignes.get(i).getMatricule(), "superieurHierarchiqueMatricule",
                        "REFERENCE_CIRCULAIRE",
                        "Référence circulaire : la ligne se désigne elle-même comme supérieur"));
                continue;
            }
            parent.put(i, idxSup);
            supInternesAResoudre.put(i, supMat);
        }

        // DFS 3-couleurs : 0=blanc, 1=gris (en cours), 2=noir (fini).
        int n = lignes.size();
        int[] color = new int[n];
        Set<Integer> enCycle = new HashSet<>();
        for (int start = 0; start < n; start++) {
            if (color[start] != 0) continue;
            // Parcours itératif le long de la chaîne parent.
            List<Integer> pile = new ArrayList<>();
            int cur = start;
            while (cur != -1 && color[cur] == 0) {
                color[cur] = 1;
                pile.add(cur);
                Integer next = parent.get(cur);
                cur = (next == null) ? -1 : next;
            }
            if (cur != -1 && color[cur] == 1) {
                // cycle détecté : cur est le point d'entrée, tous les éléments
                // de pile à partir de cur sont dans le cycle.
                int idxCur = pile.indexOf(cur);
                for (int k = idxCur; k < pile.size(); k++) {
                    enCycle.add(pile.get(k));
                }
            }
            for (int p : pile) color[p] = 2;
        }

        for (int idx : enCycle) {
            // Nettoyer l'arête "interne à résoudre" pour cette ligne : elle n'est pas insérable.
            supInternesAResoudre.remove(idx);
            errorsByLine.get(idx).add(new DossierEmployeImportError(
                    idx, lignes.get(idx).getMatricule(), "superieurHierarchiqueMatricule",
                    "REFERENCE_CIRCULAIRE",
                    "Référence circulaire détectée entre plusieurs lignes du batch"));
        }
    }

    private String buildNomComplet(String nom, String prenom) {
        StringBuilder sb = new StringBuilder();
        if (prenom != null && !prenom.isBlank()) sb.append(prenom.trim());
        if (nom != null && !nom.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(nom.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}