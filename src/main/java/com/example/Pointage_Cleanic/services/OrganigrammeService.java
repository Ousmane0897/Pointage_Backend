package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.OrganigrammeArbreDto;
import com.example.Pointage_Cleanic.Dto.OrganigrammeNodeDto;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganigrammeService {

    private final EmployeCompletRepository employeCompletRepository;

    public List<OrganigrammeNodeDto> getOrganigramme(String departement) {
        List<EmployeComplet> employes;

        if (departement != null && !departement.isBlank()) {
            employes = employeCompletRepository.findByStatutAndAgenceContaining(
                    EmployeComplet.StatutEmploye.ACTIF, departement);
        } else {
            employes = employeCompletRepository.findByStatut(EmployeComplet.StatutEmploye.ACTIF);
        }

        return employes.stream()
                .map(this::toNodeDto)
                .sorted(Comparator.comparing(OrganigrammeNodeDto::getPoste, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OrganigrammeNodeDto::getNomComplet, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    public List<OrganigrammeArbreDto> getArbre() {
        List<EmployeComplet> employes = employeCompletRepository.findByStatut(EmployeComplet.StatutEmploye.ACTIF);

        Map<String, List<EmployeComplet>> parManager = employes.stream()
                .collect(Collectors.groupingBy(e ->
                        e.getManagerOps() != null && !e.getManagerOps().isBlank()
                                ? e.getManagerOps()
                                : ""));

        List<EmployeComplet> racines = parManager.getOrDefault("", Collections.emptyList());

        Set<String> visited = new HashSet<>();
        return racines.stream()
                .map(r -> buildNode(r, parManager, visited))
                .collect(Collectors.toList());
    }

    private OrganigrammeArbreDto buildNode(EmployeComplet emp,
                                           Map<String, List<EmployeComplet>> parManager,
                                           Set<String> visited) {
        if (!visited.add(emp.getId())) {
            return toArbreDto(emp, Collections.emptyList());
        }

        List<EmployeComplet> enfants = parManager.getOrDefault(emp.getNomComplet(), Collections.emptyList());
        List<OrganigrammeArbreDto> sousOrdres = enfants.stream()
                .map(e -> buildNode(e, parManager, visited))
                .collect(Collectors.toList());

        return toArbreDto(emp, sousOrdres);
    }

    private OrganigrammeNodeDto toNodeDto(EmployeComplet emp) {
        return OrganigrammeNodeDto.builder()
                .id(emp.getId())
                .agentId(emp.getAgentId())
                .nomComplet(emp.getNomComplet())
                .poste(emp.getPoste())
                .agence(emp.getAgence() != null && emp.getAgence().length > 0 ? emp.getAgence()[0] : null)
                .managerOps(emp.getManagerOps())
                .chefEquipe(emp.getChefEquipe())
                .statut(emp.getStatut() != null ? emp.getStatut().name() : null)
                .photo(emp.getPhoto())
                .build();
    }

    private OrganigrammeArbreDto toArbreDto(EmployeComplet emp, List<OrganigrammeArbreDto> sousOrdres) {
        return OrganigrammeArbreDto.builder()
                .id(emp.getId())
                .nomComplet(emp.getNomComplet())
                .poste(emp.getPoste())
                .agence(emp.getAgence() != null && emp.getAgence().length > 0 ? emp.getAgence()[0] : null)
                .sousOrdres(sousOrdres)
                .build();
    }
}