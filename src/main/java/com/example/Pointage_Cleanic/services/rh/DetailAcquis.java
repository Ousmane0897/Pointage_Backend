package com.example.Pointage_Cleanic.services.rh;

/**
 * Ventilation des droits acquis sur un exercice : base + majorations.
 *
 * <p>Le {@link #total()} est ce qui alimente {@code SoldeCongeDto.acquis} ; les composantes
 * servent à l'expliquer à l'écran (« 16 j = 16 de base + 2 enfants + 1 ancienneté »), pas à
 * être recomposées ailleurs.
 *
 * @param moisAcquis           mois de service effectif révolus sur l'exercice
 * @param acquisBase           {@code moisAcquis × joursAcquisParMois}
 * @param supplementEnfants    majoration pour enfants à charge éligibles
 * @param supplementAnciennete majoration du palier d'ancienneté atteint
 * @param anneesAnciennete     ancienneté retenue, en années révolues (explique la majoration)
 * @param enfantsBeneficiaires nombre d'enfants ayant ouvert le droit (explique la majoration)
 */
public record DetailAcquis(
        int moisAcquis,
        int acquisBase,
        int supplementEnfants,
        int supplementAnciennete,
        int anneesAnciennete,
        int enfantsBeneficiaires
) {

    public int total() {
        return acquisBase + supplementEnfants + supplementAnciennete;
    }
}
