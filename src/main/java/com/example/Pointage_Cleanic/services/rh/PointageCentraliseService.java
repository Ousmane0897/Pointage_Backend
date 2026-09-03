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

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vue « Pointage centralisé » (RH 6.2 — Temps &amp; Présences), lecture seule, tous
 * départements confondus.
 *
 * <p><b>Une ligne = un créneau</b> (employé × jour × site attendu), et non plus un
 * enregistrement de pointage. Un agent affecté à deux sites produit deux lignes par
 * jour, chacune évaluée <b>strictement</b> sur le {@code horaireDebut} de son propre
 * site. Le socle des lignes est donc le planning ({@link PlanningAffectationResolver}),
 * les pointages venant s'y rattacher.
 *
 * <p><b>Pourquoi le site du pointage ne sert pas à rattacher.</b>
 * {@code PointageServices.enregistrerPointage} recopie <i>tous</i> les sites de l'agent
 * dans {@code Pointage.site[]} — l'agent ne choisit jamais le sien. Ce champ ne peut donc
 * pas dire où il s'est présenté ; il ne reste qu'un libellé d'affichage sur les lignes
 * hors planning. Le rattachement se fait par <b>plage horaire</b> : le pointage revient au
 * créneau dont la tranche contient son heure d'arrivée, sinon au plus proche, chaque
 * créneau ne pouvant accueillir qu'un seul pointage.
 *
 * <p><b>Échelle de statuts</b>, par créneau et comparée à la date-heure de la ligne :
 * pointé ⇒ {@code PRESENT} ou {@code RETARD} (au-delà de la tolérance) ; non pointé ⇒
 * {@code NEUTRE} avant le début du créneau, {@code EN_ATTENTE} pendant, {@code ABSENT}
 * une fois la fin dépassée. Un jour passé étant toujours après la fin, ses créneaux non
 * pointés sont {@code ABSENT} et jamais {@code NEUTRE} — c'est ce qui rend l'onglet
 * Historique correct sans cas particulier. Un congé approuvé produit une ligne
 * {@code CONGE} unique, prioritaire sur tout le reste.
 *
 * <p>Périmètre = employés ACTIF ou EN_PERIODE_ESSAI.
 */
@Service
public class PointageCentraliseService {

    private static final List<StatutDossierEmploye> STATUTS_ACTIFS =
            List.of(StatutDossierEmploye.ACTIF, StatutDossierEmploye.EN_PERIODE_ESSAI);

    static final String PRESENT = "PRESENT";
    static final String RETARD = "RETARD";
    static final String ABSENT = "ABSENT";
    static final String CONGE = "CONGE";
    static final String NEUTRE = "NEUTRE";
    static final String EN_ATTENTE = "EN_ATTENTE";
    static final String HORS_PLAN = "HORS_PLAN";

    private final DossierEmployeRepository dossierEmployeRepository;
    private final PointageRepository pointageRepository;
    private final DemandeCongeRepository demandeCongeRepository;
    private final PlanningAffectationResolver planning;
    /** Horloge métier (Africa/Dakar) : jamais de {@code now()} en dur dans ce service. */
    private final Clock clock;

    /** Seuil de tolérance de retard (minutes). Retard marqué seulement si retardBrut > seuil. */
    private final int toleranceRetardMinutes;

    public PointageCentraliseService(
            DossierEmployeRepository dossierEmployeRepository,
            PointageRepository pointageRepository,
            DemandeCongeRepository demandeCongeRepository,
            PlanningAffectationResolver planning,
            Clock clock,
            @Value("${rh.pointage.tolerance-retard-minutes:15}") int toleranceRetardMinutes) {
        this.dossierEmployeRepository = dossierEmployeRepository;
        this.pointageRepository = pointageRepository;
        this.demandeCongeRepository = demandeCongeRepository;
        this.planning = planning;
        this.clock = clock;
        this.toleranceRetardMinutes = toleranceRetardMinutes;
    }

