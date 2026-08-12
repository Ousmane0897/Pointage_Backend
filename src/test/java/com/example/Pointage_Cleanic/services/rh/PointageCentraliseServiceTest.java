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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointageCentraliseServiceTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 6, 11);

    private DossierEmployeRepository dossierEmployeRepository;
    private PointageRepository pointageRepository;
    private DemandeCongeRepository demandeCongeRepository;
    private PointageCentraliseService service;

    // emp1 = présent (a pointé), emp2 = en congé, emp3 = absent (rien)
    private final DossierEmploye emp1 = DossierEmploye.builder()
            .id("emp1").agentId("1001").matricule("M001").nom("Diop").prenom("Awa")
            .departement("Exploitation").siteAffecte("Keur Gorgui").poste("Agent")
            .statut(StatutDossierEmploye.ACTIF).build();
    private final DossierEmploye emp2 = DossierEmploye.builder()
            .id("emp2").agentId("1002").matricule("M002").nom("Ndiaye").prenom("Modou")
            .departement("Administration").siteAffecte("Siège").poste("Comptable")
            .statut(StatutDossierEmploye.EN_PERIODE_ESSAI).build();
    private final DossierEmploye emp3 = DossierEmploye.builder()
            .id("emp3").agentId("1003").matricule("M003").nom("Fall").prenom("Bineta")
            .departement("Exploitation").siteAffecte("Yoff").poste("Agent")
            .statut(StatutDossierEmploye.ACTIF).build();

    @BeforeEach
    void setup() {
        dossierEmployeRepository = mock(DossierEmployeRepository.class);
        pointageRepository = mock(PointageRepository.class);
        demandeCongeRepository = mock(DemandeCongeRepository.class);
        service = new PointageCentraliseService(
                dossierEmployeRepository, pointageRepository, demandeCongeRepository, 15);

        when(dossierEmployeRepository.findByStatutIn(any()))
                .thenReturn(List.of(emp1, emp2, emp3));

        // emp1 a pointé (arrivée + départ → durée recalculée), clé = agentId
        Pointage p1 = Pointage.builder()
                .id("p1").codeSecret("1001").nom("Diop").prenom("Awa")
                .site(new String[]{"Keur Gorgui", "Yoff"})
                .date(JOUR).heureArrive("08:15").heureDepart("16:45")
                .duree("8h30mn").status("TERMINÉ").build();
        when(pointageRepository.findAllByDate(JOUR)).thenReturn(List.of(p1));

        // emp2 en congé approuvé couvrant le jour
        DemandeConge conge = DemandeConge.builder()
                .id("c1").employeId("emp2").type(TypeConge.ANNUEL)
                .dateDebut(JOUR.minusDays(2)).dateFin(JOUR.plusDays(2))
                .statut(StatutDemande.APPROUVE).build();
        when(demandeCongeRepository.findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(StatutDemande.APPROUVE), eq(JOUR), eq(JOUR)))
                .thenReturn(List.of(conge));
    }

    private Map<String, PointageCentraliseDto> parEmploye(Page<PointageCentraliseDto> page) {
        return page.getContent().stream()
                .collect(Collectors.toMap(PointageCentraliseDto::getEmployeId, d -> d));
    }

    @Test
    void derive_present_conge_absent() {
        Map<String, PointageCentraliseDto> byEmp =
                parEmploye(service.getPointages(JOUR, null, null, null, null, 0, 20));

        PointageCentraliseDto present = byEmp.get("emp1");
        assertThat(present.getStatut()).isEqualTo("PRESENT");
        assertThat(present.getId()).isEqualTo("p1");                 // id du pointage
        assertThat(present.getHeureArrivee()).isEqualTo("08:15");
        assertThat(present.getHeureDepart()).isEqualTo("16:45");
        assertThat(present.getDureeMinutes()).isEqualTo(510);        // 8h30 recalculé
        assertThat(present.getRetardMinutes()).isZero();
        assertThat(present.getSite()).isEqualTo("Keur Gorgui, Yoff"); // sites du pointage

        PointageCentraliseDto conge = byEmp.get("emp2");
        assertThat(conge.getStatut()).isEqualTo("CONGE");
        assertThat(conge.getMotif()).isEqualTo("Annuel");   // libellé, pas le nom de l'enum
        assertThat(conge.getHeureArrivee()).isNull();

        PointageCentraliseDto absent = byEmp.get("emp3");
        assertThat(absent.getStatut()).isEqualTo("ABSENT");
        assertThat(absent.getId()).isEqualTo("emp3-" + JOUR);        // clé synthétique
        assertThat(absent.getDureeMinutes()).isNull();
        assertThat(absent.getSite()).isEqualTo("Yoff");              // siteAffecte employé
    }

    @Test
    void filtre_departement() {
        Page<PointageCentraliseDto> page =
                service.getPointages(JOUR, "Exploitation", null, null, null, 0, 20);
        assertThat(page.getContent()).extracting(PointageCentraliseDto::getEmployeId)
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

    @Test
    void filtre_site() {
        Page<PointageCentraliseDto> page =
                service.getPointages(JOUR, null, "Siège", null, null, 0, 20);
        assertThat(page.getContent()).extracting(PointageCentraliseDto::getEmployeId)
                .containsExactly("emp2");
    }

    @Test
    void pagination() {
        Page<PointageCentraliseDto> page0 = service.getPointages(JOUR, null, null, null, null, 0, 2);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(3);

        Page<PointageCentraliseDto> page1 = service.getPointages(JOUR, null, null, null, null, 1, 2);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getTotalElements()).isEqualTo(3);
    }

    @Test
    void resume_coherent() {
        ResumeJourneeDto resume = service.getResume(JOUR);
        assertThat(resume.getDate()).isEqualTo(JOUR);
        assertThat(resume.getTotalEmployes()).isEqualTo(3);
        assertThat(resume.getPresents()).isEqualTo(1);
        assertThat(resume.getEnConge()).isEqualTo(1);
        assertThat(resume.getAbsents()).isEqualTo(1);
        assertThat(resume.getRetards()).isZero();
        assertThat(resume.getPresents() + resume.getAbsents()
                + resume.getRetards() + resume.getEnConge())
                .isEqualTo(resume.getTotalEmployes());
    }

    @Test
    void range_dateFin_avant_dateDebut_leve_exception() {
        assertThatThrownBy(() -> service.getPointagesRange(
                JOUR, JOUR.minusDays(1), null, null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dateFin");
    }

    // ---- Tolérance de retard (heure prévue 09:00, seuil strict 15 min) -----------------

    /** Construit un employé mono-affectation (site « Alpha », horaireDebut donné) + son pointage. */
    private DossierEmploye employeAvecHoraire(String horaireDebut) {
        return DossierEmploye.builder()
                .id("empR").agentId("9001").matricule("R001").nom("Sow").prenom("Test")
                .departement("Exploitation").siteAffecte("Alpha").poste("Agent")
                .statut(StatutDossierEmploye.ACTIF)
                .affectations(List.of(AffectationSite.builder()
                        .site("Alpha").horaireDebut(horaireDebut).horaireFin("17:00").build()))
                .build();
    }

    /** Re-stub les repos pour un seul employé + un pointage arrivant à l'heure donnée. */
    private void stubEmployeUniqueAvecArrivee(DossierEmploye emp, String site, String heureArrivee) {
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(emp));
        Pointage p = Pointage.builder().id("pr").codeSecret(emp.getAgentId())
                .site(new String[]{site}).date(JOUR).heureArrive(heureArrivee).build();
        when(pointageRepository.findAllByDate(JOUR)).thenReturn(List.of(p));
        when(demandeCongeRepository.findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(StatutDemande.APPROUVE), eq(JOUR), eq(JOUR))).thenReturn(List.of());
    }

    private PointageCentraliseDto ligneUnique() {
        return service.getPointages(JOUR, null, null, null, null, 0, 20).getContent().get(0);
    }

    @ParameterizedTest
    @CsvSource({
            "09:10, 10, PRESENT",
            "09:13, 13, PRESENT",
            "09:15, 15, PRESENT",   // seuil strict : 15 pile = PAS en retard
            "09:16, 16, RETARD",
            "09:30, 30, RETARD"
    })
    void retard_seuil_strict_15min(String arrivee, int retardAttendu, String statutAttendu) {
        stubEmployeUniqueAvecArrivee(employeAvecHoraire("09:00"), "Alpha", arrivee);

        PointageCentraliseDto ligne = ligneUnique();

        assertThat(ligne.getRetardMinutes()).isEqualTo(retardAttendu);
        assertThat(ligne.getStatut()).isEqualTo(statutAttendu);
    }

    @Test
    void resume_compte_uniquement_les_retards_stricts() {
        DossierEmploye enRetard = employeAvecHoraire("09:00");                 // arrive 09:16 -> RETARD
        DossierEmploye aLheure = DossierEmploye.builder()
                .id("empP").agentId("9002").matricule("P001").nom("Ba").prenom("Ok")
                .departement("Exploitation").siteAffecte("Alpha").poste("Agent")
                .statut(StatutDossierEmploye.ACTIF)
                .affectations(List.of(AffectationSite.builder()
                        .site("Alpha").horaireDebut("09:00").horaireFin("17:00").build()))
                .build();

        when(dossierEmployeRepository.findByStatutIn(any()))
                .thenReturn(List.of(enRetard, aLheure));
        Pointage pRetard = Pointage.builder().id("pR").codeSecret("9001")
                .site(new String[]{"Alpha"}).date(JOUR).heureArrive("09:16").build();
        Pointage pOk = Pointage.builder().id("pOk").codeSecret("9002")
                .site(new String[]{"Alpha"}).date(JOUR).heureArrive("09:10").build();
        when(pointageRepository.findAllByDate(JOUR)).thenReturn(List.of(pRetard, pOk));
        when(demandeCongeRepository.findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(StatutDemande.APPROUVE), eq(JOUR), eq(JOUR))).thenReturn(List.of());

        ResumeJourneeDto resume = service.getResume(JOUR);

        assertThat(resume.getTotalEmployes()).isEqualTo(2);
        assertThat(resume.getRetards()).isEqualTo(1);   // seul le +16 compte, pas le +10
        assertThat(resume.getPresents()).isEqualTo(1);
        assertThat(resume.getPresents() + resume.getAbsents()
                + resume.getRetards() + resume.getEnConge())
                .isEqualTo(resume.getTotalEmployes());
    }

    @Test
    void absent_pas_de_retard() {
        when(dossierEmployeRepository.findByStatutIn(any()))
                .thenReturn(List.of(employeAvecHoraire("09:00")));
        when(pointageRepository.findAllByDate(JOUR)).thenReturn(List.of());   // n'a pas pointé
        when(demandeCongeRepository.findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(StatutDemande.APPROUVE), eq(JOUR), eq(JOUR))).thenReturn(List.of());

        PointageCentraliseDto ligne = ligneUnique();

        assertThat(ligne.getStatut()).isEqualTo("ABSENT");
        assertThat(ligne.getRetardMinutes()).isZero();
    }

    @Test
    void sans_affectation_aucun_retard_meme_en_retard() {
        DossierEmploye sansAffectation = DossierEmploye.builder()
                .id("empR").agentId("9001").matricule("R001").nom("Sow").prenom("Test")
                .departement("Exploitation").siteAffecte("Alpha").poste("Agent")
                .statut(StatutDossierEmploye.ACTIF).build();   // pas d'affectations
        stubEmployeUniqueAvecArrivee(sansAffectation, "Alpha", "10:30");

        PointageCentraliseDto ligne = ligneUnique();

        assertThat(ligne.getRetardMinutes()).isZero();
        assertThat(ligne.getStatut()).isEqualTo("PRESENT");
    }

    @Test
    void site_pointe_different_du_site_affecte_aucun_retard() {
        // Affectation « Alpha » 09:00, mais l'agent pointe sur « Beta » -> pas de match -> pas de retard.
        stubEmployeUniqueAvecArrivee(employeAvecHoraire("09:00"), "Beta", "10:30");

        PointageCentraliseDto ligne = ligneUnique();

        assertThat(ligne.getRetardMinutes()).isZero();
        assertThat(ligne.getStatut()).isEqualTo("PRESENT");
    }

    @Test
    void horaire_debut_null_aucun_retard() {
        stubEmployeUniqueAvecArrivee(employeAvecHoraire(null), "Alpha", "10:30");

        PointageCentraliseDto ligne = ligneUnique();

        assertThat(ligne.getRetardMinutes()).isZero();
        assertThat(ligne.getStatut()).isEqualTo("PRESENT");
    }

    // ---- Multi-sites : une ligne par pointage, retard propre à chaque site ---------------

    /** Employé bi-affecté : Site A (06:00) + Site B (10:00). */
    private DossierEmploye employeBiSite() {
        return DossierEmploye.builder()
                .id("empMS").agentId("9100").matricule("MS01").nom("Kane").prenom("Multi")
                .departement("Exploitation").siteAffecte("A - B").poste("Agent")
                .statut(StatutDossierEmploye.ACTIF)
                .affectations(List.of(
                        AffectationSite.builder().site("A").horaireDebut("06:00").horaireFin("14:00").build(),
                        AffectationSite.builder().site("B").horaireDebut("10:00").horaireFin("18:00").build()))
                .build();
    }

    @Test
    void multi_pointages_meme_jour_produisent_une_ligne_par_pointage() {
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(employeBiSite()));
        // 4 pointages du même agent le même jour (table de test multi-sites)
        Pointage p1 = Pointage.builder().id("p1").codeSecret("9100")
                .site(new String[]{"A"}).date(JOUR).heureArrive("06:12").build(); // +12 -> PRESENT
        Pointage p2 = Pointage.builder().id("p2").codeSecret("9100")
                .site(new String[]{"B"}).date(JOUR).heureArrive("10:20").build(); // +20 -> RETARD
        Pointage p3 = Pointage.builder().id("p3").codeSecret("9100")
                .site(new String[]{"A"}).date(JOUR).heureArrive("06:40").build(); // +40 -> RETARD
        Pointage p4 = Pointage.builder().id("p4").codeSecret("9100")
                .site(new String[]{"C"}).date(JOUR).heureArrive("08:00").build(); // site sans horaire -> 0
        when(pointageRepository.findAllByDate(JOUR)).thenReturn(List.of(p1, p2, p3, p4));
        when(demandeCongeRepository.findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(StatutDemande.APPROUVE), eq(JOUR), eq(JOUR))).thenReturn(List.of());

        Map<String, PointageCentraliseDto> parId = service
                .getPointages(JOUR, null, null, null, null, 0, 20).getContent().stream()
                .collect(Collectors.toMap(PointageCentraliseDto::getId, d -> d));

        assertThat(parId).hasSize(4);
        assertThat(parId.get("p1").getRetardMinutes()).isEqualTo(12);
        assertThat(parId.get("p1").getStatut()).isEqualTo("PRESENT");
        assertThat(parId.get("p2").getRetardMinutes()).isEqualTo(20);
        assertThat(parId.get("p2").getStatut()).isEqualTo("RETARD");
        assertThat(parId.get("p3").getRetardMinutes()).isEqualTo(40);
        assertThat(parId.get("p3").getStatut()).isEqualTo("RETARD");
        assertThat(parId.get("p4").getRetardMinutes()).isZero();
        assertThat(parId.get("p4").getStatut()).isEqualTo("PRESENT");

        // resume : comptage par ligne -> 2 retards pour 1 seul employé
        ResumeJourneeDto resume = service.getResume(JOUR);
        assertThat(resume.getTotalEmployes()).isEqualTo(1);
        assertThat(resume.getRetards()).isEqualTo(2);
        assertThat(resume.getPresents()).isEqualTo(2);
        assertThat(resume.getAbsents()).isZero();
        assertThat(resume.getEnConge()).isZero();
    }

    @Test
    void pointage_multi_sites_dans_un_record_prend_horaire_le_plus_tot() {
        // Un seul enregistrement portant 2 sites -> fallback : horaire le plus tôt (06:00).
        // Arrivée 06:20 -> retard 20 (si 10:00 avait été retenu, l'arrivée serait "en avance" -> 0).
        DossierEmploye emp = employeBiSite();
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(emp));
        Pointage p = Pointage.builder().id("pMS").codeSecret("9100")
                .site(new String[]{"A", "B"}).date(JOUR).heureArrive("06:20").build();
        when(pointageRepository.findAllByDate(JOUR)).thenReturn(List.of(p));
        when(demandeCongeRepository.findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                eq(StatutDemande.APPROUVE), eq(JOUR), eq(JOUR))).thenReturn(List.of());

        PointageCentraliseDto ligne = ligneUnique();

        assertThat(ligne.getRetardMinutes()).isEqualTo(20);
        assertThat(ligne.getStatut()).isEqualTo("RETARD");
    }
}
