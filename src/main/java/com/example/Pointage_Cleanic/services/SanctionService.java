package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.AlerteRecidiveDto;
import com.example.Pointage_Cleanic.Dto.SanctionDto;
import com.example.Pointage_Cleanic.Enum.StatutSanction;
import com.example.Pointage_Cleanic.Enum.TypeSanction;
import com.example.Pointage_Cleanic.Mapper.SanctionMapper;
import com.example.Pointage_Cleanic.entities.Sanction;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.SanctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SanctionService {

    private static final int DUREE_MISE_A_PIED_MAX_JOURS = 8;
    private static final int DELAI_CONVOCATION_ENTRETIEN_MIN_JOURS = 3;
    private static final int FENETRE_RECIDIVE_MOIS = 12;
    private static final int SEUIL_RECIDIVE = 2;

    private final SanctionRepository sanctionRepository;
    private final SanctionMapper sanctionMapper;

    public SanctionDto create(SanctionDto dto) {
        Sanction entity = sanctionMapper.toEntity(dto);
        validateProcedure(entity);
        if (entity.getDateCreation() == null) entity.setDateCreation(LocalDate.now());
        if (entity.getStatut() == null) entity.setStatut(StatutSanction.CONVOCATION);
        entity.setDelaiRespectJours(computeDelaiRespect(entity));
        return sanctionMapper.toDto(sanctionRepository.save(entity));
    }

    public SanctionDto getById(String id) {
        return sanctionMapper.toDto(findEntity(id));
    }

    public List<SanctionDto> search(String employeId, TypeSanction type, StatutSanction statut,
                                    String departement, LocalDate dateDebut, LocalDate dateFin) {
        List<Sanction> list;
        if (employeId != null) {
            list = sanctionRepository.findByEmployeIdOrderByDateSanctionDesc(employeId);
        } else if (departement != null && dateDebut != null && dateFin != null) {
            list = sanctionRepository.findByDepartementAndDateSanctionBetween(departement, dateDebut, dateFin);
        } else if (dateDebut != null && dateFin != null) {
            list = sanctionRepository.findByDateSanctionBetween(dateDebut, dateFin);
        } else if (type != null) {
            list = sanctionRepository.findByType(type);
        } else if (statut != null) {
            list = sanctionRepository.findByStatut(statut);
        } else if (departement != null) {
            list = sanctionRepository.findByDepartement(departement);
        } else {
            list = sanctionRepository.findAll();
        }
        return list.stream()
                .filter(s -> type == null || s.getType() == type)
                .filter(s -> statut == null || s.getStatut() == statut)
                .filter(s -> departement == null || departement.equals(s.getDepartement()))
                .map(sanctionMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<SanctionDto> historique(String employeId) {
        return sanctionRepository.findByEmployeIdOrderByDateSanctionDesc(employeId).stream()
                .map(sanctionMapper::toDto).collect(Collectors.toList());
    }

    public SanctionDto update(String id, SanctionDto dto) {
        Sanction existing = findEntity(id);
        sanctionMapper.updateEntityFromDto(dto, existing);
        validateProcedure(existing);
        existing.setDelaiRespectJours(computeDelaiRespect(existing));
        return sanctionMapper.toDto(sanctionRepository.save(existing));
    }

    public SanctionDto changerStatut(String id, StatutSanction statut) {
        Sanction existing = findEntity(id);
        existing.setStatut(statut);
        return sanctionMapper.toDto(sanctionRepository.save(existing));
    }

    public void delete(String id) {
        sanctionRepository.delete(findEntity(id));
    }

    /**
     * Détecte la récidive pour un employé donné : ≥ 2 sanctions du même type
     * sur une fenêtre glissante de 12 mois avant la date de référence.
     */
    public boolean estRecidiviste(String employeId, TypeSanction type, LocalDate dateReference) {
        LocalDate debutFenetre = dateReference.minusMonths(FENETRE_RECIDIVE_MOIS);
        long count = sanctionRepository.countByEmployeIdAndTypeAndDateSanctionBetween(
                employeId, type, debutFenetre, dateReference);
        return count >= SEUIL_RECIDIVE;
    }

    /**
     * Liste les employés en situation de récidive sur les 12 derniers mois,
     * tous types confondus (≥ 2 sanctions quelque soit le type).
     */
    public List<AlerteRecidiveDto> alertesRecidive() {
        LocalDate aujourdhui = LocalDate.now();
        LocalDate debutFenetre = aujourdhui.minusMonths(FENETRE_RECIDIVE_MOIS);

        Map<String, List<Sanction>> parEmploye = sanctionRepository
                .findByDateSanctionBetween(debutFenetre, aujourdhui).stream()
                .collect(Collectors.groupingBy(Sanction::getEmployeId));

        List<AlerteRecidiveDto> alertes = new ArrayList<>();
        for (Map.Entry<String, List<Sanction>> e : parEmploye.entrySet()) {
            List<Sanction> sanctions = e.getValue();
            if (sanctions.size() < SEUIL_RECIDIVE) continue;

            Sanction derniere = sanctions.stream()
                    .max(Comparator.comparing(Sanction::getDateSanction))
                    .orElseThrow();
            alertes.add(AlerteRecidiveDto.builder()
                    .employeId(e.getKey())
                    .nom(derniere.getNom())
                    .prenom(derniere.getPrenom())
                    .nombreSanctions(sanctions.size())
                    .derniereDate(derniere.getDateSanction())
                    .derniereType(derniere.getType())
                    .message("Récidive sur 12 mois : " + sanctions.size() + " sanctions")
                    .build());
        }
        return alertes;
    }

    // ====================== Helpers ======================

    private void validateProcedure(Sanction s) {
        if (s.getType() == TypeSanction.MISE_A_PIED && s.getDureeMiseAPied() != null
                && s.getDureeMiseAPied() > DUREE_MISE_A_PIED_MAX_JOURS) {
            throw new IllegalArgumentException(
                    "Mise à pied plafonnée à " + DUREE_MISE_A_PIED_MAX_JOURS
                            + " jours (Code du Travail sénégalais)");
        }
    }

    private Integer computeDelaiRespect(Sanction s) {
        if (s.getDateConvocation() == null || s.getDateEntretien() == null) return null;
        return (int) ChronoUnit.DAYS.between(s.getDateConvocation(), s.getDateEntretien());
    }

    private Sanction findEntity(String id) {
        return sanctionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sanction introuvable : " + id));
    }
}