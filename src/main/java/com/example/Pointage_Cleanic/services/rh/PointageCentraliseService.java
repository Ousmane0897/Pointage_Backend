package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.PointageCentraliseDto;
import com.example.Pointage_Cleanic.Dto.ResumeJourneeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * <p><b>Retard dérivé (tolérance stricte) :</b> l'heure d'arrivée prévue est le
 * {@code horaireDebut} de l'affectation ({@link AffectationSite}) dont le site est
 * pointé (match par site sur {@code Pointage.site[]}, insensible à la casse ; si
 * plusieurs sites pointés matchent, le {@code horaireDebut} le plus tôt est retenu).
 * {@code retardMinutes} = retard brut ({@code heureArrivee - heurePrevue}, borné à
 * {@code >= 0}) sans tolérance appliquée. Le statut {@code RETARD} n'est posé que si
 * {@code retardBrut > toleranceRetardMinutes} (seuil strict : 15 min pile = non retard).
 * Si aucune affectation ne matche ou que son {@code horaireDebut} est absent/illisible,
 * l'heure prévue est indéterminée → {@code retardMinutes = 0} et statut {@code PRESENT}.
 */
@Service
public class PointageCentraliseService {

    private static final List<StatutDossierEmploye> STATUTS_ACTIFS =
            List.of(StatutDossierEmploye.ACTIF, StatutDossierEmploye.EN_PERIODE_ESSAI);

    private final DossierEmployeRepository dossierEmployeRepository;
    private final PointageRepository pointageRepository;
    private final DemandeCongeRepository demandeCongeRepository;

    /** Seuil de tolérance de retard (minutes). Retard marqué seulement si retardBrut > seuil. */
    private final int toleranceRetardMinutes;

    public PointageCentraliseService(
            DossierEmployeRepository dossierEmployeRepository,
            PointageRepository pointageRepository,
            DemandeCongeRepository demandeCongeRepository,
            @Value("${rh.pointage.tolerance-retard-minutes:15}") int toleranceRetardMinutes) {
        this.dossierEmployeRepository = dossierEmployeRepository;
        this.pointageRepository = pointageRepository;
        this.demandeCongeRepository = demandeCongeRepository;
        this.toleranceRetardMinutes = toleranceRetardMinutes;
    }

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

    /**
     * Construit la vue centralisée d'un jour donné (filtres dép./site/q, sans statut ni
     * pagination). <b>Une ligne par pointage</b> : un agent qui pointe sur plusieurs sites
     * le même jour produit plusieurs lignes, chacune évaluée sur l'horaire de son site.
     */
    private List<PointageCentraliseDto> buildForDate(
            LocalDate jour, List<DossierEmploye> employes, String departement, String site, String q) {

        Map<String, List<Pointage>> pointagesParCode = pointageRepository.findAllByDate(jour).stream()
                .filter(p -> p.getCodeSecret() != null)
                .collect(Collectors.groupingBy(Pointage::getCodeSecret));

        Map<String, DemandeConge> congeParEmploye = demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                        StatutDemande.APPROUVE, jour, jour).stream()
                .collect(Collectors.toMap(DemandeConge::getEmployeId, c -> c, (a, b) -> a));

