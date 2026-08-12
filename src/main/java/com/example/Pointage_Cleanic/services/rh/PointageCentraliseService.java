package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.PointageCentraliseDto;
import com.example.Pointage_Cleanic.Dto.ResumeJourneeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Vue « Pointage centralisé » (RH 6.2 — Temps & Présences), lecture seule, tous
 * départements confondus. Projette le store {@code pointages} existant — enrichi du
 * référentiel {@link DossierEmploye} — vers le contrat attendu par la page RH.
 *
 * <p>Source de vérité = {@link DossierEmploye} (collection {@code dossiers_employes}) :
 * c'est l'entité sur laquelle {@code POST /api/pointages} résout l'agent via
 * {@code findByAgentId(codeSecret)}, donc {@code Pointage.codeSecret == DossierEmploye.agentId}.
 *
 * <p>Périmètre = employés ACTIF ou EN_PERIODE_ESSAI. L'agrégation part de la liste des
 * employés attendus (LEFT JOIN sur les pointages) pour que les ABSENT remontent.
 *
 * <p><b>Retard non dérivé :</b> {@code DossierEmploye} ne porte pas d'heure de début et
 * les horaires sont hétérogènes d'un employé à l'autre. Le statut {@code RETARD} reste
 * dans le contrat (énum statut, {@code retardMinutes}, compteur {@code retards}) mais
 * vaut toujours {@code 0} / {@code PRESENT}. Hook à brancher quand un planning par
 * employé (heure de début) existera.
 */
@Service
@RequiredArgsConstructor
public class PointageCentraliseService {

    private static final List<StatutDossierEmploye> STATUTS_ACTIFS =
            List.of(StatutDossierEmploye.ACTIF, StatutDossierEmploye.EN_PERIODE_ESSAI);

    private final DossierEmployeRepository dossierEmployeRepository;
    private final PointageRepository pointageRepository;
    private final DemandeCongeRepository demandeCongeRepository;

    public Page<PointageCentraliseDto> getPointages(
            LocalDate date, String departement, String site,
            String statut, String q, int page, int size) {

        LocalDate targetDate = date != null ? date : LocalDate.now();

        List<DossierEmploye> employes = dossierEmployeRepository.findByStatutIn(STATUTS_ACTIFS);
        List<PointageCentraliseDto> result = buildForDate(targetDate, employes, departement, site, q).stream()
                .filter(dto -> statut == null || statut.isBlank() || dto.getStatut().equals(statut))
                .collect(Collectors.toList());

        return paginate(result, page, size);
    }

    /**
     * Variante plage de dates : agrège la vue jour par jour entre dateDebut et
     * dateFin (incluses). Une ligne par (employé, jour). Utilisée par
     * {@code /api/temps-presences/pointages} quand le front fournit un intervalle.
     */
    public Page<PointageCentraliseDto> getPointagesRange(
            LocalDate dateDebut, LocalDate dateFin, String departement, String site,
            String statut, String q, int page, int size) {

        if (dateDebut == null || dateFin == null || dateFin.isBefore(dateDebut)) {
            throw new IllegalArgumentException("dateDebut et dateFin sont requis et dateFin doit être >= dateDebut");
        }

        List<DossierEmploye> employes = dossierEmployeRepository.findByStatutIn(STATUTS_ACTIFS);

        List<PointageCentraliseDto> result = dateDebut.datesUntil(dateFin.plusDays(1))
                .flatMap(jour -> buildForDate(jour, employes, departement, site, q).stream())
                .filter(dto -> statut == null || statut.isBlank() || dto.getStatut().equals(statut))
                .collect(Collectors.toList());

        return paginate(result, page, size);
    }

    /** Construit la vue centralisée d'un jour donné (filtres dép./site/q, sans statut ni pagination). */
    private List<PointageCentraliseDto> buildForDate(
            LocalDate jour, List<DossierEmploye> employes, String departement, String site, String q) {

        Map<String, Pointage> pointageParCode = pointageRepository.findAllByDate(jour).stream()
                .filter(p -> p.getCodeSecret() != null)
                .collect(Collectors.toMap(Pointage::getCodeSecret, p -> p, (a, b) -> a));

        Map<String, DemandeConge> congeParEmploye = demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                        StatutDemande.APPROUVE, jour, jour).stream()
                .collect(Collectors.toMap(DemandeConge::getEmployeId, c -> c, (a, b) -> a));

