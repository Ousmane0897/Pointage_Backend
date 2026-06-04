package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.PeriodeEssai;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.services.rh.PeriodeEssaiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeriodeEssaiBackfillRunnerTest {

    @Mock DossierEmployeRepository dossierRepo;
    @Mock PeriodeEssaiService periodeEssaiService;

    @InjectMocks PeriodeEssaiBackfillRunner runner;

    @Test
    void run_no_candidates_does_nothing() {
        when(dossierRepo.findByStatutAndDureeEssaiMoisIsNotNull(StatutDossierEmploye.EN_PERIODE_ESSAI))
                .thenReturn(Collections.emptyList());

        runner.run();

        verify(periodeEssaiService, never()).seedFromDossier(any(DossierEmploye.class));
    }

    @Test
    void run_calls_seedFromDossier_once_per_candidate() {
        DossierEmploye d1 = DossierEmploye.builder()
                .id("e1").statut(StatutDossierEmploye.EN_PERIODE_ESSAI)
                .dateEntree(LocalDate.now()).dureeEssaiMois(3).build();
        DossierEmploye d2 = DossierEmploye.builder()
                .id("e2").statut(StatutDossierEmploye.EN_PERIODE_ESSAI)
                .dateEntree(LocalDate.now()).dureeEssaiMois(6).build();

        when(dossierRepo.findByStatutAndDureeEssaiMoisIsNotNull(StatutDossierEmploye.EN_PERIODE_ESSAI))
                .thenReturn(List.of(d1, d2));
        when(periodeEssaiService.seedFromDossier(any(DossierEmploye.class)))
                .thenReturn(new PeriodeEssaiService.SeedResult(new PeriodeEssai(), true));

        runner.run();

        verify(periodeEssaiService, times(1)).seedFromDossier(d1);
        verify(periodeEssaiService, times(1)).seedFromDossier(d2);
    }

    @Test
    void run_continues_after_individual_failure() {
        DossierEmploye d1 = DossierEmploye.builder()
                .id("e1").statut(StatutDossierEmploye.EN_PERIODE_ESSAI)
                .dateEntree(LocalDate.now()).dureeEssaiMois(3).build();
        DossierEmploye d2 = DossierEmploye.builder()
                .id("e2").statut(StatutDossierEmploye.EN_PERIODE_ESSAI)
                .dateEntree(LocalDate.now()).dureeEssaiMois(6).build();
        when(dossierRepo.findByStatutAndDureeEssaiMoisIsNotNull(StatutDossierEmploye.EN_PERIODE_ESSAI))
                .thenReturn(List.of(d1, d2));
        when(periodeEssaiService.seedFromDossier(d1))
                .thenThrow(new RuntimeException("simulated failure"));
        when(periodeEssaiService.seedFromDossier(d2))
                .thenReturn(new PeriodeEssaiService.SeedResult(new PeriodeEssai(), true));

        runner.run();

        verify(periodeEssaiService).seedFromDossier(d1);
        verify(periodeEssaiService).seedFromDossier(d2);
    }
}