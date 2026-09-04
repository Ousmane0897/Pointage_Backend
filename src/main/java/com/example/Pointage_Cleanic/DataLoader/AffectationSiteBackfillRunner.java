package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.util.AffectationSiteUtils;
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
        int propages = 0;
        for (DossierEmploye dossier : tous) {
            boolean modifie = false;

            // Passe 1 — dossiers sans aucune affectation : les dériver de siteAffecte.
            if (dossier.getAffectations() == null || dossier.getAffectations().isEmpty()) {
                if (dossier.getSiteAffecte() == null || dossier.getSiteAffecte().isBlank()) {
                    continue;
                }
                List<AffectationSite> affectations =
                        SiteAffecteUtils.affectationsDepuisSiteAffecte(dossier.getSiteAffecte());
                if (affectations.isEmpty()) {
                    continue;
                }
                dossier.setAffectations(affectations);
                backfilled++;
                modifie = true;
            }

            // Passe 2 — période et semaine ouvrée par site. Sans elle, les dossiers
            // existants n'auraient ni dateEntree ni joursTravail sur leurs affectations,
            // et le pointage centralisé retomberait sur son échelon le plus permissif
            // (aucun filtrage de semaine ouvrée) pour tout le parc.
            if (propagerPeriodeEtJours(dossier)) {
                propages++;
                modifie = true;
            }

            if (modifie) {
                dossierEmployeRepository.save(dossier);
            }
        }
        if (backfilled > 0 || propages > 0) {
            log.info("Backfill affectations : {} dossier(s) back-fillé(s), {} enrichi(s) "
                            + "(période/jours par site) sur {} au total",
                    backfilled, propages, tous.size());
        }
    }

    /**
     * Recopie sur chaque affectation les informations que le dossier ne portait
     * jusqu'ici qu'au niveau de l'employé : la semaine ouvrée et, à défaut de mieux,
     * la date d'embauche comme date d'arrivée sur le site. Pose également l'identité
     * de ligne ({@code id}) sur les affectations qui n'en ont pas.
     * <p>
     * <b>Idempotent et purement additif</b> : seuls les champs {@code null} sont
     * renseignés, une valeur saisie par les RH n'est jamais écrasée. {@code dateSortie}
     * reste nulle — « toujours en poste » est le bon défaut, et inventer une sortie
     * ferait disparaître les lignes de pointage du site.
     *
     * @return {@code true} si au moins une affectation a été modifiée.
     */
    private boolean propagerPeriodeEtJours(DossierEmploye dossier) {
        List<AffectationSite> affectations = dossier.getAffectations();
        if (affectations == null || affectations.isEmpty()) {
            return false;
        }
        // Identité stable des lignes : les dossiers antérieurs n'en ont pas, et la
        // liste étant remplacée en bloc à chaque écriture, aucune n'en recevrait
        // jamais sans cette passe. Purement additif, comme le reste de la méthode.
        boolean modifie = AffectationSiteUtils.assurerIds(affectations);
        for (AffectationSite affectation : affectations) {
            if (affectation == null) continue;
            if (affectation.getJoursTravail() == null && dossier.getJoursTravail() != null) {
                affectation.setJoursTravail(dossier.getJoursTravail());
                modifie = true;
            }
            // Une affectation ne peut pas précéder l'embauche : à défaut de date propre
            // au site, celle de l'entreprise est la borne basse la plus juste connue.
            if (affectation.getDateEntree() == null && dossier.getDateEmbauche() != null) {
                affectation.setDateEntree(dossier.getDateEmbauche());
                modifie = true;
            }
        }
        return modifie;
    }
}