        return employes.stream()
                .filter(e -> matchesFiltres(e, departement, site, q))
                .map(e -> buildDto(e, jour, pointageParCode.get(e.getAgentId()), congeParEmploye.get(e.getId())))
                .collect(Collectors.toList());
    }

    private boolean matchesFiltres(DossierEmploye e, String departement, String site, String q) {
        if (departement != null && !departement.isBlank()) {
            String dept = e.getDepartement() != null ? e.getDepartement() : "";
            if (!dept.equalsIgnoreCase(departement)) return false;
        }
        if (site != null && !site.isBlank()) {
            String empSite = e.getSiteAffecte() != null ? e.getSiteAffecte() : "";
            if (!empSite.toLowerCase().contains(site.toLowerCase())) return false;
        }
        if (q != null && !q.isBlank()) {
            String search = q.toLowerCase();
            boolean matchNom = e.getNom() != null && e.getNom().toLowerCase().contains(search);
            boolean matchPrenom = e.getPrenom() != null && e.getPrenom().toLowerCase().contains(search);
            boolean matchMatricule = e.getMatricule() != null && e.getMatricule().toLowerCase().contains(search);
            if (!matchNom && !matchPrenom && !matchMatricule) return false;
        }
        return true;
    }

    private Page<PointageCentraliseDto> paginate(List<PointageCentraliseDto> result, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), result.size());
        List<PointageCentraliseDto> pageContent = start >= result.size()
                ? List.of() : result.subList(start, end);
        return new PageImpl<>(pageContent, pageable, result.size());
    }

    public ResumeJourneeDto getResume(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();

        List<DossierEmploye> employes = dossierEmployeRepository.findByStatutIn(STATUTS_ACTIFS);
        Map<String, Pointage> pointageParCode = pointageRepository.findAllByDate(targetDate).stream()
                .filter(p -> p.getCodeSecret() != null)
                .collect(Collectors.toMap(Pointage::getCodeSecret, p -> p, (a, b) -> a));

        Map<String, DemandeConge> congeParEmploye = demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                        StatutDemande.APPROUVE, targetDate, targetDate).stream()
                .collect(Collectors.toMap(DemandeConge::getEmployeId, c -> c, (a, b) -> a));

        int presents = 0, absents = 0, retards = 0, enConge = 0;

        for (DossierEmploye e : employes) {
            PointageCentraliseDto dto = buildDto(e, targetDate,
                    pointageParCode.get(e.getAgentId()), congeParEmploye.get(e.getId()));
            // Un seul incrément par employé : presents + absents + retards + enConge == totalEmployes.
            // (RETARD non dérivé pour l'instant — voir javadoc de classe.)
            switch (dto.getStatut()) {
                case "PRESENT" -> presents++;
                case "RETARD"  -> retards++;
                case "CONGE"   -> enConge++;
                default        -> absents++;
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

    private PointageCentraliseDto buildDto(DossierEmploye e, LocalDate date,
                                           Pointage pointage, DemandeConge conge) {
        String statutVal;
        Integer dureeMinutes = null;
        String heureArrivee = null;
        String heureDepart = null;
        String motif = null;
        String site = e.getSiteAffecte();
        String id = e.getId() + "-" + date;

        if (conge != null) {
            statutVal = "CONGE";
            // Libellé lisible plutôt que le nom brut de l'enum : le motif est affiché tel
            // quel dans le tableau (« Absence non justifiée », pas « ABSENCE_NON_JUSTIFIEE »).
            motif = conge.getType() != null ? conge.getType().getLibelle() : null;
        } else if (pointage != null) {
            id = pointage.getId();
            heureArrivee = pointage.getHeureArrive();
            heureDepart = pointage.getHeureDepart();
            dureeMinutes = computeDureeMinutes(heureArrivee, heureDepart);
            site = joinSites(pointage.getSite());
            statutVal = "PRESENT";
        } else {
            statutVal = "ABSENT";
        }

        return PointageCentraliseDto.builder()
                .id(id)
                .employeId(e.getId())
                .matricule(e.getMatricule())
                .nom(e.getNom())
                .prenom(e.getPrenom())
                .departement(e.getDepartement())
                .site(site)
                .poste(e.getPoste())
                .date(date)
                .heureArrivee(heureArrivee)
                .heureDepart(heureDepart)
                .dureeMinutes(dureeMinutes)
                .retardMinutes(0)
                .statut(statutVal)
                .motif(motif)
                .build();
    }

    /** Premier(s) site(s) couvert(s) par le pointage, joints par ", " (modèle front mono-site). */
    private String joinSites(String[] sites) {
        if (sites == null || sites.length == 0) return null;
        return Arrays.stream(sites)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    /**
     * Durée travaillée en minutes, recalculée depuis les heures d'arrivée/départ
     * ("HH:mm"). {@code null} tant que le départ n'est pas pointé (la source stocke
     * {@code Pointage.duree} en texte "9h"/"8h30mn", non exploitable tel quel).
     */
    private Integer computeDureeMinutes(String heureArrive, String heureDepart) {
        if (heureArrive == null || heureDepart == null) return null;
        try {
            LocalTime arrivee = LocalTime.parse(heureArrive);
            LocalTime depart = LocalTime.parse(heureDepart);
            long minutes = Duration.between(arrivee, depart).toMinutes();
            return minutes >= 0 ? (int) minutes : null;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
