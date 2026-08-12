package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.util.SiteAffecteUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Back-fill au démarrage des {@code affectations} manquantes sur les
 * {@link DossierEmploye} historiques : pour tout dossier dont {@code affectations}
 * est null/vide mais dont {@code siteAffecte} est renseigné, on éclate
 * {@code siteAffecte} via le splitter tolérant {@link SiteAffecteUtils#decouper}
 * ({@code /}, {@code ,} ou {@code " - "} — « Sacré-Coeur » préservé) et on crée
 * une {@link AffectationSite} par site, horaires laissés à null (inconnus pour
 * l'historique).
 * <p>
 * <b>Idempotent</b> : ne touche que les dossiers sans {@code affectations}.
 * Purement additif — aucune donnée existante n'est détruite, {@code siteAffecte}
 * reste intact et fait toujours foi pour les consommateurs.
 */
@Slf4j
@Component
@Order(1200)
@RequiredArgsConstructor
public class AffectationSiteBackfillRunner implements CommandLineRunner {

    private final DossierEmployeRepository dossierEmployeRepository;

    @Override
    public void run(String... args) {
        List<DossierEmploye> tous = dossierEmployeRepository.findAll();
        if (tous.isEmpty()) return;

        int backfilled = 0;
        for (DossierEmploye dossier : tous) {
            if (dossier.getAffectations() != null && !dossier.getAffectations().isEmpty()) {
                continue;
            }
            if (dossier.getSiteAffecte() == null || dossier.getSiteAffecte().isBlank()) {
                continue;
            }
            List<AffectationSite> affectations =
                    SiteAffecteUtils.affectationsDepuisSiteAffecte(dossier.getSiteAffecte());
            if (affectations.isEmpty()) {
                continue;
            }
            dossier.setAffectations(affectations);
            dossierEmployeRepository.save(dossier);
            backfilled++;
        }
        if (backfilled > 0) {
            log.info("Backfill affectations : {} dossier(s) back-fillé(s) sur {} au total",
                    backfilled, tous.size());
        }
    }
}
