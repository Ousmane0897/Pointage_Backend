package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.ResumeJourneeDto;
import com.example.Pointage_Cleanic.Dto.rh.PointageCentraliseDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Enum.rh.TypeConge;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Page;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Vue pointage centralisé — une ligne par <b>créneau</b> (employé × jour × site attendu).
 *
 * <p>Tous les tests fixent l'horloge : l'échelle de statuts compare « maintenant » à la
 * date-heure du créneau, donc un test qui laisserait courir l'horloge système changerait
 * de résultat selon le jour où il est joué. C'était déjà un piège latent de l'ancienne
 * suite, dont la date de référence était future à l'écriture et ne l'est plus.
 */
class PointageCentraliseServiceTest {

    /** Jeudi — jour ouvré dans les trois rythmes (LUN_VEN, LUN_SAM, LUN_DIM). */
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 11);
    private static final LocalDate SAMEDI = LocalDate.of(2026, 6, 13);
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 6, 14);
    private static final ZoneId ZONE = ZoneId.of("Africa/Dakar");

    private DossierEmployeRepository dossierEmployeRepository;
    private PointageRepository pointageRepository;
    private DemandeCongeRepository demandeCongeRepository;
    private PointageCentraliseService service;

    // emp1 = a pointé, emp2 = en congé, emp3 = rien. Tous trois ont désormais une
    // affectation : sans créneau attendu, aucune ligne ne serait produite.
    private final DossierEmploye emp1 = employe("emp1", "1001", "M001", "Diop", "Awa",
            "Exploitation", affectation("Keur Gorgui", "08:00", "17:00", "LUN_VEN"));
    private final DossierEmploye emp2 = employe("emp2", "1002", "M002", "Ndiaye", "Modou",
            "Administration", affectation("Siège", "08:00", "17:00", "LUN_VEN"));
    private final DossierEmploye emp3 = employe("emp3", "1003", "M003", "Fall", "Bineta",
            "Exploitation", affectation("Yoff", "08:00", "17:00", "LUN_VEN"));

    @BeforeEach
    void setup() {
        dossierEmployeRepository = mock(DossierEmployeRepository.class);
        pointageRepository = mock(PointageRepository.class);
        demandeCongeRepository = mock(DemandeCongeRepository.class);
        // Journée terminée : les créneaux non pointés sont ABSENT, pas EN_ATTENTE.
        rebuildService(JOUR, LocalTime.of(23, 0));

        when(dossierEmployeRepository.findByStatutIn(any()))
                .thenReturn(List.of(emp1, emp2, emp3));

        Pointage p1 = Pointage.builder()
                .id("p1").codeSecret("1001").nom("Diop").prenom("Awa")
                .site(new String[]{"Keur Gorgui", "Yoff"})
                .date(JOUR).heureArrive("08:15").heureDepart("16:45")
                .duree("8h30mn").status("TERMINÉ").build();
        stubPointages(JOUR, p1);

        DemandeConge conge = DemandeConge.builder()
                .id("c1").employeId("emp2").type(TypeConge.ANNUEL)
                .dateDebut(JOUR.minusDays(2)).dateFin(JOUR.plusDays(2))
                .statut(StatutDemande.APPROUVE).build();
        when(demandeCongeRepository.findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(StatutDemande.APPROUVE), eq(JOUR), eq(JOUR)))
                .thenReturn(List.of(conge));
    }

    // =====================================================================
    //  Fixtures
    // =====================================================================

    private void rebuildService(LocalDate jour, LocalTime heure) {
        Clock clock = Clock.fixed(jour.atTime(heure).atZone(ZONE).toInstant(), ZONE);
        service = new PointageCentraliseService(
                dossierEmployeRepository, pointageRepository, demandeCongeRepository,
                new PlanningAffectationResolver(), clock, 15);
    }

    private static AffectationSite affectation(String site, String debut, String fin, String jours) {
        return AffectationSite.builder()
                .site(site).horaireDebut(debut).horaireFin(fin).joursTravail(jours).build();
    }

    private static DossierEmploye employe(String id, String agentId, String matricule,
                                          String nom, String prenom, String departement,
                                          AffectationSite... affectations) {
        return DossierEmploye.builder()
                .id(id).agentId(agentId).matricule(matricule).nom(nom).prenom(prenom)
                .departement(departement).poste("Agent")
                .siteAffecte(Arrays.stream(affectations)
                        .map(AffectationSite::getSite).collect(Collectors.joining(" - ")))
                .statut(StatutDossierEmploye.ACTIF)
                .affectations(List.of(affectations))
                .build();
    }

    private void stubPointages(LocalDate jour, Pointage... pointages) {
        when(pointageRepository.findAllByDate(jour)).thenReturn(List.of(pointages));
    }

    private void stubAucunConge(LocalDate jour) {
        when(demandeCongeRepository.findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(StatutDemande.APPROUVE), eq(jour), eq(jour))).thenReturn(List.of());
    }

    private static Pointage pointage(String id, String agentId, LocalDate jour, String arrivee) {
        return Pointage.builder().id(id).codeSecret(agentId)
                .site(new String[]{"peu importe"}).date(jour).heureArrive(arrivee).build();
    }

    private List<PointageCentraliseDto> lignes(LocalDate jour) {
        return service.getPointages(jour, null, null, null, null, 0, 50).getContent();
    }

    private PointageCentraliseDto ligneUnique() {
        List<PointageCentraliseDto> lignes = lignes(JOUR);
        assertThat(lignes).hasSize(1);
        return lignes.get(0);
    }

    private Map<String, PointageCentraliseDto> parSite(List<PointageCentraliseDto> lignes) {
        return lignes.stream().collect(Collectors.toMap(PointageCentraliseDto::getSite, d -> d));
    }

    /** Employé bi-affecté : site A 06:00-14:00, site B 10:00-18:00. */
    private DossierEmploye employeBiSite() {
        return employe("empMS", "9100", "MS01", "Kane", "Multi", "Exploitation",
                affectation("A", "06:00", "14:00", "LUN_VEN"),
                affectation("B", "10:00", "18:00", "LUN_VEN"));
    }

    private void stubBiSite(String... arrivees) {
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(employeBiSite()));
        Pointage[] pointages = new Pointage[arrivees.length];
        for (int i = 0; i < arrivees.length; i++) {
            pointages[i] = pointage("p" + i, "9100", JOUR, arrivees[i]);
        }
        stubPointages(JOUR, pointages);
        stubAucunConge(JOUR);
    }

    // =====================================================================
    //  Socle : une ligne par créneau attendu
    // =====================================================================

    @Test
    void derive_present_conge_absent() {
        Map<String, PointageCentraliseDto> byEmp = lignes(JOUR).stream()
                .collect(Collectors.toMap(PointageCentraliseDto::getEmployeId, d -> d));

        PointageCentraliseDto present = byEmp.get("emp1");
        assertThat(present.getStatut()).isEqualTo("PRESENT");
        assertThat(present.getHeureArrivee()).isEqualTo("08:15");
        assertThat(present.getHeureDepart()).isEqualTo("16:45");
        assertThat(present.getDureeMinutes()).isEqualTo(510);
        assertThat(present.getRetardMinutes()).isEqualTo(15);   // 08:00 -> 08:15, sous tolérance
        // Le site vient de l'AFFECTATION, jamais du tableau Pointage.site[] (qui porte
        // ici « Keur Gorgui, Yoff », c'est-à-dire tous les sites de l'agent).
        assertThat(present.getSite()).isEqualTo("Keur Gorgui");
        assertThat(present.getSiteHoraireDebut()).isEqualTo("08:00");
        assertThat(present.getPointageId()).isEqualTo("p1");
        assertThat(present.isPlanifie()).isTrue();

        PointageCentraliseDto conge = byEmp.get("emp2");
        assertThat(conge.getStatut()).isEqualTo("CONGE");
        assertThat(conge.getMotif()).isEqualTo("Annuel");
        assertThat(conge.getHeureArrivee()).isNull();

        PointageCentraliseDto absent = byEmp.get("emp3");
        assertThat(absent.getStatut()).isEqualTo("ABSENT");
        assertThat(absent.getDureeMinutes()).isNull();
        assertThat(absent.getSite()).isEqualTo("Yoff");
    }

    @Test
    void bi_site_produit_deux_lignes_meme_sans_pointage() {
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(employeBiSite()));
        stubPointages(JOUR);
        stubAucunConge(JOUR);

        assertThat(lignes(JOUR)).extracting(PointageCentraliseDto::getSite)
                .containsExactly("A", "B");   // ordonné par horaire de début
    }

    @Test
    void site_hors_semaine_ouvree_ne_produit_aucune_ligne() {
        rebuildService(SAMEDI, LocalTime.of(23, 0));
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(
                employe("empS", "9300", "S001", "Sy", "Sam", "Exploitation",
                        affectation("LunVen", "08:00", "17:00", "LUN_VEN"),
                        affectation("LunSam", "08:00", "17:00", "LUN_SAM"))));
        stubPointages(SAMEDI);
        stubAucunConge(SAMEDI);

        assertThat(lignes(SAMEDI)).extracting(PointageCentraliseDto::getSite)
                .containsExactly("LunSam");
    }

    @Test
    void seul_lun_dim_travaille_le_dimanche() {
        rebuildService(DIMANCHE, LocalTime.of(23, 0));
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(
                employe("empD", "9301", "D001", "Diallo", "Dim", "Exploitation",
                        affectation("LunSam", "08:00", "17:00", "LUN_SAM"),
                        affectation("LunDim", "08:00", "17:00", "LUN_DIM"))));
        stubPointages(DIMANCHE);
        stubAucunConge(DIMANCHE);

        assertThat(lignes(DIMANCHE)).extracting(PointageCentraliseDto::getSite)
                .containsExactly("LunDim");
    }

    @Test
    void jours_travail_du_site_prime_sur_celui_de_l_employe() {
        rebuildService(SAMEDI, LocalTime.of(23, 0));
        DossierEmploye emp = employe("empJ", "9302", "J001", "Ba", "Jour", "Exploitation",
                affectation("Samedi ouvert", "08:00", "17:00", "LUN_SAM"));
        emp.setJoursTravail("LUN_VEN");   // le rythme employé dirait « fermé »
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(emp));
        stubPointages(SAMEDI);
        stubAucunConge(SAMEDI);

        assertThat(lignes(SAMEDI)).hasSize(1);
    }

    @Test
    void jours_travail_absent_du_site_retombe_sur_celui_de_l_employe() {
        rebuildService(SAMEDI, LocalTime.of(23, 0));
        DossierEmploye emp = employe("empJ2", "9303", "J002", "Ba", "Jour", "Exploitation",
                affectation("Sans rythme", "08:00", "17:00", null));
        emp.setJoursTravail("LUN_VEN");
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(emp));
        stubPointages(SAMEDI);
        stubAucunConge(SAMEDI);

        assertThat(lignes(SAMEDI)).isEmpty();
    }

    /**
     * Aucun rythme connu ⇒ aucun filtrage, et non un repli sur LUN_VEN : masquer le
     * samedi ferait disparaître une absence réelle sur l'écran qui sert à les tracer.
     */
    @Test
    void aucun_rythme_connu_reste_permissif_le_samedi() {
        rebuildService(SAMEDI, LocalTime.of(23, 0));
        DossierEmploye emp = employe("empJ3", "9304", "J003", "Ba", "Jour", "Exploitation",
                affectation("Legacy", "08:00", "17:00", null));   // ni site ni employé
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(emp));
        stubPointages(SAMEDI);
        stubAucunConge(SAMEDI);

        assertThat(lignes(SAMEDI)).hasSize(1);
    }

    @Test
    void periode_du_site_borne_les_lignes() {
        DossierEmploye emp = employe("empP", "9305", "P001", "Ndour", "Periode", "Exploitation",
                affectation("PasEncore", "08:00", "17:00", "LUN_VEN"),
                affectation("Termine", "08:00", "17:00", "LUN_VEN"),
                affectation("EnCours", "08:00", "17:00", "LUN_VEN"));
        emp.getAffectations().get(0).setDateEntree(JOUR.plusDays(1));    // arrive demain
        emp.getAffectations().get(1).setDateSortie(JOUR.minusDays(1));   // parti hier
        emp.getAffectations().get(2).setDateEntree(JOUR);                // arrive pile aujourd'hui
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(emp));
        stubPointages(JOUR);
        stubAucunConge(JOUR);

        assertThat(lignes(JOUR)).extracting(PointageCentraliseDto::getSite)
                .containsExactly("EnCours");
    }

    // =====================================================================
    //  Rattachement d'un pointage à un créneau
    // =====================================================================

    /**
     * Régression du bug d'origine : le retard est mesuré sur l'horaire du site où
     * l'agent s'est présenté. Avant, le repli « horaire le plus tôt » retenait 06:00, si
     * bien qu'une arrivée à 10:20 passait pour « en avance ».
     */
    @Test
    void retard_calcule_sur_l_horaire_du_site_pointe() {
        stubBiSite("10:20");

        Map<String, PointageCentraliseDto> parSite = parSite(lignes(JOUR));

        assertThat(parSite.get("B").getStatut()).isEqualTo("RETARD");
        assertThat(parSite.get("B").getRetardMinutes()).isEqualTo(20);
        assertThat(parSite.get("A").getPointageId()).isNull();
    }

    /** Fenêtres chevauchantes : 10:05 revient au créneau qui vient de s'ouvrir. */
    @Test
    void contenance_retient_la_fenetre_la_plus_recemment_ouverte() {
        stubBiSite("10:05");

        Map<String, PointageCentraliseDto> parSite = parSite(lignes(JOUR));

        assertThat(parSite.get("B").getPointageId()).isNotNull();
        assertThat(parSite.get("B").getRetardMinutes()).isEqualTo(5);
        assertThat(parSite.get("A").getPointageId()).isNull();
    }

    /** Hors de toute fenêtre : le créneau dont le début est le plus proche l'emporte. */
    @Test
    void proximite_choisit_le_debut_le_plus_proche() {
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(
                employe("empX", "9400", "X001", "Sarr", "Prox", "Exploitation",
                        affectation("Matin", "06:00", "07:00", "LUN_VEN"),
                        affectation("Soir", "10:00", "11:00", "LUN_VEN"))));
        stubPointages(JOUR, pointage("px", "9400", JOUR, "09:30"));
        stubAucunConge(JOUR);

        Map<String, PointageCentraliseDto> parSite = parSite(lignes(JOUR));

        assertThat(parSite.get("Soir").getPointageId()).isEqualTo("px");   // |30| < |210|
        assertThat(parSite.get("Matin").getPointageId()).isNull();
    }

    @Test
    void un_creneau_n_accueille_qu_un_seul_pointage_le_reste_est_hors_plan() {
        stubBiSite("06:12", "10:20", "06:40");

        List<PointageCentraliseDto> lignes = lignes(JOUR);

        assertThat(lignes).hasSize(3);
        Map<String, PointageCentraliseDto> parSite = parSite(
                lignes.stream().filter(PointageCentraliseDto::isPlanifie).toList());
        assertThat(parSite.get("A").getRetardMinutes()).isEqualTo(12);
        assertThat(parSite.get("A").getStatut()).isEqualTo("PRESENT");
        assertThat(parSite.get("B").getRetardMinutes()).isEqualTo(20);
        assertThat(parSite.get("B").getStatut()).isEqualTo("RETARD");

        List<PointageCentraliseDto> horsPlan = lignes.stream()
                .filter(l -> !l.isPlanifie()).toList();
        assertThat(horsPlan).hasSize(1);
        assertThat(horsPlan.get(0).getStatut()).isEqualTo("HORS_PLAN");
        assertThat(horsPlan.get(0).getHeureArrivee()).isEqualTo("06:40");
    }

    /** Un pointage sans créneau attendu ne disparaît jamais silencieusement. */
    @Test
    void pointage_sans_affectation_reste_visible_en_hors_plan() {
        DossierEmploye sansAffectation = DossierEmploye.builder()
                .id("empN").agentId("9500").matricule("N001").nom("Sow").prenom("Nada")
                .departement("Exploitation").siteAffecte("Alpha").poste("Agent")
                .statut(StatutDossierEmploye.ACTIF).build();
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(sansAffectation));
        stubPointages(JOUR, pointage("pn", "9500", JOUR, "10:30"));
        stubAucunConge(JOUR);

        PointageCentraliseDto ligne = ligneUnique();

        assertThat(ligne.getStatut()).isEqualTo("HORS_PLAN");
        assertThat(ligne.isPlanifie()).isFalse();
        assertThat(ligne.getRetardMinutes()).isZero();
        assertThat(ligne.getSiteHoraireDebut()).isNull();
    }

    @Test
    void creneau_sans_horaire_debut_ne_capte_aucun_pointage() {
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(
                employe("empH", "9600", "H001", "Ka", "Sans", "Exploitation",
                        affectation("SansHoraire", null, null, "LUN_VEN"))));
        stubPointages(JOUR, pointage("ph", "9600", JOUR, "10:30"));
        stubAucunConge(JOUR);

        List<PointageCentraliseDto> lignes = lignes(JOUR);

        assertThat(lignes).hasSize(2);
        PointageCentraliseDto creneau = lignes.stream()
                .filter(PointageCentraliseDto::isPlanifie).findFirst().orElseThrow();
        assertThat(creneau.getPointageId()).isNull();
        assertThat(creneau.getRetardMinutes()).isZero();
        assertThat(lignes.stream().filter(l -> !l.isPlanifie()).findFirst().orElseThrow()
                .getStatut()).isEqualTo("HORS_PLAN");
    }

    // =====================================================================
    //  Échelle de statuts dans le temps
    // =====================================================================

    @Test
    void avant_le_debut_du_creneau_le_statut_est_neutre() {
        rebuildService(JOUR, LocalTime.of(7, 0));   // créneau à 08:00
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(emp3));
        stubPointages(JOUR);
        stubAucunConge(JOUR);

        assertThat(ligneUnique().getStatut()).isEqualTo("NEUTRE");
    }

    @Test
    void pendant_le_creneau_le_statut_est_en_attente() {
        rebuildService(JOUR, LocalTime.of(9, 0));
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(emp3));
        stubPointages(JOUR);
        stubAucunConge(JOUR);

        assertThat(ligneUnique().getStatut()).isEqualTo("EN_ATTENTE");
    }

    @Test
    void apres_la_fin_du_creneau_le_statut_est_absent() {
        rebuildService(JOUR, LocalTime.of(18, 0));
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(emp3));
        stubPointages(JOUR);
        stubAucunConge(JOUR);

        assertThat(ligneUnique().getStatut()).isEqualTo("ABSENT");
    }

    /** Un second site dont l'heure n'est pas encore arrivée reste neutre (grisé). */
    @Test
    void second_site_pas_encore_commence_reste_neutre() {
        rebuildService(JOUR, LocalTime.of(8, 0));   // A ouvert depuis 06:00, B ouvre à 10:00
        stubBiSite("06:05");

        Map<String, PointageCentraliseDto> parSite = parSite(lignes(JOUR));

        assertThat(parSite.get("A").getStatut()).isEqualTo("PRESENT");
        assertThat(parSite.get("B").getStatut()).isEqualTo("NEUTRE");
    }

    /** Jour passé : la journée est finie, NEUTRE et EN_ATTENTE sont inatteignables. */
    @Test
    void jour_passe_ne_produit_ni_neutre_ni_en_attente() {
        LocalDate hier = JOUR.minusDays(1);
        rebuildService(JOUR, LocalTime.of(7, 0));   // « maintenant » avant tout créneau du jour
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(emp3));
        stubPointages(hier);
        stubAucunConge(hier);

        assertThat(lignes(hier)).extracting(PointageCentraliseDto::getStatut)
                .containsExactly("ABSENT");
    }

    /** Jour futur : on ne peut pas être absent d'une journée qui n'a pas eu lieu. */
    @Test
    void jour_futur_est_integralement_neutre() {
        LocalDate demain = JOUR.plusDays(1);
        rebuildService(JOUR, LocalTime.of(23, 0));
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(emp3));
        stubPointages(demain);
        stubAucunConge(demain);

        assertThat(service.getPointagesRange(demain, demain, null, null, null, null, 0, 20)
                .getContent()).extracting(PointageCentraliseDto::getStatut)
                .containsExactly("NEUTRE");
    }

    @Test
    void conge_produit_une_seule_ligne_meme_multi_sites() {
        DossierEmploye biSite = employeBiSite();
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(biSite));
        stubPointages(JOUR, pointage("pc", "9100", JOUR, "06:05"));
        when(demandeCongeRepository.findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(StatutDemande.APPROUVE), eq(JOUR), eq(JOUR)))
                .thenReturn(List.of(DemandeConge.builder()
                        .id("c2").employeId("empMS").type(TypeConge.ANNUEL)
                        .dateDebut(JOUR).dateFin(JOUR).statut(StatutDemande.APPROUVE).build()));

        assertThat(lignes(JOUR)).extracting(PointageCentraliseDto::getStatut)
                .containsExactly("CONGE");
    }

    // =====================================================================
    //  Tolérance de retard (seuil strict)
    // =====================================================================

    @ParameterizedTest
    @CsvSource({
            "09:10, 10, PRESENT",
            "09:13, 13, PRESENT",
            "09:15, 15, PRESENT",   // seuil strict : 15 pile = PAS en retard
            "09:16, 16, RETARD",
            "09:30, 30, RETARD"
    })
    void retard_seuil_strict_15min(String arrivee, int retardAttendu, String statutAttendu) {
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(
                employe("empR", "9001", "R001", "Sow", "Test", "Exploitation",
                        affectation("Alpha", "09:00", "17:00", "LUN_VEN"))));
        stubPointages(JOUR, pointage("pr", "9001", JOUR, arrivee));
        stubAucunConge(JOUR);

        PointageCentraliseDto ligne = ligneUnique();

        assertThat(ligne.getRetardMinutes()).isEqualTo(retardAttendu);
        assertThat(ligne.getStatut()).isEqualTo(statutAttendu);
    }

    // =====================================================================
    //  Identifiants, filtres, pagination, résumé
    // =====================================================================

    /**
     * L'identifiant appartient au créneau : il ne bouge pas quand l'agent finit par
     * pointer, sinon le {@code trackBy} Angular recréerait la ligne à chaque
     * rafraîchissement traversant un pointage.
     */
    @Test
    void id_de_ligne_stable_avant_et_apres_le_pointage() {
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(emp3));
        stubPointages(JOUR);
        stubAucunConge(JOUR);
        PointageCentraliseDto avant = ligneUnique();

        stubPointages(JOUR, pointage("pid", "1003", JOUR, "08:05"));
        PointageCentraliseDto apres = ligneUnique();

        assertThat(apres.getId()).isEqualTo(avant.getId());
        assertThat(avant.getPointageId()).isNull();
        assertThat(apres.getPointageId()).isEqualTo("pid");
    }

    @Test
    void ids_uniques_sur_une_journee_multi_sites() {
        stubBiSite("06:12", "10:20", "06:40");

        assertThat(lignes(JOUR)).extracting(PointageCentraliseDto::getId)
                .doesNotHaveDuplicates();
    }

    @Test
    void filtre_departement() {
        assertThat(service.getPointages(JOUR, "Exploitation", null, null, null, 0, 20).getContent())
                .extracting(PointageCentraliseDto::getEmployeId)
                .containsExactlyInAnyOrder("emp1", "emp3");
    }

    @Test
    void filtre_statut() {
        Page<PointageCentraliseDto> page =
                service.getPointages(JOUR, null, null, "ABSENT", null, 0, 20);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getEmployeId()).isEqualTo("emp3");
    }

    @Test
    void filtre_q_par_prenom_et_matricule() {
        assertThat(service.getPointages(JOUR, null, null, null, "bineta", 0, 20).getContent())
                .extracting(PointageCentraliseDto::getEmployeId).containsExactly("emp3");
        assertThat(service.getPointages(JOUR, null, null, null, "M002", 0, 20).getContent())
                .extracting(PointageCentraliseDto::getEmployeId).containsExactly("emp2");
    }

    /**
     * Le filtre site porte sur la LIGNE : sur un agent bi-site, il ne rend que le site
     * demandé. Auparavant il testait {@code siteAffecte} et rendait donc tout l'employé.
     */
    @Test
    void filtre_site_est_applique_a_la_ligne() {
        stubBiSite("06:12", "10:20");

        Page<PointageCentraliseDto> page =
                service.getPointages(JOUR, null, "B", null, null, 0, 20);

        assertThat(page.getContent()).extracting(PointageCentraliseDto::getSite)
                .containsExactly("B");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void pagination_reflete_le_nombre_de_lignes_filtrees() {
        Page<PointageCentraliseDto> page0 = service.getPointages(JOUR, null, null, null, null, 0, 2);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(3);

        Page<PointageCentraliseDto> page1 = service.getPointages(JOUR, null, null, null, null, 1, 2);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getTotalElements()).isEqualTo(3);
    }

    @Test
    void resume_respecte_l_invariant_des_creneaux() {
        ResumeJourneeDto resume = service.getResume(JOUR);

        assertThat(resume.getDate()).isEqualTo(JOUR);
        assertThat(resume.getTotalEmployes()).isEqualTo(3);
        assertThat(resume.getPresents()).isEqualTo(1);
        assertThat(resume.getAbsents()).isEqualTo(1);
        assertThat(resume.getEnConge()).isEqualTo(1);
        assertThat(resume.getRetards()).isZero();
        assertThat(resume.getHorsPlan()).isZero();
        // Le congé compte des PERSONNES : il n'entre pas dans l'invariant des créneaux.
        assertThat(resume.getPresents() + resume.getRetards() + resume.getAbsents()
                + resume.getEnAttente() + resume.getNeutres())
                .isEqualTo(resume.getCreneauxPrevus());
    }

    @Test
    void resume_compte_les_creneaux_pas_les_employes() {
        stubBiSite("06:12", "10:20", "06:40");

        ResumeJourneeDto resume = service.getResume(JOUR);

        assertThat(resume.getTotalEmployes()).isEqualTo(1);   // une seule personne
        assertThat(resume.getCreneauxPrevus()).isEqualTo(2);  // mais deux créneaux
        assertThat(resume.getPresents()).isEqualTo(1);
        assertThat(resume.getRetards()).isEqualTo(1);
        assertThat(resume.getHorsPlan()).isEqualTo(1);        // isolé, pas fondu dans absents
        assertThat(resume.getAbsents()).isZero();
    }

    @Test
    void range_dateFin_avant_dateDebut_leve_exception() {
        assertThatThrownBy(() -> service.getPointagesRange(
                JOUR, JOUR.minusDays(1), null, null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dateFin");
    }
}
