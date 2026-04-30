package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.PeriodeEssaiDto;
import com.example.Pointage_Cleanic.Dto.ProlongerPeriodeEssaiRequest;
import com.example.Pointage_Cleanic.Enum.StatutContrat;
import com.example.Pointage_Cleanic.Enum.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Enum.StatutPeriodeEssai;
import com.example.Pointage_Cleanic.Enum.TypeContratRh;
import com.example.Pointage_Cleanic.Mapper.PeriodeEssaiMapper;
import com.example.Pointage_Cleanic.Mapper.PeriodeEssaiMapperImpl;
import com.example.Pointage_Cleanic.entities.AlertePeriodeEssai;
import com.example.Pointage_Cleanic.entities.Contrat;
import com.example.Pointage_Cleanic.entities.DossierEmploye;
import com.example.Pointage_Cleanic.entities.PeriodeEssai;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.ContratRepository;
import com.example.Pointage_Cleanic.repositories.PeriodeEssaiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeriodeEssaiServiceTest {

    @Mock
    private PeriodeEssaiRepository repository;

    @Mock
    private ContratRepository contratRepository;

    @Spy
    private PeriodeEssaiMapper mapper = new PeriodeEssaiMapperImpl();

    @InjectMocks
    private PeriodeEssaiService service;

    private PeriodeEssai existing;

    @BeforeEach
    void setUp() {
        existing = PeriodeEssai.builder()
                .id("p1")
                .employeId("emp1")
                .contratId("c1")
                .typeContrat(TypeContratRh.CDI)
                .dateDebut(LocalDate.of(2026, 1, 1))
                .dateFin(LocalDate.of(2026, 4, 1))
                .dureeJours(90)
                .statut(StatutPeriodeEssai.EN_COURS)
                .alertes(new ArrayList<>())
                .decisions(new ArrayList<>())
                .build();
    }

    @Test
    void seedFromContrat_creates_periode_with_alertes_par_defaut() {
        Contrat contrat = Contrat.builder()
                .id("c1")
                .employeId("emp1")
                .employeNom("Diop")
                .employePrenom("Mamadou")
                .typeContrat(TypeContratRh.CDI)
                .dateDebut(LocalDate.of(2026, 1, 1))
                .statut(StatutContrat.ACTIF)
                .build();

        when(repository.save(any(PeriodeEssai.class))).thenAnswer(inv -> {
            PeriodeEssai p = inv.getArgument(0);
            if (p.getId() == null) p.setId("generated-id");
            return p;
        });

        PeriodeEssai saved = service.seedFromContrat(contrat, 3);

        assertThat(saved.getStatut()).isEqualTo(StatutPeriodeEssai.EN_COURS);
        assertThat(saved.getDateDebut()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(saved.getDateFin()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(saved.getDureeJours()).isEqualTo(90);  // 31+28+31 = 90 sur jan/fev/mars 2026
        assertThat(saved.getEmployeNom()).isEqualTo("Diop");
        assertThat(saved.getAlertes()).hasSize(3);
        assertThat(saved.getAlertes())
                .extracting(AlertePeriodeEssai::getJoursAvant)
                .containsExactly(30, 15, 7);
        assertThat(saved.getAlertes())
                .extracting(AlertePeriodeEssai::isEnvoyee)
                .containsOnly(false);
    }

    @Test
    void seedFromContrat_idempotent_when_periode_active_existe() {
        Contrat contrat = Contrat.builder()
                .id("c2").employeId("emp1")
                .typeContrat(TypeContratRh.CDD)
                .dateDebut(LocalDate.of(2026, 1, 1))
                .statut(StatutContrat.ACTIF)
                .build();
        when(repository.findFirstByEmployeIdAndStatutIn(eq("emp1"), anyList()))
                .thenReturn(Optional.of(existing));

        PeriodeEssai result = service.seedFromContrat(contrat, 3);

        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void seedFromContrat_rejects_null_dateDebut() {
        Contrat contrat = Contrat.builder().id("c1").employeId("emp1").build();

        assertThatThrownBy(() -> service.seedFromContrat(contrat, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date de début");
    }

    @Test
    void seedFromContrat_rejects_zero_dureeMois() {
        Contrat contrat = Contrat.builder()
                .id("c1").employeId("emp1")
                .dateDebut(LocalDate.now())
                .build();

        assertThatThrownBy(() -> service.seedFromContrat(contrat, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dureeEssaiMois");
    }

    // =========================================================================
    //  seedFromDossier
    // =========================================================================

    @Test
    void seedFromDossier_creates_periode_when_employe_en_periode_essai() {
        DossierEmploye dossier = dossierEnPeriodeEssai("emp1", LocalDate.of(2026, 1, 15), 3);
        when(contratRepository.findByEmployeId("emp1")).thenReturn(Collections.emptyList());
        when(repository.save(any(PeriodeEssai.class))).thenAnswer(inv -> {
            PeriodeEssai p = inv.getArgument(0);
            if (p.getId() == null) p.setId("generated-id");
            return p;
        });

        PeriodeEssaiService.SeedResult result = service.seedFromDossier(dossier);

        assertThat(result.created()).isTrue();
        assertThat(result.periode().getStatut()).isEqualTo(StatutPeriodeEssai.EN_COURS);
        assertThat(result.periode().getDateDebut()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(result.periode().getDateFin()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(result.periode().getEmployeId()).isEqualTo("emp1");
        assertThat(result.periode().getContratId()).isEqualTo("");
        assertThat(result.periode().getTypeContrat()).isNull();
        assertThat(result.periode().getAlertes()).hasSize(3);
        assertThat(result.periode().getAlertes())
                .extracting(AlertePeriodeEssai::getJoursAvant)
                .containsExactly(30, 15, 7);
    }

    @Test
    void seedFromDossier_calculates_dateFin_calendrier() {
        // 3 mois calendaires depuis 15 janvier = 15 avril (différent de +90j fixes
        // qui donnerait 14 avril ou 16 avril selon les mois traversés).
        DossierEmploye dossier = dossierEnPeriodeEssai("emp1", LocalDate.of(2026, 1, 15), 3);
        when(contratRepository.findByEmployeId("emp1")).thenReturn(Collections.emptyList());
        when(repository.save(any(PeriodeEssai.class))).thenAnswer(inv -> inv.getArgument(0));

        PeriodeEssaiService.SeedResult result = service.seedFromDossier(dossier);

        assertThat(result.periode().getDateFin()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(result.periode().getDureeJours()).isEqualTo(90);  // jan(31-15)+fev(28)+mars(31)+avr(15) = 90
    }

    @Test
    void seedFromDossier_idempotent_when_periode_active_exists() {
        DossierEmploye dossier = dossierEnPeriodeEssai("emp1", LocalDate.of(2026, 1, 15), 3);
        when(repository.findFirstByEmployeIdAndStatutIn(eq("emp1"), anyList()))
                .thenReturn(Optional.of(existing));

        PeriodeEssaiService.SeedResult result = service.seedFromDossier(dossier);

        assertThat(result.created()).isFalse();
        assertThat(result.periode()).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void seedFromDossier_returns_skipped_when_statut_not_periode_essai() {
        DossierEmploye dossier = DossierEmploye.builder()
                .id("emp1").statut(StatutDossierEmploye.ACTIF)
                .dateEntree(LocalDate.of(2026, 1, 15)).dureeEssaiMois(3)
                .build();

        PeriodeEssaiService.SeedResult result = service.seedFromDossier(dossier);

        assertThat(result.created()).isFalse();
        assertThat(result.periode()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void seedFromDossier_returns_skipped_when_dureeEssaiMois_invalide() {
        DossierEmploye nullDuree = dossierEnPeriodeEssai("emp1", LocalDate.now(), null);
        DossierEmploye zeroDuree = dossierEnPeriodeEssai("emp1", LocalDate.now(), 0);

        assertThat(service.seedFromDossier(nullDuree).created()).isFalse();
        assertThat(service.seedFromDossier(zeroDuree).created()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void seedFromDossier_returns_skipped_when_dateEntree_null() {
        DossierEmploye dossier = dossierEnPeriodeEssai("emp1", null, 3);

        PeriodeEssaiService.SeedResult result = service.seedFromDossier(dossier);

        assertThat(result.created()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void seedFromDossier_uses_active_contrat_metadata() {
        DossierEmploye dossier = dossierEnPeriodeEssai("emp1", LocalDate.of(2026, 1, 15), 3);
        Contrat contratActif = Contrat.builder()
                .id("contrat-actif").employeId("emp1")
                .typeContrat(TypeContratRh.CDD).statut(StatutContrat.ACTIF)
                .build();
        Contrat contratResilie = Contrat.builder()
                .id("vieux").employeId("emp1")
                .typeContrat(TypeContratRh.STAGE).statut(StatutContrat.RESILIE)
                .build();
        when(contratRepository.findByEmployeId("emp1"))
                .thenReturn(List.of(contratResilie, contratActif));
        when(repository.save(any(PeriodeEssai.class))).thenAnswer(inv -> inv.getArgument(0));

        PeriodeEssaiService.SeedResult result = service.seedFromDossier(dossier);

        assertThat(result.periode().getContratId()).isEqualTo("contrat-actif");
        assertThat(result.periode().getTypeContrat()).isEqualTo(TypeContratRh.CDD);
    }

    // =========================================================================
    //  applyTransitionStatutForEmploye
    // =========================================================================

    @Test
    void applyTransitionStatutForEmploye_actif_marks_titularise() {
        when(repository.findFirstByEmployeIdAndStatutIn(eq("emp1"), anyList()))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(PeriodeEssai.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<PeriodeEssai> result = service.applyTransitionStatutForEmploye(
                "emp1", StatutPeriodeEssai.TITULARISE, "rh@cleanic.com", "auto");

        assertThat(result).isPresent();
        assertThat(result.get().getStatut()).isEqualTo(StatutPeriodeEssai.TITULARISE);
        assertThat(result.get().getDecisions()).hasSize(1);
        assertThat(result.get().getDecisions().get(0).getDecision())
                .isEqualTo(StatutPeriodeEssai.TITULARISE);
        assertThat(result.get().getDecisions().get(0).getDecideurNom()).isEqualTo("rh@cleanic.com");
    }

    @Test
    void applyTransitionStatutForEmploye_sorti_marks_non_renouvelle() {
        when(repository.findFirstByEmployeIdAndStatutIn(eq("emp1"), anyList()))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(PeriodeEssai.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<PeriodeEssai> result = service.applyTransitionStatutForEmploye(
                "emp1", StatutPeriodeEssai.NON_RENOUVELE, "rh", "fin");

        assertThat(result).isPresent();
        assertThat(result.get().getStatut()).isEqualTo(StatutPeriodeEssai.NON_RENOUVELE);
        assertThat(result.get().getDecisions()).hasSize(1);
        assertThat(result.get().getDecisions().get(0).getDecision())
                .isEqualTo(StatutPeriodeEssai.NON_RENOUVELE);
    }

    @Test
    void applyTransitionStatutForEmploye_returns_empty_when_no_active_period() {
        when(repository.findFirstByEmployeIdAndStatutIn(eq("emp1"), anyList()))
                .thenReturn(Optional.empty());

        Optional<PeriodeEssai> result = service.applyTransitionStatutForEmploye(
                "emp1", StatutPeriodeEssai.TITULARISE, "rh", "x");

        assertThat(result).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void applyTransitionStatutForEmploye_idempotent_when_already_targeted() {
        existing.setStatut(StatutPeriodeEssai.TITULARISE);
        when(repository.findFirstByEmployeIdAndStatutIn(eq("emp1"), anyList()))
                .thenReturn(Optional.of(existing));

        Optional<PeriodeEssai> result = service.applyTransitionStatutForEmploye(
                "emp1", StatutPeriodeEssai.TITULARISE, "rh", "x");

        assertThat(result).isPresent();
        verify(repository, never()).save(any());
    }

    // =========================================================================
    //  prolonger / getById / getAlertes
    // =========================================================================

    @Test
    void prolonger_ok_appends_decision_and_recalcule_alertes() {
        when(repository.findById("p1")).thenReturn(Optional.of(existing));
        when(repository.save(any(PeriodeEssai.class))).thenAnswer(inv -> inv.getArgument(0));

        ProlongerPeriodeEssaiRequest request =
                new ProlongerPeriodeEssaiRequest(LocalDate.of(2026, 6, 1), "Besoin de plus de temps");

        PeriodeEssaiDto result = service.prolonger("p1", request, "rh@cleanic.com");

        assertThat(result.getStatut()).isEqualTo(StatutPeriodeEssai.PROLONGE);
        assertThat(result.getDateFin()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(result.getDecisions()).hasSize(1);
        assertThat(result.getDecisions().get(0).getDecision()).isEqualTo(StatutPeriodeEssai.PROLONGE);
        assertThat(result.getDecisions().get(0).getDecideurNom()).isEqualTo("rh@cleanic.com");
        assertThat(result.getAlertes()).hasSize(3);
    }

    @Test
    void prolonger_rejects_dateFin_before_or_equal_current() {
        when(repository.findById("p1")).thenReturn(Optional.of(existing));

        ProlongerPeriodeEssaiRequest request =
                new ProlongerPeriodeEssaiRequest(LocalDate.of(2026, 4, 1), "trop court");

        assertThatThrownBy(() -> service.prolonger("p1", request, "rh"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictement postérieure");
    }

    @Test
    void prolonger_rejects_when_already_titularise() {
        existing.setStatut(StatutPeriodeEssai.TITULARISE);
        when(repository.findById("p1")).thenReturn(Optional.of(existing));

        ProlongerPeriodeEssaiRequest request =
                new ProlongerPeriodeEssaiRequest(LocalDate.of(2026, 6, 1), "x");

        assertThatThrownBy(() -> service.prolonger("p1", request, "rh"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TITULARISE");
    }

    @Test
    void getById_throws_when_not_found() {
        when(repository.findById("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("inconnu"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAlertes_returns_periodes_with_dateFin_within_30_days() {
        LocalDate today = LocalDate.now();
        PeriodeEssai dans15j = PeriodeEssai.builder()
                .id("p1").employeId("e1").statut(StatutPeriodeEssai.EN_COURS)
                .dateFin(today.plusDays(15))
                .alertes(new ArrayList<>()).decisions(new ArrayList<>())
                .build();
        PeriodeEssai dans25j = PeriodeEssai.builder()
                .id("p2").employeId("e2").statut(StatutPeriodeEssai.PROLONGE)
                .dateFin(today.plusDays(25))
                .alertes(new ArrayList<>()).decisions(new ArrayList<>())
                .build();
        when(repository.findByStatutInAndDateFinLessThanEqualOrderByDateFinAsc(
                anyList(), any(LocalDate.class)))
                .thenReturn(List.of(dans15j, dans25j));

        List<PeriodeEssaiDto> alertes = service.getAlertes();

        assertThat(alertes).hasSize(2);
        assertThat(alertes).extracting(PeriodeEssaiDto::getId).containsExactly("p1", "p2");

        ArgumentCaptor<LocalDate> seuilCaptor = ArgumentCaptor.forClass(LocalDate.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StatutPeriodeEssai>> statutsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(repository).findByStatutInAndDateFinLessThanEqualOrderByDateFinAsc(
                statutsCaptor.capture(), seuilCaptor.capture());
        assertThat(statutsCaptor.getValue())
                .containsExactlyInAnyOrder(StatutPeriodeEssai.EN_COURS, StatutPeriodeEssai.PROLONGE);
        assertThat(seuilCaptor.getValue()).isEqualTo(today.plusDays(30));
    }

    // =========================================================================
    //  applyTitularisation (workflow officiel)
    // =========================================================================

    @Test
    void applyTitularisation_idempotent_when_already_titularise() {
        existing.setStatut(StatutPeriodeEssai.TITULARISE);
        when(repository.findById("p1")).thenReturn(Optional.of(existing));

        PeriodeEssai result = service.applyTitularisation("p1", "rh", "ok");

        assertThat(result.getStatut()).isEqualTo(StatutPeriodeEssai.TITULARISE);
        verify(repository, times(0)).save(any());
    }

    @Test
    void applyTitularisation_appends_decision_and_saves() {
        when(repository.findById("p1")).thenReturn(Optional.of(existing));
        when(repository.save(any(PeriodeEssai.class))).thenAnswer(inv -> inv.getArgument(0));

        PeriodeEssai result = service.applyTitularisation("p1", "rh@cleanic.com", "feu vert");

        assertThat(result.getStatut()).isEqualTo(StatutPeriodeEssai.TITULARISE);
        assertThat(result.getDecisions()).hasSize(1);
        assertThat(result.getDecisions().get(0).getDecision()).isEqualTo(StatutPeriodeEssai.TITULARISE);

        ArgumentCaptor<PeriodeEssai> captor = ArgumentCaptor.forClass(PeriodeEssai.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutPeriodeEssai.TITULARISE);
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    private DossierEmploye dossierEnPeriodeEssai(String id, LocalDate dateEntree, Integer dureeMois) {
        return DossierEmploye.builder()
                .id(id).matricule("M-" + id).nom("Test").prenom("Employe")
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI)
                .dateEntree(dateEntree).dureeEssaiMois(dureeMois)
                .build();
    }
}