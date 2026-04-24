package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.AlertePeriodeEssaiDossierDto;
import com.example.Pointage_Cleanic.Dto.DossierEmployeDto;
import com.example.Pointage_Cleanic.Dto.DossierEmployeStatutRequest;
import com.example.Pointage_Cleanic.Enum.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Mapper.DossierEmployeMapper;
import com.example.Pointage_Cleanic.entities.DossierEmploye;
import com.example.Pointage_Cleanic.exception.EmployeAlreadyExistsException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.DossierEmployeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DossierEmployeService {

    private final DossierEmployeRepository dossierEmployeRepository;
    private final DossierEmployeMapper mapper;
    private final MongoTemplate mongoTemplate;

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
}