package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.AlertePeriodeEssaiDossierDto;
import com.example.Pointage_Cleanic.Dto.DossierEmployeBulkImportRequest;
import com.example.Pointage_Cleanic.Dto.DossierEmployeBulkImportResponse;
import com.example.Pointage_Cleanic.Dto.DossierEmployeBulkLigneDto;
import com.example.Pointage_Cleanic.Dto.DossierEmployeDto;
import com.example.Pointage_Cleanic.Dto.DossierEmployeImportError;
import com.example.Pointage_Cleanic.Dto.DossierEmployeStatutRequest;
import com.example.Pointage_Cleanic.Enum.SituationMatrimoniale;
import com.example.Pointage_Cleanic.Enum.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Enum.StrategieErreursImport;
import com.example.Pointage_Cleanic.Mapper.DossierEmployeMapper;
import com.example.Pointage_Cleanic.entities.DossierEmploye;
import com.example.Pointage_Cleanic.exception.BulkInsertPartialFailureException;
import com.example.Pointage_Cleanic.exception.EmployeAlreadyExistsException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.DossierEmployeRepository;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    @Value("${rh.import.bulk.max-size:1000}")
    private int bulkMaxSize;

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
            criterias.add(Criteria.where("siteAffecte").regex("^" + site + "$", "i"));
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
        validerCoherenceDureeEssai(dto.getStatut(), dto.getDureeEssaiMois());
        validerCoherenceNombreEnfants(dto.getSituationMatrimoniale(), dto.getNombreEnfants());

        DossierEmploye entity = mapper.toEntity(dto);
        nettoyerChampsOptionnels(entity);

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

        mapper.updateEntityFromDto(dto, existing);
        // Règles de cohérence post-mise-à-jour
        validerCoherenceDureeEssai(existing.getStatut(), existing.getDureeEssaiMois());
        validerCoherenceNombreEnfants(existing.getSituationMatrimoniale(), existing.getNombreEnfants());
        nettoyerChampsOptionnels(existing);

        if (photo != null && !photo.isEmpty()) {
            existing.setPhoto(photo.getBytes());
        }

        return toDtoWithUrl(dossierEmployeRepository.save(existing));
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

        return toDtoWithUrl(dossierEmployeRepository.save(existing));
    }

    public DossierEmployeDto titulariser(String id) {
        DossierEmploye existing = requireById(id);
        existing.setStatut(StatutDossierEmploye.ACTIF);
        existing.setDureeEssaiMois(null);
        return toDtoWithUrl(dossierEmployeRepository.save(existing));
    }

    public byte[] getPhoto(String id) {
        DossierEmploye d = requireById(id);
        return d.getPhoto();
    }

    public List<AlertePeriodeEssaiDossierDto> getAlertesPeriodeEssai() {
        LocalDate today = LocalDate.now();
        return dossierEmployeRepository
                .findByStatutAndDureeEssaiMoisIsNotNull(StatutDossierEmploye.EN_PERIODE_ESSAI)
                .stream()
                .map(d -> toAlerteDto(d, today))
                .toList();
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

    private AlertePeriodeEssaiDossierDto toAlerteDto(DossierEmploye d, LocalDate today) {
        LocalDate dateFin = null;
        long joursRestants = 0;
        if (d.getDateEntree() != null && d.getDureeEssaiMois() != null) {
            dateFin = d.getDateEntree().plusMonths(d.getDureeEssaiMois());
            joursRestants = ChronoUnit.DAYS.between(today, dateFin);
        }
        String alerteStatut = joursRestants < 0 ? "EXPIRE"
                : joursRestants <= 15 ? "IMMINENT"
                : "EN_ESSAI";

        return AlertePeriodeEssaiDossierDto.builder()
                .id(d.getId())
                .matricule(d.getMatricule())
                .nom(d.getNom())
                .prenom(d.getPrenom())
                .poste(d.getPoste())
                .departement(d.getDepartement())
                .dateEntree(d.getDateEntree())
                .dureeEssaiMois(d.getDureeEssaiMois())
                .dateFinEssaiCalculee(dateFin)
                .joursRestants(joursRestants)
                .statut(alerteStatut)
                .build();
    }

    private void validerCoherenceDureeEssai(StatutDossierEmploye statut, Integer dureeEssaiMois) {
        if (statut == StatutDossierEmploye.EN_PERIODE_ESSAI && dureeEssaiMois == null) {
            throw new IllegalArgumentException("dureeEssaiMois est obligatoire pour le statut EN_PERIODE_ESSAI");
        }
    }

    private void validerCoherenceNombreEnfants(
            com.example.Pointage_Cleanic.Enum.SituationMatrimoniale situation, Integer nombreEnfants) {
        if (nombreEnfants != null && nombreEnfants < 0) {
            throw new IllegalArgumentException("nombreEnfants doit être >= 0");
        }
    }

    private void nettoyerChampsOptionnels(DossierEmploye d) {
        if (d.getStatut() != StatutDossierEmploye.EN_PERIODE_ESSAI) {
            d.setDureeEssaiMois(null);
        }
        if (d.getSituationMatrimoniale() != com.example.Pointage_Cleanic.Enum.SituationMatrimoniale.MARIE) {
            d.setNombreEnfants(null);
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

        // Passe 2 : unicité matricule DANS le payload.
        detecterDoublonsMatriculePayload(lignes, errorsByLine);

        // Passe 3 : unicité matricule EN BASE (une seule requête Mongo).
        detecterDoublonsMatriculeBase(lignes, errorsByLine);

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
        if (request.strategieErreurs() == null) {
            throw new IllegalArgumentException(
                    "strategieErreurs est obligatoire (TOUT_OU_RIEN ou IMPORTER_LIGNES_VALIDES)");
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
        exigerNonBlank(errorsByLine, index, dto, "nom", dto.getNom());
        exigerNonBlank(errorsByLine, index, dto, "prenom", dto.getPrenom());
        exigerNonBlank(errorsByLine, index, dto, "poste", dto.getPoste());
        if (dto.getDateEntree() == null) {
            errorsByLine.get(index).add(new DossierEmployeImportError(
                    index, dto.getMatricule(), "dateEntree", "CHAMP_OBLIGATOIRE",
                    "La date d'entrée est obligatoire"));
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