package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.SyntheseFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.IngredientFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.MatierePremiere;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service de calcul des valeurs dérivées d'une fiche de formulation :
 * matière active (Fonction A), eau de complément « qsp » (Fonction B) et
 * contrôle du total vs taille du lot (Fonction C).
 *
 * <p><b>Pur et testable isolément</b> : aucune dépendance Spring/Mongo. Toutes les
 * entrées (ingrédients, taille du lot, matières premières référencées, tolérance)
 * sont passées en paramètres. La tolérance provient du paramétrage global
 * ({@code ParametresProductionChimie}) fourni par l'appelant.</p>
 *
 * <p><b>Aucun arrondi intermédiaire</b> : les sommes sont exactes en {@link BigDecimal} ;
 * seules les divisions non terminantes (pourcentages) sont bornées à {@link #SCALE_DIVISION}
 * décimales. L'arrondi d'affichage (kg → 1 décimale, % → 2 décimales) est fait côté client.</p>
 */
@Service
public class FormulationCalculService {

    /** Échelle des divisions non terminantes (%). Large pour préserver la précision ; le front arrondit. */
    private static final int SCALE_DIVISION = 6;

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    /** Tolérance par défaut si le paramétrage ne fournit rien (± 0,1 %). */
    public static final BigDecimal TOLERANCE_TOTAL_DEFAUT_PCT = new BigDecimal("0.1");

    /**
     * Calcule la synthèse dérivée d'une formule.
     *
     * @param ingredients      lignes d'ingrédients (peut être null/vide)
     * @param quantiteRef      taille du lot de référence (peut être null ou ≤ 0)
     * @param mpById           matières premières référencées, indexées par id (concentration MA + drapeau « compter »)
     * @param toleranceTotalPct tolérance de contrôle du total en % ; repli sur {@link #TOLERANCE_TOTAL_DEFAUT_PCT} si null
     * @return la synthèse (jamais persistée)
     */
    public SyntheseFormulation calculer(List<IngredientFormulation> ingredients,
                                        Double quantiteRef,
                                        Map<String, MatierePremiere> mpById,
                                        BigDecimal toleranceTotalPct) {
        List<IngredientFormulation> lignes = ingredients == null ? List.of() : ingredients;
        Map<String, MatierePremiere> mp = mpById == null ? Map.of() : mpById;
        BigDecimal tolerance = toleranceTotalPct == null ? TOLERANCE_TOTAL_DEFAUT_PCT : toleranceTotalPct;
        List<String> warnings = new ArrayList<>();

        boolean lotValide = quantiteRef != null && quantiteRef > 0;
        BigDecimal lot = lotValide ? BigDecimal.valueOf(quantiteRef) : null;
        if (!lotValide) {
            warnings.add("Taille du lot absente ou nulle : les pourcentages ne peuvent pas être calculés.");
        }

        // ─── Fonction A : matière active ────────────────────────────────────
        BigDecimal maTotaleKg = BigDecimal.ZERO;
        for (IngredientFormulation ligne : lignes) {
            if (ligne.isQs() || ligne.isIngredientComplement()) {
                continue; // q.s. et compléments exclus du calcul MA
            }
            MatierePremiere matiere = mp.get(ligne.getMatierePremiereId());
            if (matiere == null || !matiere.isCompterDansMa()) {
                continue; // seule la case « compter dans la MA » décide
            }
            BigDecimal dosage = dosageOuZero(ligne);
            Double maPctMp = matiere.getMatiereActivePct();
            if (maPctMp == null) {
                warnings.add("La matière première « " + nomLigne(ligne, matiere)
                        + " » est comptée dans la MA mais n'a pas de concentration renseignée (comptée pour 0).");
                continue;
            }
            // dosage × maPct / 100 — division par 100 toujours exacte (dénominateur 2²·5²)
            BigDecimal apport = dosage.multiply(BigDecimal.valueOf(maPctMp)).divide(CENT);
            maTotaleKg = maTotaleKg.add(apport);
        }

        BigDecimal maPct = lotValide
                ? maTotaleKg.multiply(CENT).divide(lot, SCALE_DIVISION, RoundingMode.HALF_UP)
                : null;

        // ─── Fonction B : eau de complément (qsp) ───────────────────────────
        int nbComplement = 0;
        IngredientFormulation ligneComplement = null;
        for (IngredientFormulation ligne : lignes) {
            if (ligne.isIngredientComplement()) {
                nbComplement++;
                if (ligneComplement == null) {
                    ligneComplement = ligne;
                }
            }
        }
        if (nbComplement > 1) {
            warnings.add("Plusieurs lignes de complément (qsp) : une seule est autorisée par formule.");
        }

        BigDecimal eauQspKg = null;
        if (nbComplement == 1 && lotValide) {
            BigDecimal sommeAutres = BigDecimal.ZERO;
            for (IngredientFormulation ligne : lignes) {
                if (ligne == ligneComplement || ligne.isQs()) {
                    continue;
                }
                sommeAutres = sommeAutres.add(dosageOuZero(ligne));
            }
            BigDecimal eau = lot.subtract(sommeAutres);
            if (eau.signum() < 0) {
                warnings.add("Les autres ingrédients dépassent la taille du lot : "
                        + "l'eau de complément serait négative.");
            } else {
                eauQspKg = eau;
            }
        }

        // ─── Fonction C : contrôle du total ─────────────────────────────────
        // Total saisi = Σ(dosages des lignes non-qs), la ligne de complément valide
        // étant valorisée par l'eau calculée (le total est alors juste par construction).
        BigDecimal totalSaisiKg = BigDecimal.ZERO;
        for (IngredientFormulation ligne : lignes) {
            if (ligne.isQs()) {
                continue;
            }
            if (ligne == ligneComplement && eauQspKg != null) {
                totalSaisiKg = totalSaisiKg.add(eauQspKg);
            } else {
                totalSaisiKg = totalSaisiKg.add(dosageOuZero(ligne));
            }
        }

        BigDecimal ecartPct = null;
        boolean totalConforme = false;
        if (lotValide) {
            ecartPct = totalSaisiKg.subtract(lot).abs()
                    .multiply(CENT).divide(lot, SCALE_DIVISION, RoundingMode.HALF_UP);
            totalConforme = ecartPct.compareTo(tolerance) <= 0;
        }

        return SyntheseFormulation.builder()
                .maTotaleKg(maTotaleKg)
                .maPct(maPct)
                .eauQspKg(eauQspKg)
                .totalSaisiKg(totalSaisiKg)
                .ecartTolerancePct(ecartPct)
                .totalConforme(totalConforme)
                .nbLignesComplement(nbComplement)
                .warnings(warnings)
                .build();
    }

    private BigDecimal dosageOuZero(IngredientFormulation ligne) {
        return ligne.getDosage() == null ? BigDecimal.ZERO : BigDecimal.valueOf(ligne.getDosage());
    }

    private String nomLigne(IngredientFormulation ligne, MatierePremiere matiere) {
        if (ligne.getMatierePremiereNom() != null && !ligne.getMatierePremiereNom().isBlank()) {
            return ligne.getMatierePremiereNom();
        }
        if (matiere != null && matiere.getNom() != null) {
            return matiere.getNom();
        }
        return ligne.getMatierePremiereId();
    }
}
