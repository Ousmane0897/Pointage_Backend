package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.ResumeJourneeDto;
import com.example.Pointage_Cleanic.Dto.rh.PointageCentraliseDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Enum.rh.TypeConge;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
                dossierEmployeRepository, pointageRepository, demandeCongeRepository);

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
}