        return employes.stream()
                .filter(e -> matchesFiltres(e, departement, site, q))
                .flatMap(e -> buildLignes(e, jour,
                        pointagesParCode.getOrDefault(e.getAgentId(), List.of()),
                        congeParEmploye.get(e.getId())).stream())
                .collect(Collectors.toList());
    }

    /**
     * Lignes de la vue pour un employé et un jour : une ligne {@code CONGE} si congé
     * approuvé (prioritaire), sinon une ligne par pointage du jour (multi-sites → plusieurs
     * lignes, chacune avec son retard propre calculé sur l'horaire de son site), sinon une
     * seule ligne {@code ABSENT}.
     */
    private List<PointageCentraliseDto> buildLignes(DossierEmploye e, LocalDate jour,
                                                    List<Pointage> pointages, DemandeConge conge) {
        if (conge != null) {
            return List.of(buildDto(e, jour, null, conge));
        }
        if (pointages.isEmpty()) {
            return List.of(buildDto(e, jour, null, null));
        }
        return pointages.stream()
                .map(p -> buildDto(e, jour, p, null))
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
        // Comptage par LIGNE de pointage (mêmes lignes que la liste, sans filtre) : un agent
        // en retard sur 2 sites compte pour 2. presents/absents/retards/enConge portent donc
        // sur les lignes, pas sur les employés — leur somme peut dépasser totalEmployes, qui
        // reste le headcount d'employés actifs distincts.
        List<PointageCentraliseDto> lignes = buildForDate(targetDate, employes, null, null, null);

        int presents = 0, absents = 0, retards = 0, enConge = 0;
        for (PointageCentraliseDto dto : lignes) {
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
        int retardMinutes = 0;
        String site = e.getSiteAffecte();
        String id = e.getId() + "-" + date;

        if (conge != null) {
            statutVal = "CONGE";
            motif = conge.getType() != null ? conge.getType().name() : null;
        } else if (pointage != null) {
            id = pointage.getId();
            heureArrivee = pointage.getHeureArrive();
            heureDepart = pointage.getHeureDepart();
            dureeMinutes = computeDureeMinutes(heureArrivee, heureDepart);
            site = joinSites(pointage.getSite());
            retardMinutes = computeRetardMinutes(heureArrivee, resoudreHeurePrevue(e, pointage));
            statutVal = retardMinutes > toleranceRetardMinutes ? "RETARD" : "PRESENT";
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
                .retardMinutes(retardMinutes)
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
        LocalTime arrivee = parseHeure(heureArrive);
        LocalTime depart = parseHeure(heureDepart);
        if (arrivee == null || depart == null) return null;
        long minutes = Duration.between(arrivee, depart).toMinutes();
        return minutes >= 0 ? (int) minutes : null;
    }

    /**
     * Heure d'arrivée prévue de l'employé pour ce pointage, résolue par site (fallbacks ordonnés) :
     * <ol>
     *   <li>Pointage mono-site : {@code horaireDebut} de l'affectation de ce site précisément
     *       (match insensible à la casse).</li>
     *   <li>Pointage multi-sites (cas exceptionnel : un enregistrement porte plusieurs sites) :
     *       {@code horaireDebut} le plus tôt parmi toutes les affectations de l'employé.</li>
     *   <li>Sinon (site hors affectations, {@code horaireDebut} non renseigné/illisible, aucune
     *       affectation) : {@code null} → on ne pénalise pas sur une donnée manquante.</li>
     * </ol>
     */
    private LocalTime resoudreHeurePrevue(DossierEmploye e, Pointage pointage) {
        List<AffectationSite> affectations = e.getAffectations();
        if (affectations == null || affectations.isEmpty()
                || pointage == null || pointage.getSite() == null) {
            return null;
        }
        Set<String> sitesPointes = Arrays.stream(pointage.getSite())
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase())
                .collect(Collectors.toSet());
        if (sitesPointes.isEmpty()) return null;

        if (sitesPointes.size() == 1) {
            // (1) mono-site : horaireDebut de l'affectation de ce site (min si doublons d'affectation).
            return affectations.stream()
                    .filter(a -> a.getSite() != null
                            && sitesPointes.contains(a.getSite().trim().toLowerCase()))
                    .map(a -> parseHeure(a.getHoraireDebut()))
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
        }
        // (2) multi-sites dans un même enregistrement : horaireDebut le plus tôt parmi les affectations.
        return affectations.stream()
                .map(a -> parseHeure(a.getHoraireDebut()))
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * Retard brut en minutes = {@code heureArrivee - heurePrevue}, borné à {@code >= 0}
     * (arrivée en avance → 0). {@code 0} si l'heure d'arrivée ou l'heure prévue est
     * absente/illisible. La tolérance n'est PAS appliquée ici (le champ porte le brut).
     */
    private int computeRetardMinutes(String heureArrive, LocalTime heurePrevue) {
        LocalTime arrivee = parseHeure(heureArrive);
        if (arrivee == null || heurePrevue == null) return 0;
        long minutes = Duration.between(heurePrevue, arrivee).toMinutes();
        return minutes > 0 ? (int) minutes : 0;
    }

    /** Parse une heure "HH:mm" en {@link LocalTime}, {@code null} si absente/illisible. */
    private LocalTime parseHeure(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return null;
        try {
            return LocalTime.parse(hhmm.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
