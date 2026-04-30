package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.DossierEmployeDto;
import com.example.Pointage_Cleanic.Dto.DossierEmployeStatutRequest;
import com.example.Pointage_Cleanic.Enum.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Enum.StatutPeriodeEssai;
import com.example.Pointage_Cleanic.Mapper.DossierEmployeMapper;
import com.example.Pointage_Cleanic.entities.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.DossierEmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests des hooks d'auto-création / auto-clôture de PeriodeEssai sur les
 * différents chemins de save de DossierEmployeService (create, update,
 * updateStatut, titulariser).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DossierEmployeServiceTest {

    @Mock DossierEmployeRepository repository;
    @Mock DossierEmployeMapper mapper;
    @Mock MongoTemplate mongoTemplate;
    @Mock PeriodeEssaiService periodeEssaiService;

    DossierEmployeService service;

    @BeforeEach
    void setUp() {
        service = new DossierEmployeService(repository, mapper, mongoTemplate, periodeEssaiService);

        // Mapper mock : copie les champs essentiels du DTO vers l'entité.
        when(mapper.toEntity(any(DossierEmployeDto.class))).thenAnswer(inv -> {
            DossierEmployeDto dto = inv.getArgument(0);
            return DossierEmploye.builder()
                    .matricule(dto.getMatricule())
                    .nom(dto.getNom())
                    .prenom(dto.getPrenom())
                    .poste(dto.getPoste())
                    .dateEntree(dto.getDateEntree())
                    .statut(dto.getStatut())
                    .dureeEssaiMois(dto.getDureeEssaiMois())
                    .build();
        });
        when(mapper.toDto(any(DossierEmploye.class))).thenAnswer(inv -> {
            DossierEmploye e = inv.getArgument(0);
            return DossierEmployeDto.builder()
                    .id(e.getId()).matricule(e.getMatricule())
                    .nom(e.getNom()).prenom(e.getPrenom())
                    .statut(e.getStatut()).dureeEssaiMois(e.getDureeEssaiMois())
                    .build();
        });

        // saveAll/save assignent un id par défaut.
        when(repository.save(any(DossierEmploye.class))).thenAnswer(inv -> {
            DossierEmploye d = inv.getArgument(0);
            if (d.getId() == null) d.setId("emp-" + d.getMatricule());
            return d;
        });

        when(repository.existsByMatricule(anyString())).thenReturn(false);

        // Les seeds/transitions retournent par défaut un résultat neutre.
        when(periodeEssaiService.seedFromDossier(any(DossierEmploye.class)))
                .thenReturn(PeriodeEssaiService.SeedResult.skipped());
        when(periodeEssaiService.applyTransitionStatutForEmploye(anyString(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void create_employe_en_periode_essai_triggers_seedFromDossier() throws Exception {
        DossierEmployeDto dto = enPeriodeEssaiDto("M1", LocalDate.of(2026, 1, 15), 3);

        service.create(dto, null);

        ArgumentCaptor<DossierEmploye> captor = ArgumentCaptor.forClass(DossierEmploye.class);
        verify(periodeEssaiService).seedFromDossier(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutDossierEmploye.EN_PERIODE_ESSAI);
        assertThat(captor.getValue().getDureeEssaiMois()).isEqualTo(3);
    }

    @Test
    void create_employe_actif_does_not_trigger_seed_creation() throws Exception {
        // seedFromDossier est tout de même appelé (no-op idempotent côté service),
        // mais il doit être skipped() pour un employé non-EN_PERIODE_ESSAI.
        // On vérifie surtout qu'aucune transition n'est demandée.
        DossierEmployeDto dto = DossierEmployeDto.builder()
                .matricule("M2").nom("X").prenom("Y").poste("Op")
                .dateEntree(LocalDate.of(2026, 1, 1))
                .statut(StatutDossierEmploye.ACTIF)
                .build();

        service.create(dto, null);

        verify(periodeEssaiService).seedFromDossier(any(DossierEmploye.class));
        verify(periodeEssaiService, never())
                .applyTransitionStatutForEmploye(anyString(), any(), any(), any());
    }

    @Test
    void update_employe_calls_synchroniserPeriodeEssai_with_seed_when_en_periode_essai() throws Exception {
        DossierEmploye existing = DossierEmploye.builder()
                .id("emp1").matricule("M1").nom("X").prenom("Y").poste("Op")
                .dateEntree(LocalDate.of(2026, 1, 15))
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI).dureeEssaiMois(3)
                .build();
        when(repository.findById("emp1")).thenReturn(Optional.of(existing));
        // updateEntityFromDto : ne change rien pour ce test (statut reste EN_PERIODE_ESSAI).

        DossierEmployeDto dto = enPeriodeEssaiDto("M1", LocalDate.of(2026, 1, 15), 3);
        service.update("emp1", dto, null);

        verify(periodeEssaiService).seedFromDossier(any(DossierEmploye.class));
        verify(periodeEssaiService, never())
                .applyTransitionStatutForEmploye(anyString(), any(), any(), any());
    }

    @Test
    void updateStatut_to_periode_essai_triggers_seed() {
        DossierEmploye existing = DossierEmploye.builder()
                .id("emp1").matricule("M1").nom("X").prenom("Y")
                .dateEntree(LocalDate.of(2026, 1, 15))
                .statut(StatutDossierEmploye.ACTIF)
                .build();
        when(repository.findById("emp1")).thenReturn(Optional.of(existing));

        DossierEmployeStatutRequest req = DossierEmployeStatutRequest.builder()
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI).dureeEssaiMois(6).build();
        service.updateStatut("emp1", req);

        verify(periodeEssaiService).seedFromDossier(any(DossierEmploye.class));
        verify(periodeEssaiService, never())
                .applyTransitionStatutForEmploye(anyString(), any(), any(), any());
    }

    @Test
    void updateStatut_from_periode_essai_to_actif_marks_titularise() {
        DossierEmploye existing = DossierEmploye.builder()
                .id("emp1").matricule("M1").nom("X").prenom("Y")
                .dateEntree(LocalDate.of(2026, 1, 15))
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI).dureeEssaiMois(3)
                .build();
        when(repository.findById("emp1")).thenReturn(Optional.of(existing));

        DossierEmployeStatutRequest req = DossierEmployeStatutRequest.builder()
                .statut(StatutDossierEmploye.ACTIF).build();
        service.updateStatut("emp1", req);

        verify(periodeEssaiService).applyTransitionStatutForEmploye(
                eq("emp1"), eq(StatutPeriodeEssai.TITULARISE), anyString(), anyString());
        verify(periodeEssaiService, never()).seedFromDossier(any(DossierEmploye.class));
    }

    @Test
    void updateStatut_from_periode_essai_to_sorti_marks_non_renouvelle() {
        DossierEmploye existing = DossierEmploye.builder()
                .id("emp1").matricule("M1").nom("X").prenom("Y")
                .dateEntree(LocalDate.of(2026, 1, 15))
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI).dureeEssaiMois(3)
                .build();
        when(repository.findById("emp1")).thenReturn(Optional.of(existing));

        DossierEmployeStatutRequest req = DossierEmployeStatutRequest.builder()
                .statut(StatutDossierEmploye.SORTI).build();
        service.updateStatut("emp1", req);

        verify(periodeEssaiService).applyTransitionStatutForEmploye(
                eq("emp1"), eq(StatutPeriodeEssai.NON_RENOUVELE), anyString(), anyString());
    }

    @Test
    void updateStatut_from_periode_essai_to_suspendu_marks_non_renouvelle() {
        DossierEmploye existing = DossierEmploye.builder()
                .id("emp1").matricule("M1").nom("X").prenom("Y")
                .dateEntree(LocalDate.of(2026, 1, 15))
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI).dureeEssaiMois(3)
                .build();
        when(repository.findById("emp1")).thenReturn(Optional.of(existing));

        DossierEmployeStatutRequest req = DossierEmployeStatutRequest.builder()
                .statut(StatutDossierEmploye.SUSPENDU).build();
        service.updateStatut("emp1", req);

        verify(periodeEssaiService).applyTransitionStatutForEmploye(
                eq("emp1"), eq(StatutPeriodeEssai.NON_RENOUVELE), anyString(), anyString());
    }

    @Test
    void titulariser_marks_periode_titularise() {
        DossierEmploye existing = DossierEmploye.builder()
                .id("emp1").matricule("M1").nom("X").prenom("Y")
                .dateEntree(LocalDate.of(2026, 1, 15))
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI).dureeEssaiMois(3)
                .build();
        when(repository.findById("emp1")).thenReturn(Optional.of(existing));

        service.titulariser("emp1");

        verify(periodeEssaiService).applyTransitionStatutForEmploye(
                eq("emp1"), eq(StatutPeriodeEssai.TITULARISE), anyString(), anyString());
    }

    @Test
    void titulariser_when_already_actif_does_nothing_on_periode() {
        // Cas dégénéré : titulariser appelé sur un employé déjà ACTIF (ancienStatut != EN_PERIODE_ESSAI).
        DossierEmploye existing = DossierEmploye.builder()
                .id("emp1").matricule("M1").nom("X").prenom("Y")
                .dateEntree(LocalDate.of(2026, 1, 15))
                .statut(StatutDossierEmploye.ACTIF)
                .build();
        when(repository.findById("emp1")).thenReturn(Optional.of(existing));

        service.titulariser("emp1");

        verify(periodeEssaiService, never())
                .applyTransitionStatutForEmploye(anyString(), any(), any(), any());
        verify(periodeEssaiService, never()).seedFromDossier(any(DossierEmploye.class));
    }

    private DossierEmployeDto enPeriodeEssaiDto(String matricule, LocalDate dateEntree, int dureeMois) {
        return DossierEmployeDto.builder()
                .matricule(matricule).nom("X").prenom("Y").poste("Op")
                .dateEntree(dateEntree)
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI)
                .dureeEssaiMois(dureeMois)
                .build();
    }
}