package com.example.Pointage_Cleanic.util;

import com.example.Pointage_Cleanic.entities.rh.AffectationSite;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sémantique d'une {@link AffectationSite} : est-elle close, laquelle est encore
 * active, quelle est son identité. Point unique de vérité côté serveur, miroir des
 * helpers exportés à côté du type {@code AffectationSite} du front
 * ({@code src/app/models/dossier-employe.model.ts}).
 * <p>
 * Classe statique pure, sans dépendance Spring ni accès base, sur le modèle de
 * {@link SiteAffecteUtils} — c'est ce qui la rend testable sans contexte.
 * <p>
 * <b>« Aujourd'hui » est toujours un paramètre</b>, jamais {@code LocalDate.now()} :
 * une date implicite rendrait les résultats dépendants du jour d'exécution, donc les
 * tests intestables (le piège rencontré sur la suite du pointage centralisé). Les
 * appelants passent la date tirée du bean {@code Clock} d'{@code Africa/Dakar}.
 */
public final class AffectationSiteUtils {

    private AffectationSiteUtils() {
    }

    /**
     * Affectation close : l'employé a quitté ce site.
     * <p>
     * La sortie du jour même ne compte pas comme close — l'agent y travaille encore
     * aujourd'hui, et l'exclure ferait disparaître ses créneaux du jour.
     */
    public static boolean estTerminee(AffectationSite affectation, LocalDate aujourdHui) {
        if (affectation == null) {
            return false;
        }
        LocalDate sortie = affectation.getDateSortie();
        return sortie != null && sortie.isBefore(aujourdHui);
    }

    /** Affectations encore en cours (ou à venir), dans l'ordre reçu. */
    public static List<AffectationSite> actives(List<AffectationSite> affectations,
                                                LocalDate aujourdHui) {
        if (affectations == null) {
            return List.of();
        }
        return affectations.stream()
                .filter(Objects::nonNull)
                .filter(a -> !estTerminee(a, aujourdHui))
                .toList();
    }

    /** Affectations closes, dans l'ordre reçu. */
    public static List<AffectationSite> terminees(List<AffectationSite> affectations,
                                                  LocalDate aujourdHui) {
        if (affectations == null) {
            return List.of();
        }
        return affectations.stream()
                .filter(Objects::nonNull)
                .filter(a -> estTerminee(a, aujourdHui))
                .toList();
    }

    /**
     * Pose un identifiant sur les seules affectations qui n'en ont pas.
     *
     * @return {@code true} si au moins une ligne a été modifiée — permet aux appelants
     *         (backfill) de n'écrire en base que lorsqu'il y a matière, donc d'être
     *         idempotents.
     */
    public static boolean assurerIds(List<AffectationSite> affectations) {
        if (affectations == null || affectations.isEmpty()) {
            return false;
        }
        boolean modifie = false;
        for (AffectationSite affectation : affectations) {
            if (affectation == null) {
                continue;
            }
            if (affectation.getId() == null || affectation.getId().isBlank()) {
                affectation.setId(UUID.randomUUID().toString());
                modifie = true;
            }
        }
        return modifie;
    }

    /**
     * Clé naturelle d'une affectation : site (normalisé) + période.
     * <p>
     * C'est elle, et non l'{@code id}, qui sert à retrouver une affectation d'une
     * écriture à l'autre : un client qui ne renverrait pas les ids contournerait
     * autrement la garde anti-perte, et celle-ci doit valoir <b>dès le premier
     * déploiement</b>, avant que le backfill n'ait posé le moindre id.
     * <p>
     * Les horaires et la semaine ouvrée n'en font délibérément pas partie : les y
     * inclure rendrait le rapprochement sensible au moindre écart d'aller-retour
     * ({@code ""} vs {@code null}, {@code "6:00"} vs {@code "06:00"}) et bloquerait
     * l'enregistrement du dossier entier.
     */
    public static String signature(AffectationSite affectation) {
        return affectation == null
                ? signature(null, null, null)
                : signature(affectation.getSite(), affectation.getDateEntree(),
                        affectation.getDateSortie());
    }

    /**
     * Même clé, calculée sur les champs bruts — le DTO n'est pas encore converti en
     * entité au moment où la garde compare les deux listes.
     */
    public static String signature(String site, LocalDate dateEntree, LocalDate dateSortie) {
        String nom = site == null ? "" : site.trim().toLowerCase();
        return nom + "|" + dateEntree + "|" + dateSortie;
    }
}