    public Page<PointageCentraliseDto> getPointages(
            LocalDate date, String departement, String site,
            String statut, String q, int page, int size) {

        LocalDate targetDate = date != null ? date : LocalDate.now(clock);

        List<DossierEmploye> employes = dossierEmployeRepository.findByStatutIn(STATUTS_ACTIFS);
        List<PointageCentraliseDto> result = buildForDate(targetDate, employes, departement, site, q).stream()
                .filter(dto -> statut == null || statut.isBlank() || dto.getStatut().equals(statut))
                .collect(Collectors.toList());

        return paginate(result, page, size);
    }

    /**
     * Variante plage de dates : agrège la vue jour par jour entre dateDebut et
     * dateFin (incluses). Utilisée par {@code /api/temps-presences/pointages} quand le
     * front fournit un intervalle.
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
     * pagination).
     *
     * <p>{@code departement} et {@code q} filtrent l'<b>employé</b> — un pré-filtre bon
     * marché qui évite de construire ses lignes. {@code site}, lui, filtre la
     * <b>ligne</b> : chaque ligne portant désormais son propre site, filtrer sur l'employé
     * rendrait aussi les lignes de tous ses autres sites.
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

        LocalDateTime maintenant = LocalDateTime.now(clock);

        return employes.stream()
                .filter(e -> matchesFiltres(e, departement, q))
                .flatMap(e -> buildLignes(e, jour,
                        pointagesParCode.getOrDefault(e.getAgentId(), List.of()),
                        congeParEmploye.get(e.getId()), maintenant).stream())
                .filter(dto -> matchesSite(dto, site))
                .collect(Collectors.toList());
    }

    /**
     * Lignes de la vue pour un employé et un jour : une ligne {@code CONGE} si congé
     * approuvé (prioritaire), sinon une ligne par créneau attendu — enrichie du pointage
     * qui s'y rattache — plus une ligne {@code HORS_PLAN} par pointage inexpliqué.
     */
    private List<PointageCentraliseDto> buildLignes(DossierEmploye e, LocalDate jour,
                                                    List<Pointage> pointages, DemandeConge conge,
                                                    LocalDateTime maintenant) {
        // Le congé est un fait de la JOURNÉE, pas du créneau : une seule ligne, sinon la
        // tuile « En congé » compterait un multi-sites autant de fois qu'il a de sites.
        if (conge != null) {
            return List.of(buildLigneConge(e, jour, conge));
        }

        List<AffectationSite> prevues = planning.prevuesPourJour(e, jour);
        Map<AffectationSite, Pointage> attribues = rattacher(prevues, pointages);

        List<PointageCentraliseDto> lignes = new ArrayList<>();
        for (int i = 0; i < prevues.size(); i++) {
            AffectationSite affectation = prevues.get(i);
            lignes.add(buildLigneCreneau(
                    e, jour, affectation, i, attribues.get(affectation), maintenant));
        }

        // Pointages qu'aucun créneau n'explique : plus de pointages que de créneaux,
        // employé sans affectation ce jour-là (week-end, affectation expirée, dossier
        // legacy), heure d'arrivée illisible. Ils ne doivent JAMAIS disparaître.
        Set<Pointage> consommes = Collections.newSetFromMap(new IdentityHashMap<>());
        consommes.addAll(attribues.values());
        for (Pointage p : pointages) {
            if (!consommes.contains(p)) {
                lignes.add(buildLigneHorsPlan(e, jour, p));
            }
        }
        return lignes;
    }

