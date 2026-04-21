package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.PointageCentraliseDto;
import com.example.Pointage_Cleanic.Dto.ResumeJourneeDto;
import com.example.Pointage_Cleanic.Enum.StatutDemande;
import com.example.Pointage_Cleanic.entities.DemandeConge;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PointageCentraliseService {

    private final EmployeCompletRepository employeCompletRepository;
    private final PointageRepository pointageRepository;
    private final DemandeCongeRepository demandeCongeRepository;

    public Page<PointageCentraliseDto> getPointages(
            LocalDate date, String departement, String site,
            String statut, String q, int page, int size) {

        LocalDate targetDate = date != null ? date : LocalDate.now();
        Pageable pageable = PageRequest.of(page, size);

        // Récupérer tous les pointages du jour indexés par codeSecret
        List<Pointage> pointagesDuJour = pointageRepository.findAllByDate(targetDate);
        Map<String, Pointage> pointageParCode = pointagesDuJour.stream()
                .collect(Collectors.toMap(Pointage::getCodeSecret, p -> p, (a, b) -> a));

        // Récupérer les congés approuvés couvrant la date
        List<DemandeConge> congesActifs = demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                        StatutDemande.APPROUVE, targetDate, targetDate);
        Map<String, DemandeConge> congeParEmploye = congesActifs.stream()
                .collect(Collectors.toMap(DemandeConge::getEmployeId, c -> c, (a, b) -> a));

        // Construire la vue pour chaque employé ACTIF
        List<EmployeComplet> employes = employeCompletRepository.findByStatut(EmployeComplet.StatutEmploye.ACTIF);

        List<PointageCentraliseDto> result = employes.stream()
                .filter(e -> {
                    if (departement != null && !departement.isBlank()) {
                        String dept = e.getAgence() != null && e.getAgence().length > 0 ? e.getAgence()[0] : "";
                        if (!dept.equalsIgnoreCase(departement)) return false;
                    }
                    if (site != null && !site.isBlank()) {
                        String empSite = e.getCodeSite() != null ? e.getCodeSite() : "";
                        if (!empSite.equalsIgnoreCase(site)) return false;
                    }
                    if (q != null && !q.isBlank()) {
                        String search = q.toLowerCase();
                        boolean matchNom = e.getNom() != null && e.getNom().toLowerCase().contains(search);
                        boolean matchMatricule = e.getMatricule() != null && e.getMatricule().toLowerCase().contains(search);
                        if (!matchNom && !matchMatricule) return false;
                    }
                    return true;
                })
                .map(e -> buildDto(e, targetDate, pointageParCode.get(e.getAgentId()), congeParEmploye.get(e.getId())))
                .filter(dto -> statut == null || statut.isBlank() || dto.getStatut().equals(statut))
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), result.size());
        List<PointageCentraliseDto> pageContent = start >= result.size()
                ? List.of() : result.subList(start, end);

        return new PageImpl<>(pageContent, pageable, result.size());
    }

    public ResumeJourneeDto getResume(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();

        List<EmployeComplet> employes = employeCompletRepository.findByStatut(EmployeComplet.StatutEmploye.ACTIF);
        List<Pointage> pointages = pointageRepository.findAllByDate(targetDate);
        Map<String, Pointage> pointageParCode = pointages.stream()
                .collect(Collectors.toMap(Pointage::getCodeSecret, p -> p, (a, b) -> a));

        List<DemandeConge> congesActifs = demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                        StatutDemande.APPROUVE, targetDate, targetDate);
        Map<String, DemandeConge> congeParEmploye = congesActifs.stream()
                .collect(Collectors.toMap(DemandeConge::getEmployeId, c -> c, (a, b) -> a));

        int presents = 0, absents = 0, retards = 0, enConge = 0;

        for (EmployeComplet e : employes) {
            PointageCentraliseDto dto = buildDto(e, targetDate,
                    pointageParCode.get(e.getAgentId()), congeParEmploye.get(e.getId()));
            switch (dto.getStatut()) {
                case "PRESENT"  -> presents++;
                case "RETARD"   -> { presents++; retards++; }
                case "CONGE"    -> enConge++;
                default         -> absents++;
            }
        }

        return ResumeJourneeDto.builder()
                .date(targetDate)
                .totalEmployes(employes.size())
                .presents(presents)
                .absents(absents)
                .retards(retards)
                .enConge(enConge)
                .build();
    }

    private PointageCentraliseDto buildDto(EmployeComplet e, LocalDate date,
                                           Pointage pointage, DemandeConge conge) {
        String dept = e.getAgence() != null && e.getAgence().length > 0 ? e.getAgence()[0] : null;
        String siteName = e.getVilleSite() != null ? e.getVilleSite() : e.getCodeSite();

        String statutVal;
        Integer dureeMinutes = null;
        Integer retardMinutes = null;
        String heureArrivee = null;
        String heureDepart = null;
        String motif = null;

        if (conge != null) {
            statutVal = "CONGE";
            motif = conge.getType() != null ? conge.getType().name() : null;
        } else if (pointage != null) {
            heureArrivee = pointage.getHeureArrive();
            heureDepart = pointage.getHeureDepart();
            dureeMinutes = parseDureeToMinutes(pointage.getDuree());
            retardMinutes = computeRetardMinutes(e.getHeureDebut(), heureArrivee);
            statutVal = retardMinutes != null && retardMinutes > 0 ? "RETARD" : "PRESENT";
        } else {
            statutVal = "ABSENT";
        }

        return PointageCentraliseDto.builder()
                .id(e.getId())
                .employeId(e.getId())
                .matricule(e.getMatricule())
                .nom(e.getNom())
                .prenom(e.getPrenom())
                .departement(dept)
                .site(siteName)
                .poste(e.getPoste())
                .date(date)
                .heureArrivee(heureArrivee)
                .heureDepart(heureDepart)
                .dureeMinutes(dureeMinutes)
                .retardMinutes(retardMinutes)
                .statut(statutVal)
                .motif(motif)
                .build();
    }

    private Integer parseDureeToMinutes(String duree) {
        if (duree == null || duree.isBlank()) return null;
        try {
            // format attendu HH:mm
            String[] parts = duree.split(":");
            if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private Integer computeRetardMinutes(String heureDebutPrevue, String heureArriveeReelle) {
        if (heureDebutPrevue == null || heureArriveeReelle == null) return null;
        try {
            LocalTime prevue = LocalTime.parse(heureDebutPrevue);
            LocalTime reelle = LocalTime.parse(heureArriveeReelle);
            int diff = (int) java.time.Duration.between(prevue, reelle).toMinutes();
            return diff > 0 ? diff : 0;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}