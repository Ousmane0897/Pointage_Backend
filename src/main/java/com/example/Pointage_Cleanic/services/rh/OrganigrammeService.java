package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.OrganigrammeArbreDto;
import com.example.Pointage_Cleanic.Dto.rh.OrganigrammeNodeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Organigramme bâti sur {@link DossierEmploye} (source de vérité RH).
 * La hiérarchie est résolue par {@code superieurHierarchiqueId} (lien par id,
 * fiable) et non plus par nom de manager. Périmètre = ACTIF + EN_PERIODE_ESSAI.
 */
@Service
@RequiredArgsConstructor
public class OrganigrammeService {

    private static final List<StatutDossierEmploye> STATUTS_ACTIFS =
            List.of(StatutDossierEmploye.ACTIF, StatutDossierEmploye.EN_PERIODE_ESSAI);

    private final DossierEmployeRepository dossierEmployeRepository;

    public List<OrganigrammeNodeDto> getOrganigramme(String departement) {
        List<DossierEmploye> employes = dossierEmployeRepository.findByStatutIn(STATUTS_ACTIFS);

        if (departement != null && !departement.isBlank()) {
            employes = employes.stream()
                    .filter(e -> departement.equalsIgnoreCase(e.getDepartement()))
                    .collect(Collectors.toList());
        }

        return employes.stream()
                .map(this::toNodeDto)
                .sorted(Comparator.comparing(OrganigrammeNodeDto::getPoste, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OrganigrammeNodeDto::getNomComplet, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    public List<OrganigrammeArbreDto> getArbre() {
        List<DossierEmploye> employes = dossierEmployeRepository.findByStatutIn(STATUTS_ACTIFS);

        // Regroupe les employés par id de leur supérieur hiérarchique ("" = racine).
        Map<String, List<DossierEmploye>> parManager = employes.stream()
                .collect(Collectors.groupingBy(e ->
                        e.getSuperieurHierarchiqueId() != null && !e.getSuperieurHierarchiqueId().isBlank()
                                ? e.getSuperieurHierarchiqueId()
                                : ""));

        List<DossierEmploye> racines = parManager.getOrDefault("", Collections.emptyList());

        Set<String> visited = new HashSet<>();
        return racines.stream()
                .map(r -> buildNode(r, parManager, visited))
                .collect(Collectors.toList());
    }

    private OrganigrammeArbreDto buildNode(DossierEmploye emp,
                                           Map<String, List<DossierEmploye>> parManager,
                                           Set<String> visited) {
        if (!visited.add(emp.getId())) {
            return toArbreDto(emp, Collections.emptyList());
        }

        List<DossierEmploye> enfants = parManager.getOrDefault(emp.getId(), Collections.emptyList());
        List<OrganigrammeArbreDto> sousOrdres = enfants.stream()
                .map(e -> buildNode(e, parManager, visited))
                .collect(Collectors.toList());

        return toArbreDto(emp, sousOrdres);
    }

    private OrganigrammeNodeDto toNodeDto(DossierEmploye emp) {
        return OrganigrammeNodeDto.builder()
                .id(emp.getId())
                .agentId(emp.getAgentId())
                .nomComplet(nomComplet(emp))
                .poste(emp.getPoste())
                .agence(emp.getDepartement())
                .managerOps(emp.getSuperieurHierarchiqueNom())
                .statut(emp.getStatut() != null ? emp.getStatut().name() : null)
                .photo(emp.getPhoto())
                .build();
    }

    private OrganigrammeArbreDto toArbreDto(DossierEmploye emp, List<OrganigrammeArbreDto> sousOrdres) {
        return OrganigrammeArbreDto.builder()
                .id(emp.getId())
                .nomComplet(nomComplet(emp))
                .poste(emp.getPoste())
                .agence(emp.getDepartement())
                .sousOrdres(sousOrdres)
                .build();
    }

    private static String nomComplet(DossierEmploye e) {
        String prenom = e.getPrenom() == null ? "" : e.getPrenom().trim();
        String nom = e.getNom() == null ? "" : e.getNom().trim();
        return (prenom + " " + nom).trim();
    }
}
