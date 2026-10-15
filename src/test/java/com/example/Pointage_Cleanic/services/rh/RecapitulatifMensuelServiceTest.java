package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.RecapitulatifMensuelDto;
import com.example.Pointage_Cleanic.Dto.rh.RecapitulatifMensuelDto.RecapSiteDto;
import com.example.Pointage_Cleanic.Enum.rh.TypeConge;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.rh.HeureSupplementaireRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Récapitulatif mensuel — jours ouvrables par employé, fériés et absences.
 *
 * <p>Fenêtre figée : <b>septembre 2026</b>, 30 jours du mardi 1er au mercredi 30, soit
 * 22 jours ouvrés lundi-vendredi et 26 lundi-samedi.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecapitulatifMensuelServiceTest {

    @Mock private DossierEmployeRepository dossierEmployeRepository;
    @Mock private PointageRepository pointageRepository;
    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private HeureSupplementaireRepository heureSupplementaireRepository;
    @Mock private JourFerieService jourFerieService;

    private RecapitulatifMensuelService service;

    private static final LocalDate FERIE_MERCREDI = LocalDate.of(2026, 9, 16);

    /**
     * Horloge figée APRÈS septembre 2026 : tous les jours du mois testé sont passés, et le
     * résultat ne dépend jamais du jour d'exécution.
     */
    private static final Clock APRES_SEPTEMBRE = horloge(LocalDate.of(2026, 10, 15));

    private static Clock horloge(LocalDate jour) {
        ZoneId dakar = ZoneId.of("Africa/Dakar");
        return Clock.fixed(jour.atTime(12, 0).atZone(dakar).toInstant(), dakar);
    }

    /** Service câblé sur le VRAI pointage centralisé : c'est lui qui juge les créneaux. */
    private RecapitulatifMensuelService construire(Clock clock) {
        PlanningAffectationResolver planning = new PlanningAffectationResolver();
        PointageCentraliseService pointageCentralise = new PointageCentraliseService(
                dossierEmployeRepository, pointageRepository, demandeCongeRepository,
                planning, jourFerieService, clock, 15);
        return new RecapitulatifMensuelService(
                dossierEmployeRepository, pointageRepository, demandeCongeRepository,
                heureSupplementaireRepository,
                new CalendrierTravailService(planning),
                jourFerieService, pointageCentralise, clock);
    }

    @BeforeEach
    void setUp() {
        service = construire(APRES_SEPTEMBRE);

        when(jourFerieService.datesFeriees(any(), any())).thenReturn(Set.of());
        when(heureSupplementaireRepository.findByStatutAndDateBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of());
        when(pointageRepository.findByDateBetween(any(), any())).thenReturn(List.of());
    }

    private static DossierEmploye employe(String id, String rythme) {
        DossierEmploye e = new DossierEmploye();
        e.setId(id);
        e.setAgentId(id);
        e.setMatricule("M-" + id);
        e.setNom("Diop");
        e.setPrenom("Awa");
        e.setStatut(StatutDossierEmploye.ACTIF);
        e.setAffectations(new ArrayList<>(List.of(
                AffectationSite.builder().site("Yoff").joursTravail(rythme).build())));
        return e;
    }

    private static Pointage pointage(String agentId, LocalDate date) {
        Pointage p = new Pointage();
        p.setCodeSecret(agentId);
        p.setDate(date);
        return p;
    }

    private static Pointage pointage(String agentId, LocalDate date, String arrivee, String depart) {
        Pointage p = pointage(agentId, date);
        p.setHeureArrive(arrivee);
        p.setHeureDepart(depart);
        return p;
    }

    /** Agent lundi-vendredi portant les créneaux donnés (site, début, fin). */
    private static DossierEmploye employeCreneaux(String id, String[]... creneaux) {
        DossierEmploye e = employe(id, "LUN_VEN");
        List<AffectationSite> affectations = new ArrayList<>();
        for (String[] c : creneaux) {
            affectations.add(AffectationSite.builder().site(c[0]).horaireDebut(c[1]).horaireFin(c[2])
                    .joursTravail("LUN_VEN").build());
        }
        e.setAffectations(affectations);
        return e;
    }

    private static DemandeConge conge(String employeId, TypeConge type, LocalDate du, LocalDate au) {
        DemandeConge c = new DemandeConge();
        c.setEmployeId(employeId);
        c.setType(type);
        c.setDateDebut(du);
        c.setDateFin(au);
        return c;
    }

    private static RecapSiteDto site(RecapitulatifMensuelDto ligne, String nom) {
        return ligne.getSites().stream().filter(s -> s.getSite().equals(nom)).findFirst().orElseThrow();
    }

    private RecapitulatifMensuelDto recap(DossierEmploye e) {
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(e));
        List<RecapitulatifMensuelDto> lignes =
                service.getRecapitulatifDetaille(9, 2026, null, null, null);
        assertThat(lignes).hasSize(1);
        return lignes.get(0);
    }

    // --- Jours ouvrables par rythme -------------------------------------------

    @Test
    void un_agent_lundi_vendredi_a_22_jours_ouvrables() {
        assertThat(recap(employe("a1", "LUN_VEN")).getJoursOuvrables()).isEqualTo(22);
    }

    @Test
    void un_agent_de_terrain_lundi_samedi_a_26_jours_ouvrables() {
        // ⚠ C'est LE bug corrigé : le calcul lundi-vendredi en dur ignorait ses 4 samedis,
        // et ses absences étaient donc systématiquement fausses.
        assertThat(recap(employe("a2", "LUN_SAM")).getJoursOuvrables()).isEqualTo(26);
    }

    // --- Jours fériés ----------------------------------------------------------

    @Test
    void un_ferie_sort_des_jours_ouvrables_sans_creer_d_absence() {
        when(jourFerieService.datesFeriees(any(), any())).thenReturn(Set.of(FERIE_MERCREDI));

        RecapitulatifMensuelDto ligne = recap(employe("a3", "LUN_VEN"));

        assertThat(ligne.getJoursOuvrables()).isEqualTo(21);
        assertThat(ligne.getJoursFeries()).isEqualTo(1);
        assertThat(ligne.getJoursTravaillesFeries()).isZero();
        // 21 dus, 0 pointé : 21 absences — le férié n'en fait pas une 22e.
        assertThat(ligne.getJoursAbsence()).isEqualTo(21);
    }

    @Test
    void un_ferie_travaille_est_compte_comme_jour_travaille_sans_fausser_les_absences() {
        when(jourFerieService.datesFeriees(any(), any())).thenReturn(Set.of(FERIE_MERCREDI));
        when(pointageRepository.findByDateBetween(any(), any()))
                .thenReturn(List.of(pointage("a4", FERIE_MERCREDI)));

        RecapitulatifMensuelDto ligne = recap(employe("a4", "LUN_VEN"));

        // ⚠ Deux compteurs distincts : le travail réel se voit (1 jour travaillé) sans
        // entrer dans le calcul des absences, qui ne porte que sur les jours dus (21).
        assertThat(ligne.getJoursTravailles()).isEqualTo(1);
        assertThat(ligne.getJoursTravaillesFeries()).isEqualTo(1);
        assertThat(ligne.getJoursOuvrables()).isEqualTo(21);
        assertThat(ligne.getJoursAbsence()).isEqualTo(21);
    }

    // --- Congés ----------------------------------------------------------------

    @Test
    void un_conge_a_cheval_sur_deux_mois_n_est_compte_que_pour_sa_part_du_mois() {
        // ⚠ Correction : la somme des `nombreJours` comptait la demande EN ENTIER dans
        // chacun des deux mois, ce qui pouvait ramener les absences à zéro et masquer
        // une absence réelle.
        DemandeConge c = new DemandeConge();
        c.setEmployeId("a5");
        c.setDateDebut(LocalDate.of(2026, 8, 24));
        c.setDateFin(LocalDate.of(2026, 9, 4));
        c.setNombreJours(10);
        when(demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(c));

        RecapitulatifMensuelDto ligne = recap(employe("a5", "LUN_VEN"));

        // Du mardi 1er au vendredi 4 septembre : 4 jours ouvrés, pas 10.
        assertThat(ligne.getJoursConge()).isEqualTo(4);
        assertThat(ligne.getJoursAbsence()).isEqualTo(18);
    }

    @Test
    void un_jour_a_la_fois_pointe_et_en_conge_n_est_pas_decompte_deux_fois() {
        LocalDate jeudi3 = LocalDate.of(2026, 9, 3);
        DemandeConge c = new DemandeConge();
        c.setEmployeId("a6");
        c.setDateDebut(jeudi3);
        c.setDateFin(jeudi3);
        when(demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(c));
        when(pointageRepository.findByDateBetween(any(), any()))
                .thenReturn(List.of(pointage("a6", jeudi3)));

        RecapitulatifMensuelDto ligne = recap(employe("a6", "LUN_VEN"));

        // 22 dus, 1 seul jour couvert (et non 2) ⇒ 21 absences, jamais 20.
        assertThat(ligne.getJoursAbsence()).isEqualTo(21);
    }

    // --- Discipline de lecture -------------------------------------------------

    @Test
    void le_calendrier_des_feries_n_est_lu_qu_une_fois_pour_tout_le_tableau() {
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(
                employe("a7", "LUN_VEN"), employe("a8", "LUN_SAM"), employe("a9", "LUN_DIM")));

        service.getRecapitulatifDetaille(9, 2026, null, null, null);

        // ⚠ Une lecture par employé serait N requêtes Mongo pour une valeur identique, et
        // deux lignes du tableau pourraient reposer sur des calendriers différents si
        // l'exécution chevauchait une saisie RH.
        verify(jourFerieService, times(1)).datesFeriees(any(), any());
    }

    // --- Détail par site : heures, retards, absences ----------------------------

    private static final LocalDate MARDI_1ER = LocalDate.of(2026, 9, 1);

    @Test
    void un_agent_bi_site_a_une_ligne_par_site_et_un_seul_jour_travaille() {
        when(pointageRepository.findByDateBetween(any(), any())).thenReturn(List.of(
                pointage("b1", MARDI_1ER, "06:20", "10:00"),     // 20 min de retard
                pointage("b1", MARDI_1ER, "15:05", "19:00")));   // 5 min : toléré

        RecapitulatifMensuelDto ligne = recap(employeCreneaux("b1",
                new String[]{"Yoff", "06:00", "10:00"}, new String[]{"Ouakam", "15:00", "19:00"}));

        assertThat(ligne.getSites()).extracting(RecapSiteDto::getSite).containsExactly("Ouakam", "Yoff");
        assertThat(site(ligne, "Yoff").getNombreRetards()).isEqualTo(1);
        assertThat(site(ligne, "Yoff").getMinutesRetardTotal()).isEqualTo(20);
        assertThat(site(ligne, "Yoff").getMinutesTravaillees()).isEqualTo(220);
        assertThat(site(ligne, "Ouakam").getNombreRetards()).isZero();
        assertThat(site(ligne, "Ouakam").getMinutesTravaillees()).isEqualTo(235);
        // 21 créneaux non pointés sur chaque site.
        assertThat(site(ligne, "Yoff").getCreneauxAbsents()).isEqualTo(21);

        // Cumuls agent : minutes et retards s'additionnent, ⚠ pas les jours.
        assertThat(ligne.getMinutesTravaillees()).isEqualTo(455);
        assertThat(ligne.getNombreRetards()).isEqualTo(1);
        assertThat(ligne.getMinutesRetardTotal()).isEqualTo(20);
        assertThat(ligne.getJoursTravailles()).isEqualTo(1);
        assertThat(site(ligne, "Yoff").getJoursTravailles()).isEqualTo(1);
        assertThat(site(ligne, "Ouakam").getJoursTravailles()).isEqualTo(1);
    }

    @Test
    void deux_creneaux_sur_un_meme_site_ne_forment_qu_une_ligne() {
        when(pointageRepository.findByDateBetween(any(), any())).thenReturn(List.of(
                pointage("b2", MARDI_1ER, "06:00", "10:00"),
                pointage("b2", MARDI_1ER, "15:00", "19:00")));

        RecapitulatifMensuelDto ligne = recap(employeCreneaux("b2",
                new String[]{"Yoff", "06:00", "10:00"}, new String[]{"Yoff", "15:00", "19:00"}));

        assertThat(ligne.getSites()).hasSize(1);
        RecapSiteDto yoff = site(ligne, "Yoff");
        assertThat(yoff.getHoraires()).isEqualTo("06:00–10:00, 15:00–19:00");
        assertThat(yoff.getCreneauxPrevus()).isEqualTo(44);
        assertThat(yoff.getJoursTravailles()).isEqualTo(1);
        assertThat(yoff.getMinutesTravaillees()).isEqualTo(480);
    }

    @Test
    void un_creneau_sans_depart_est_signale_sans_heures_inventees() {
        when(pointageRepository.findByDateBetween(any(), any())).thenReturn(List.of(
                pointage("b3", MARDI_1ER, "06:00", null)));

        RecapitulatifMensuelDto ligne = recap(employeCreneaux("b3", new String[]{"Yoff", "06:00", "10:00"}));

        assertThat(ligne.getMinutesTravaillees()).isZero();
        assertThat(ligne.getCreneauxSansDepart()).isEqualTo(1);
        assertThat(ligne.getJoursTravailles()).isEqualTo(1);
    }

    @Test
    void un_pointage_inexplique_forme_une_ligne_hors_planning_en_dernier() {
        // Samedi : aucun créneau prévu pour un agent lundi-vendredi.
        when(pointageRepository.findByDateBetween(any(), any())).thenReturn(List.of(
                pointage("b4", LocalDate.of(2026, 9, 5), "08:00", "12:00")));

        RecapitulatifMensuelDto ligne = recap(employeCreneaux("b4", new String[]{"Yoff", "06:00", "10:00"}));

        assertThat(ligne.getSites()).extracting(RecapSiteDto::getSite)
                .containsExactly("Yoff", RecapitulatifMensuelService.SITE_HORS_PLANNING);
        assertThat(ligne.getHorsPlan()).isEqualTo(1);
        // Le travail réel compte, même hors planning.
        assertThat(ligne.getMinutesTravaillees()).isEqualTo(240);
    }

    @Test
    void le_filtre_site_ne_garde_que_les_lignes_du_site_et_conserve_les_cumuls() {
        when(pointageRepository.findByDateBetween(any(), any())).thenReturn(List.of(
                pointage("b5", MARDI_1ER, "06:00", "10:00"),
                pointage("b5", MARDI_1ER, "15:00", "19:00")));
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(employeCreneaux("b5",
                new String[]{"Yoff", "06:00", "10:00"}, new String[]{"Ouakam", "15:00", "19:00"})));

        RecapitulatifMensuelDto ligne = service.getRecapitulatifDetaille(9, 2026, null, "ouakam", null).get(0);

        assertThat(ligne.getSites()).extracting(RecapSiteDto::getSite).containsExactly("Ouakam");
        assertThat(ligne.getMinutesTravaillees()).isEqualTo(480);
    }

    // --- Repos médical et absences ---------------------------------------------

    @Test
    void un_repos_medical_n_est_ni_une_absence_ni_un_conge() {
        when(demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(conge("c1", TypeConge.REPOS_MEDICAL,
                        LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11))));

        RecapitulatifMensuelDto ligne = recap(employe("c1", "LUN_VEN"));

        assertThat(ligne.getJoursReposMedical()).isEqualTo(5);
        assertThat(ligne.getJoursConge()).isZero();
        assertThat(ligne.getJoursAbsence()).isEqualTo(17);
    }

    @Test
    void une_absence_non_justifiee_approuvee_reste_une_absence() {
        when(demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(conge("c2", TypeConge.ABSENCE_NON_JUSTIFIEE,
                        LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11))));

        RecapitulatifMensuelDto ligne = recap(employe("c2", "LUN_VEN"));

        assertThat(ligne.getJoursConge()).isZero();
        assertThat(ligne.getJoursAbsence()).isEqualTo(22);
    }

    @Test
    void un_jour_encore_a_venir_n_est_pas_une_absence() {
        service = construire(horloge(LocalDate.of(2026, 9, 10)));   // jeudi 10

        RecapitulatifMensuelDto ligne = recap(employe("c3", "LUN_VEN"));

        // Dus jusqu'au 10 inclus : 1, 2, 3, 4, 7, 8, 9, 10.
        assertThat(ligne.getJoursOuvrables()).isEqualTo(22);
        assertThat(ligne.getJoursAbsence()).isEqualTo(8);
    }

    // --- Code agent ------------------------------------------------------------

    @Test
    void la_date_du_code_agent_est_restituee() {
        DossierEmploye e = employe("c4", "LUN_VEN");
        e.setDateCreationAgentId(LocalDateTime.of(2025, 3, 2, 9, 30));
        e.setDateCreationAgentIdEstimee(true);

        RecapitulatifMensuelDto ligne = recap(e);

        assertThat(ligne.getAgentId()).isEqualTo("c4");
        assertThat(ligne.getDateCreationAgentId()).isEqualTo(LocalDateTime.of(2025, 3, 2, 9, 30));
        assertThat(ligne.isDateCreationAgentIdEstimee()).isTrue();
    }
}
