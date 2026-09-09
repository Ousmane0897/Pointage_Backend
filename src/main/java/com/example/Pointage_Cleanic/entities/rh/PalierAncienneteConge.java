package com.example.Pointage_Cleanic.entities.rh;

import lombok.*;

/**
 * Palier du barème d'ancienneté : « à partir de {@code anneesAnciennete} années de
 * service, {@code joursSupplementaires} jours de congé en plus ».
 *
 * <p>⚠ Les paliers ne sont <b>pas cumulatifs</b> entre eux : un employé de 26 ans
 * d'ancienneté a 6 jours, pas 1+2+3+6=12. Le calcul retient le {@code max} des paliers
 * atteints, ce qui rend aussi l'ordre de la liste en base indifférent.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PalierAncienneteConge {

    /** Ancienneté requise, en années révolues. */
    private Integer anneesAnciennete;

    /** Jours de congé supplémentaires accordés à ce palier. */
    private Integer joursSupplementaires;
}
