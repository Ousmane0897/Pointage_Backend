package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.CreerDemandeValidationRequest;
import com.example.Pointage_Cleanic.Dto.DemandeValidationDto;
import com.example.Pointage_Cleanic.Dto.ValiderDemandeRequest;
import com.example.Pointage_Cleanic.Enum.ActionValidation;
import com.example.Pointage_Cleanic.Enum.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Enum.StatutPeriodeEssai;
import com.example.Pointage_Cleanic.Enum.StatutValidation;
import com.example.Pointage_Cleanic.Mapper.DemandeValidationMapper;
import com.example.Pointage_Cleanic.Mapper.DemandeValidationMapperImpl;
import com.example.Pointage_Cleanic.entities.DemandeValidationPeriodeEssai;
import com.example.Pointage_Cleanic.entities.DossierEmploye;
import com.example.Pointage_Cleanic.entities.PeriodeEssai;
import com.example.Pointage_Cleanic.exception.DemandeValidationConflictException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.DemandeValidationPeriodeEssaiRepository;
import com.example.Pointage_Cleanic.repositories.DossierEmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandeValidationPeriodeEssaiServiceTest {

    @Mock
    private DemandeValidationPeriodeEssaiRepository repository;

    @Spy
    private DemandeValidationMapper mapper = new DemandeValidationMapperImpl();

    @Mock
    private PeriodeEssaiService periodeEssaiService;

    @Mock
    private DossierEmployeRepository dossierEmployeRepository;

    @InjectMocks
    private DemandeValidationPeriodeEssaiService service;

    private PeriodeEssai periode;
    private DemandeValidationPeriodeEssai demande;

    @BeforeEach
    void setUp() {
        periode = PeriodeEssai.builder()
                .id("p1")
                .employeId("emp1")
                .employeNom("Diop")
                .employePrenom("Mamadou")
                .statut(StatutPeriodeEssai.EN_COURS)
                .build();

        demande = DemandeValidationPeriodeEssai.builder()
                .id("d1")
                .periodeEssaiId("p1")
                .employeId("emp1")
                .statut(StatutValidation.EN_ATTENTE_MANAGER)
                .build();
    }

    // ---- Création ----

    @Test
    void creer_demande_ok() {
        when(periodeEssaiService.requireById("p1")).thenReturn(periode);
        when(repository.findByPeriodeEssaiIdAndStatutNotIn(eq("p1"), any()))
                .thenReturn(Collections.emptyList());
        when(repository.save(any(DemandeValidationPeriodeEssai.class)))
                .thenAnswer(inv -> {
                    DemandeValidationPeriodeEssai d = inv.getArgument(0);
                    d.setId("new-id");
                    return d;
                });

        DemandeValidationDto created = service.creer("p1", new CreerDemandeValidationRequest("avis manager"));

        assertThat(created.getStatut()).isEqualTo(StatutValidation.EN_ATTENTE_MANAGER);
        assertThat(created.getCommentaireManager()).isEqualTo("avis manager");
        assertThat(created.getEmployeId()).isEqualTo("emp1");
        assertThat(created.getDateCreation()).isNotNull();
    }

    @Test
    void creer_demande_rejected_when_periode_titularise() {
        periode.setStatut(StatutPeriodeEssai.TITULARISE);
        when(periodeEssaiService.requireById("p1")).thenReturn(periode);

        assertThatThrownBy(() -> service.creer("p1", new CreerDemandeValidationRequest("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TITULARISE");
    }

    @Test
    void creer_demande_409_when_demande_active_existe() {
        when(periodeEssaiService.requireById("p1")).thenReturn(periode);
        when(repository.findByPeriodeEssaiIdAndStatutNotIn(eq("p1"), any()))
                .thenReturn(List.of(demande));

        assertThatThrownBy(() -> service.creer("p1", new CreerDemandeValidationRequest("x")))
                .isInstanceOf(DemandeValidationConflictException.class);

        verify(repository, never()).save(any());
    }

    // ---- Workflow Manager → RH → CONFIRMEE ----

    @Test
    void valider_manager_passes_to_VALIDEE_MANAGER() {
        when(repository.findById("d1")).thenReturn(Optional.of(demande));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DemandeValidationDto result = service.traiter(
                "d1", new ValiderDemandeRequest(ActionValidation.VALIDER, "OK manager"), "manager@x");

        assertThat(result.getStatut()).isEqualTo(StatutValidation.VALIDEE_MANAGER);
        assertThat(result.getCommentaireManager()).isEqualTo("OK manager");
    }

    @Test
    void valider_rh_passes_to_VALIDEE_RH() {
        demande.setStatut(StatutValidation.VALIDEE_MANAGER);
        when(repository.findById("d1")).thenReturn(Optional.of(demande));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DemandeValidationDto result = service.traiter(
                "d1", new ValiderDemandeRequest(ActionValidation.VALIDER, "OK rh"), "rh@x");

        assertThat(result.getStatut()).isEqualTo(StatutValidation.VALIDEE_RH);
        assertThat(result.getCommentaireRh()).isEqualTo("OK rh");
    }

    @Test
    void confirmer_applies_titularisation_and_resets_dossier() {
        demande.setStatut(StatutValidation.VALIDEE_RH);
        when(repository.findById("d1")).thenReturn(Optional.of(demande));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DossierEmploye dossier = DossierEmploye.builder()
                .id("emp1")
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI)
                .dureeEssaiMois(3)
                .build();
        when(dossierEmployeRepository.findById("emp1")).thenReturn(Optional.of(dossier));

        DemandeValidationDto result = service.traiter(
                "d1", new ValiderDemandeRequest(ActionValidation.CONFIRMER, "feu vert"), "directeur@x");

        assertThat(result.getStatut()).isEqualTo(StatutValidation.CONFIRMEE);
        assertThat(result.getDateFinalisation()).isNotNull();

        verify(periodeEssaiService).applyTitularisation(eq("p1"), eq("directeur@x"), eq("feu vert"));

        ArgumentCaptor<DossierEmploye> dossierCaptor = ArgumentCaptor.forClass(DossierEmploye.class);
        verify(dossierEmployeRepository).save(dossierCaptor.capture());
        DossierEmploye saved = dossierCaptor.getValue();
        assertThat(saved.getStatut()).isEqualTo(StatutDossierEmploye.ACTIF);
        assertThat(saved.getDureeEssaiMois()).isNull();
    }

    @Test
    void confirmer_illegal_from_EN_ATTENTE_MANAGER() {
        when(repository.findById("d1")).thenReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.traiter(
                "d1", new ValiderDemandeRequest(ActionValidation.CONFIRMER, "x"), "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONFIRMER illégale");
    }

    @Test
    void refuser_at_manager_step_writes_to_commentaireManager() {
        when(repository.findById("d1")).thenReturn(Optional.of(demande));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DemandeValidationDto result = service.traiter(
                "d1", new ValiderDemandeRequest(ActionValidation.REFUSER, "non concluant"), "manager@x");

        assertThat(result.getStatut()).isEqualTo(StatutValidation.REFUSEE);
        assertThat(result.getCommentaireManager()).isEqualTo("non concluant");
        assertThat(result.getCommentaireRh()).isNull();
        assertThat(result.getDateFinalisation()).isNotNull();
    }

    @Test
    void refuser_at_rh_step_writes_to_commentaireRh() {
        demande.setStatut(StatutValidation.VALIDEE_MANAGER);
        when(repository.findById("d1")).thenReturn(Optional.of(demande));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DemandeValidationDto result = service.traiter(
                "d1", new ValiderDemandeRequest(ActionValidation.REFUSER, "non titularisable"), "rh@x");

        assertThat(result.getStatut()).isEqualTo(StatutValidation.REFUSEE);
        assertThat(result.getCommentaireRh()).isEqualTo("non titularisable");
    }

    @Test
    void traiter_already_finalized_throws_400() {
        demande.setStatut(StatutValidation.CONFIRMEE);
        when(repository.findById("d1")).thenReturn(Optional.of(demande));

        assertThatThrownBy(() -> service.traiter(
                "d1", new ValiderDemandeRequest(ActionValidation.VALIDER, "x"), "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("déjà finalisée");
    }

    @Test
    void traiter_not_found() {
        when(repository.findById("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.traiter(
                "inconnu", new ValiderDemandeRequest(ActionValidation.VALIDER, "x"), "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_filtre_par_statut() {
        when(repository.findByStatut(StatutValidation.EN_ATTENTE_MANAGER))
                .thenReturn(List.of(demande));

        List<DemandeValidationDto> result = service.list("EN_ATTENTE_MANAGER");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatut()).isEqualTo(StatutValidation.EN_ATTENTE_MANAGER);
    }

    @Test
    void list_invalid_statut_throws_400() {
        assertThatThrownBy(() -> service.list("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }
}