    /**
     * Rattache chaque pointage à au plus un créneau, en deux passes.
     *
     * <ol>
     *   <li><b>Contenance</b> — l'arrivée tombe dans la tranche du créneau. Quand
     *       plusieurs tranches se chevauchent (06:00-14:00 et 10:00-18:00, arrivée à
     *       10:05), on retient celle dont le début est le <b>plus tard</b> : c'est celle
     *       qui vient de s'ouvrir, donc celle où l'agent se présente.</li>
     *   <li><b>Proximité</b> — les pointages restants rejoignent le créneau libre dont le
     *       début est le plus proche, les couples étant traités par écart croissant pour
     *       que le résultat ne dépende pas de l'ordre d'itération.</li>
     * </ol>
     *
     * <p>Un créneau sans {@code horaireDebut} ne peut être gagné par aucune des deux
     * passes : sans heure attendue, tout rattachement serait arbitraire.
     */
    private Map<AffectationSite, Pointage> rattacher(List<AffectationSite> prevues,
                                                     List<Pointage> pointages) {
        Map<AffectationSite, Pointage> attribues = new IdentityHashMap<>();
        if (prevues.isEmpty() || pointages.isEmpty()) return attribues;

        List<Pointage> candidats = pointages.stream()
                .filter(p -> planning.parseHeure(p.getHeureArrive()) != null)
                .sorted(Comparator
                        .comparing((Pointage p) -> planning.parseHeure(p.getHeureArrive()))
                        .thenComparing(p -> p.getId() != null ? p.getId() : ""))
                .collect(Collectors.toList());

        Set<AffectationSite> libres = Collections.newSetFromMap(new IdentityHashMap<>());
        libres.addAll(prevues);
        Set<Pointage> utilises = Collections.newSetFromMap(new IdentityHashMap<>());

        // Passe 1 — contenance.
        for (Pointage p : candidats) {
            LocalTime arrivee = planning.parseHeure(p.getHeureArrive());
            AffectationSite gagnante = prevues.stream()
                    .filter(libres::contains)
                    .filter(a -> planning.contient(a, arrivee))
                    .max(Comparator.comparing(a -> planning.parseHeure(a.getHoraireDebut()),
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
            if (gagnante != null) {
                attribues.put(gagnante, p);
                libres.remove(gagnante);
                utilises.add(p);
            }
        }

        // Passe 2 — proximité, globalement gloutonne (écart croissant).
        List<Couple> couples = new ArrayList<>();
        for (Pointage p : candidats) {
            if (utilises.contains(p)) continue;
            LocalTime arrivee = planning.parseHeure(p.getHeureArrive());
            for (AffectationSite a : prevues) {
                if (!libres.contains(a)) continue;
                LocalTime debut = planning.parseHeure(a.getHoraireDebut());
                if (debut == null) continue;
                couples.add(new Couple(p, a,
                        Math.abs(Duration.between(debut, arrivee).toMinutes()), arrivee));
            }
        }
        couples.sort(Comparator.comparingLong(Couple::ecart).thenComparing(Couple::arrivee));
        for (Couple couple : couples) {
            if (utilises.contains(couple.pointage()) || !libres.contains(couple.affectation())) {
                continue;
            }
            attribues.put(couple.affectation(), couple.pointage());
            libres.remove(couple.affectation());
            utilises.add(couple.pointage());
        }
        return attribues;
    }

    /** Appariement candidat de la passe « proximité ». */
    private record Couple(Pointage pointage, AffectationSite affectation, long ecart, LocalTime arrivee) {}

    /** Filtre employé : département et recherche libre. Le site, lui, filtre la ligne. */
    private boolean matchesFiltres(DossierEmploye e, String departement, String q) {
        if (departement != null && !departement.isBlank()) {
            String dept = e.getDepartement() != null ? e.getDepartement() : "";
            if (!dept.equalsIgnoreCase(departement)) return false;
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

    /** Filtre site, appliqué à la LIGNE (même sémantique « contient » qu'auparavant). */
    private boolean matchesSite(PointageCentraliseDto dto, String site) {
        if (site == null || site.isBlank()) return true;
        String siteLigne = dto.getSite() != null ? dto.getSite() : "";
        return siteLigne.toLowerCase().contains(site.toLowerCase());
    }

    private Page<PointageCentraliseDto> paginate(List<PointageCentraliseDto> result, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), result.size());
        List<PointageCentraliseDto> pageContent = start >= result.size()
                ? List.of() : result.subList(start, end);
        return new PageImpl<>(pageContent, pageable, result.size());
    }

    /**
     * Compteurs de la journée. Créneaux et personnes sont comptés séparément — voir
     * {@link ResumeJourneeDto} : {@code enConge} et {@code totalEmployes} sont des
     * effectifs, tout le reste des créneaux.
     */
    public ResumeJourneeDto getResume(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(clock);

        List<DossierEmploye> employes = dossierEmployeRepository.findByStatutIn(STATUTS_ACTIFS);
        List<PointageCentraliseDto> lignes = buildForDate(targetDate, employes, null, null, null);

        int presents = 0, absents = 0, retards = 0, enConge = 0;
        int enAttente = 0, neutres = 0, horsPlan = 0;
        for (PointageCentraliseDto dto : lignes) {
            switch (dto.getStatut()) {
                case PRESENT    -> presents++;
                case RETARD     -> retards++;
                case ABSENT     -> absents++;
                case EN_ATTENTE -> enAttente++;
                case NEUTRE     -> neutres++;
                case HORS_PLAN  -> horsPlan++;
                case CONGE      -> enConge++;
                // Pas de branche attrape-tout : c'est elle qui, en rangeant l'inattendu
                // dans « absents », a masqué le rattachement défaillant jusqu'ici.
                default -> { }
            }
        }

        return ResumeJourneeDto.builder()
                .date(targetDate)
                .totalEmployes(employes.size())
                .creneauxPrevus(presents + retards + absents + enAttente + neutres)
                .presents(presents)
                .absents(absents)
                .retards(retards)
                .enAttente(enAttente)
                .neutres(neutres)
                .horsPlan(horsPlan)
                .enConge(enConge)
                .build();
    }

    // =========================================================================
    //  Construction des lignes
    // =========================================================================

    private PointageCentraliseDto buildLigneConge(DossierEmploye e, LocalDate date, DemandeConge conge) {
        return baseDto(e, date)
                .id(idLigne(e, date, "conge"))
                .site(e.getSiteAffecte())
                .statut(CONGE)
                .retardMinutes(0)
                .planifie(true)
                // Libellé lisible plutôt que le nom brut de l'enum : le motif est affiché
                // tel quel dans le tableau (« Absence non justifiée », pas l'enum).
                .motif(conge.getType() != null ? conge.getType().getLibelle() : null)
                .build();
    }

    /** Ligne d'un créneau attendu, avec le pointage qui s'y rattache s'il existe. */
    private PointageCentraliseDto buildLigneCreneau(DossierEmploye e, LocalDate date,
                                                    AffectationSite affectation, int ordinal,
                                                    Pointage pointage, LocalDateTime maintenant) {
        LocalTime debut = planning.parseHeure(affectation.getHoraireDebut());
        LocalTime fin = planning.parseHeure(affectation.getHoraireFin());

        PointageCentraliseDto.PointageCentraliseDtoBuilder dto = baseDto(e, date)
                .id(idLigne(e, date, ordinal + "|" + slug(affectation.getSite()) + "@"
                        + (affectation.getHoraireDebut() != null ? affectation.getHoraireDebut() : "--")))
                .site(affectation.getSite())
                .siteHoraireDebut(affectation.getHoraireDebut())
                .siteHoraireFin(affectation.getHoraireFin())
                .planifie(true);

        if (pointage != null) {
            String heureArrivee = pointage.getHeureArrive();
            int retard = computeRetardMinutes(heureArrivee, debut);
            return dto
                    .pointageId(pointage.getId())
                    .heureArrivee(heureArrivee)
                    .heureDepart(pointage.getHeureDepart())
                    .dureeMinutes(computeDureeMinutes(heureArrivee, pointage.getHeureDepart()))
                    .retardMinutes(retard)
                    .statut(retard > toleranceRetardMinutes ? RETARD : PRESENT)
                    .build();
        }

        // Horaires inconnus : bornes = journée entière. On n'invente pas 08:00-17:00,
        // un horaire fabriqué produirait un retard faux.
        LocalDateTime debutCreneau = date.atTime(debut != null ? debut : LocalTime.MIN);
        LocalDateTime finCreneau = date.atTime(fin != null ? fin : LocalTime.MAX);

        String statut;
        if (maintenant.isBefore(debutCreneau)) {
            statut = NEUTRE;
        } else if (maintenant.isBefore(finCreneau)) {
            statut = EN_ATTENTE;
        } else {
            statut = ABSENT;
        }
        return dto.retardMinutes(0).statut(statut).build();
    }

    /** Pointage qu'aucun créneau attendu n'explique — conservé, jamais fondu ailleurs. */
    private PointageCentraliseDto buildLigneHorsPlan(DossierEmploye e, LocalDate date, Pointage pointage) {
        String heureArrivee = pointage.getHeureArrive();
        return baseDto(e, date)
                .id(idLigne(e, date, "hp|" + (pointage.getId() != null ? pointage.getId() : "?")))
                // Seul endroit où Pointage.site[] est affiché : faute de créneau, c'est la
                // seule indication de lieu disponible (même si elle liste tous les sites).
                .site(joinSites(pointage.getSite()))
                .pointageId(pointage.getId())
                .heureArrivee(heureArrivee)
                .heureDepart(pointage.getHeureDepart())
                .dureeMinutes(computeDureeMinutes(heureArrivee, pointage.getHeureDepart()))
                .retardMinutes(0)
                .statut(HORS_PLAN)
                .planifie(false)
                .build();
    }

    private PointageCentraliseDto.PointageCentraliseDtoBuilder baseDto(DossierEmploye e, LocalDate date) {
        return PointageCentraliseDto.builder()
                .employeId(e.getId())
                .matricule(e.getMatricule())
                .nom(e.getNom())
                .prenom(e.getPrenom())
                .departement(e.getDepartement())
                .poste(e.getPoste())
                .date(date);
    }

    /**
     * Identifiant de ligne, propriété du <b>créneau</b> et non du pointage : il reste
     * identique avant et après l'arrivée de l'agent, sans quoi le {@code trackBy} Angular
     * détruirait puis recréerait la ligne à chaque rafraîchissement traversant un pointage.
     */
    private String idLigne(DossierEmploye e, LocalDate date, String suffixe) {
        return e.getId() + "|" + date + "|" + suffixe;
    }

    /** Nom de site normalisé pour l'identifiant : minuscules, sans accent ni séparateur. */
    private String slug(String site) {
        if (site == null || site.isBlank()) return "site";
        String sansAccent = Normalizer.normalize(site, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String resultat = sansAccent.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return resultat.isEmpty() ? "site" : resultat;
    }

    /** Sites portés par le pointage, joints par ", " — affichage hors planning seulement. */
    private String joinSites(String[] sites) {
        if (sites == null || sites.length == 0) return null;
        String joint = Arrays.stream(sites)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
        return joint.isEmpty() ? null : joint;
    }

    /**
     * Durée travaillée en minutes, recalculée depuis les heures d'arrivée/départ
     * ("HH:mm"). {@code null} tant que le départ n'est pas pointé (la source stocke
     * {@code Pointage.duree} en texte "9h"/"8h30mn", non exploitable tel quel).
     */
    private Integer computeDureeMinutes(String heureArrive, String heureDepart) {
        LocalTime arrivee = planning.parseHeure(heureArrive);
        LocalTime depart = planning.parseHeure(heureDepart);
        if (arrivee == null || depart == null) return null;
        long minutes = Duration.between(arrivee, depart).toMinutes();
        return minutes >= 0 ? (int) minutes : null;
    }

    /**
     * Retard brut en minutes = {@code heureArrivee - horaireDebut du créneau}, borné à
     * {@code >= 0} (arrivée en avance → 0). {@code 0} si l'une des deux heures est
     * absente. La tolérance n'est PAS appliquée ici : le champ porte le brut.
     */
    private int computeRetardMinutes(String heureArrive, LocalTime heurePrevue) {
        LocalTime arrivee = planning.parseHeure(heureArrive);
        if (arrivee == null || heurePrevue == null) return 0;
        long minutes = Duration.between(heurePrevue, arrivee).toMinutes();
        return minutes > 0 ? (int) minutes : 0;
    }
}
