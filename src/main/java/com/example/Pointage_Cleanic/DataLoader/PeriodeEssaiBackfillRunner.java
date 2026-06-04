package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.services.rh.PeriodeEssaiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Crée au démarrage les PeriodeEssai manquantes pour les DossierEmploye
 * actuellement en statut EN_PERIODE_ESSAI mais sans période active en base.
 * Idempotent grâce à {@link PeriodeEssaiService#seedFromDossier} qui no-op si
 * une période EN_COURS ou PROLONGE existe déjà pour l'employé.
 */
@Slf4j
@Component
@Order(1100)
@RequiredArgsConstructor
public class PeriodeEssaiBackfillRunner implements CommandLineRunner {

    private final DossierEmployeRepository dossierEmployeRepository;
    private final PeriodeEssaiService periodeEssaiService;

    @Override
    public void run(String... args) {
        List<DossierEmploye> candidats = dossierEmployeRepository
                .findByStatutAndDureeEssaiMoisIsNotNull(StatutDossierEmploye.EN_PERIODE_ESSAI);
        if (candidats.isEmpty()) return;

        int created = 0;
        int skipped = 0;
        for (DossierEmploye dossier : candidats) {
            try {
                PeriodeEssaiService.SeedResult result = periodeEssaiService.seedFromDossier(dossier);
                if (result.created()) {
                    created++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException ex) {
                log.warn("Backfill PeriodeEssai : échec pour employé {} : {}",
                        dossier.getId(), ex.getMessage());
            }
        }
        if (created > 0) {
            log.info("Backfill PeriodeEssai : {} période(s) créée(s), {} déjà existante(s) sur {} candidat(s)",
                    created, skipped, candidats.size());
        }
    }
}