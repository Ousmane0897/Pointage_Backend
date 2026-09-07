package com.example.Pointage_Cleanic.services.rh;

import java.util.Comparator;
import java.util.List;

/**
 * Snapshot <b>immuable</b> du barème de congés, tel qu'il vaut à l'instant d'un calcul.
 *
 * <p>Il existe pour découpler {@link CongeAcquisCalculator} — composant volontairement pur,
 * testable sans contexte Spring ni base — du document Mongo modifiable qui porte le barème.
 * Le calculateur ne lit jamais le paramétrage : il le <b>reçoit en argument</b>. C'est
 * aussi ce qui permet à {@code DemandeCongeService} de lire le barème <b>une seule fois par
 * méthode publique</b> et de le faire circuler, plutôt qu'une lecture Mongo par employé.
 *
 * @param joursAcquisParMois        acquis de base, par mois de service effectif
 * @param supplementEnfantsActif    active la majoration pour enfants à charge
 * @param joursParEnfant            jours accordés par enfant éligible
 * @param ageMaxEnfant              âge limite <b>exclu</b> (« moins de 14 ans » ⇒ 14)
 * @param reserverAuxMeres          restreint la majoration aux femmes
 * @param plafondJoursEnfants       plafond de la majoration enfants ; {@code null} ⇒ aucun
 * @param supplementAncienneteActif active la majoration d'ancienneté
 * @param paliersAnciennete         paliers <b>non cumulatifs</b> (le plus élevé atteint gagne)
 * @param proratiserSupplements     proratise les majorations sur les mois de service
 */
public record BaremeConges(
        int joursAcquisParMois,
        boolean supplementEnfantsActif,
        int joursParEnfant,
        int ageMaxEnfant,
        boolean reserverAuxMeres,
        Integer plafondJoursEnfants,
        boolean supplementAncienneteActif,
        List<Palier> paliersAnciennete,
        boolean proratiserSupplements
) {

    /** Palier d'ancienneté — cf. {@link com.example.Pointage_Cleanic.entities.rh.PalierAncienneteConge}. */
    public record Palier(int anneesAnciennete, int joursSupplementaires) {
    }

    public BaremeConges {
        paliersAnciennete = paliersAnciennete == null ? List.of() : List.copyOf(paliersAnciennete);
    }

    /** Barème légal sénégalais — valeurs de semis du singleton, et repli des tests. */
    public static BaremeConges defaut() {
        return new BaremeConges(
                2,
                true, 1, 14, true, null,
                true, PALIERS_LEGAUX,
                false);
    }

    /** +1 j à 10 ans, +2 à 15, +3 à 20, +6 à 25. */
    public static final List<Palier> PALIERS_LEGAUX = List.of(
            new Palier(10, 1),
            new Palier(15, 2),
            new Palier(20, 3),
            new Palier(25, 6));

    /**
     * Jours accordés pour une ancienneté donnée.
     *
     * <p>{@code max} et non somme : les paliers ne se cumulent pas. Le {@code max} rend au
     * passage le résultat indépendant de l'ordre des paliers en base.
     */
    public int joursPourAnciennete(int annees) {
        if (!supplementAncienneteActif) {
            return 0;
        }
        return paliersAnciennete.stream()
                .filter(p -> annees >= p.anneesAnciennete())
                .max(Comparator.comparingInt(Palier::joursSupplementaires))
                .map(Palier::joursSupplementaires)
                .orElse(0);
    }
}
