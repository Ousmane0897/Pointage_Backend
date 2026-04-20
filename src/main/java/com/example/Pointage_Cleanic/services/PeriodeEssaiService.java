package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.PeriodeEssaiDto;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PeriodeEssaiService {

    private final EmployeCompletRepository employeCompletRepository;

    public List<PeriodeEssaiDto> getAlertes() {
        return employeCompletRepository
                .findByDureeEssaiMoisIsNotNullAndEssaiTermineIsNot(Boolean.TRUE)
                .stream()
                .filter(e -> e.getDateEmbauche() != null)
                .map(this::toDto)
                .sorted(Comparator.comparingLong(PeriodeEssaiDto::getJoursRestants))
                .collect(Collectors.toList());
    }

    public PeriodeEssaiDto titulariser(String id) {
        EmployeComplet emp = employeCompletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employé introuvable : " + id));
        emp.setEssaiTermine(true);
        return toDto(employeCompletRepository.save(emp));
    }

    private PeriodeEssaiDto toDto(EmployeComplet emp) {
        LocalDate dateFinEssai = emp.getDateEmbauche().plusMonths(emp.getDureeEssaiMois());
        long joursRestants = ChronoUnit.DAYS.between(LocalDate.now(), dateFinEssai);

        String statut;
        if (Boolean.TRUE.equals(emp.getEssaiTermine())) {
            statut = "TITULARISE";
        } else if (joursRestants < 0) {
            statut = "EXPIRE";
        } else {
            statut = "EN_ESSAI";
        }

        return PeriodeEssaiDto.builder()
                .id(emp.getId())
                .agentId(emp.getAgentId())
                .nomComplet(emp.getNomComplet())
                .poste(emp.getPoste())
                .dateEmbauche(emp.getDateEmbauche())
                .dureeEssaiMois(emp.getDureeEssaiMois())
                .dateFinEssaiCalculee(dateFinEssai)
                .joursRestants(joursRestants)
                .essaiTermine(emp.getEssaiTermine())
                .statut(statut)
                .build();
    }
}