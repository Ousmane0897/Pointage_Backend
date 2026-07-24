package com.example.Pointage_Cleanic.Dto.productionchimie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Synthèse dérivée d'une fiche de formulation : matière active, eau de complément
 * (qsp) et contrôle du total.
 *
 * <p>⚠️ <b>Valeurs calculées, jamais persistées.</b> Cet objet n'est pas stocké dans
 * la collection {@code production_chimie_formulations} : il est recalculé à chaque
 * lecture par {@link com.example.Pointage_Cleanic.services.productionchimie.FormulationCalculService}
 * et attaché au {@link FicheFormulationDto} de sortie uniquement.</p>
 *
 * <p>Les montants sont exposés en {@link BigDecimal} <b>exacts</b> (aucun arrondi
 * intermédiaire) ; l'arrondi d'affichage (kg → 1 décimale, % → 2 décimales) est
 * effectué côté client.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyntheseFormulation {

    /** Matière active totale (kg) = Σ(dosage × matiereActivePct/100) des lignes comptées. */
    private BigDecimal maTotaleKg;

    /** Pourcentage de matière active = maTotaleKg / quantiteRef × 100. Null si quantiteRef absent/0. */
    private BigDecimal maPct;

    /** Eau de complément (kg) = quantiteRef − Σ(autres lignes non-qs). Null si aucune ligne qsp ou valeur négative. */
    private BigDecimal eauQspKg;

    /** Total saisi (kg) = Σ(dosage des lignes non-qs), eau qsp comprise. */
    private BigDecimal totalSaisiKg;

    /** Écart relatif au lot = |totalSaisi − quantiteRef| / quantiteRef × 100. Null si quantiteRef absent/0. */
    private BigDecimal ecartTolerancePct;

    /** true si l'écart est dans la tolérance paramétrée (contrôle informatif, non bloquant). */
    private boolean totalConforme;

    /** Nombre de lignes marquées « ingrédient de complément (qsp) » (doit rester ≤ 1). */
    private int nbLignesComplement;

    /** Avertissements non bloquants (MP cochée sans MA, eau négative, lot absent…). */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
