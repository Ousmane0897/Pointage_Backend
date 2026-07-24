package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.SyntheseFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.IngredientFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.MatierePremiere;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du calcul des valeurs dérivées d'une formule (MA, eau qsp, contrôle du total).
 *
 * <p>Jeu de référence : Détergent V5, lot 1000 kg (cf. cahier des charges). NB : le CDC
 * annonce « eau = 813 kg » mais la somme des 8 ingrédients non-eau vaut 181 kg
 * (80+30+30+8+10+3+16+4), donc l'eau de complément exacte est <b>819 kg</b> (1000 − 181) —
 * la valeur 813 du CDC est une erreur arithmétique (181 + 813 = 994 ≠ 1000). Le test
 * vérifie la valeur correcte 819 et un total conforme (vert).</p>
 */
class FormulationCalculServiceTest {

    private final FormulationCalculService service = new FormulationCalculService();

    // ─── Helpers ────────────────────────────────────────────────────────────

    private IngredientFormulation ligne(String mpId, Double dosage, boolean complement, boolean qs) {
        return IngredientFormulation.builder()
                .matierePremiereId(mpId)
                .dosage(dosage)
                .ingredientComplement(complement)
                .qs(qs)
                .build();
    }

    private MatierePremiere mp(String id, String nom, Double maPct, boolean compter) {
        return MatierePremiere.builder()
                .id(id).nom(nom).matiereActivePct(maPct).compterDansMa(compter)
                .build();
    }

    /** Construit les 9 lignes + la map MP du jeu de référence Détergent V5. */
    private List<IngredientFormulation> lignesReference() {
        List<IngredientFormulation> l = new ArrayList<>();
        l.add(ligne("SLES", 80.0, false, false));
        l.add(ligne("CAPB", 30.0, false, false));
        l.add(ligne("LABSA", 30.0, false, false));
        l.add(ligne("AOS", 8.0, false, false));
        l.add(ligne("CMEA", 10.0, false, false));
        l.add(ligne("GLUCO", 3.0, false, false));
        l.add(ligne("SEL", 16.0, false, false));
        l.add(ligne("PARFUM", 4.0, false, false));
        l.add(ligne("EAU", null, true, false)); // complément qsp, dosage calculé
        return l;
    }

    private Map<String, MatierePremiere> mpReference() {
        Map<String, MatierePremiere> m = new HashMap<>();
        m.put("SLES", mp("SLES", "SLES", 70.0, true));
        m.put("CAPB", mp("CAPB", "Bétaïne", 30.0, true));
        m.put("LABSA", mp("LABSA", "LABSA", 96.0, true));
        m.put("AOS", mp("AOS", "AOS poudre", 92.0, true));
        m.put("CMEA", mp("CMEA", "CMEA", 100.0, true));
        m.put("GLUCO", mp("GLUCO", "Gluconate", 100.0, false));
        m.put("SEL", mp("SEL", "Sel", 100.0, false));
        m.put("PARFUM", mp("PARFUM", "Parfum", null, false));
        m.put("EAU", mp("EAU", "Eau", null, false));
        return m;
    }

    // ─── Cas nominal ──────────────────────────────────────────────────────────

    @Test
    void jeu_de_reference_detergent_v5() {
        SyntheseFormulation s = service.calculer(lignesReference(), 1000.0, mpReference(), null);

        assertThat(s.getMaTotaleKg()).isEqualByComparingTo("111.16");
        assertThat(s.getMaPct()).isEqualByComparingTo("11.116"); // affiché 11,12 %
        assertThat(s.getEauQspKg()).isEqualByComparingTo("819");  // 1000 − 181 (le CDC dit 813 par erreur)
        assertThat(s.getTotalSaisiKg()).isEqualByComparingTo("1000");
        assertThat(s.getEcartTolerancePct()).isEqualByComparingTo("0");
        assertThat(s.isTotalConforme()).isTrue();
        assertThat(s.getNbLignesComplement()).isEqualTo(1);
        assertThat(s.getWarnings()).isEmpty();
    }

    // ─── Cas limites ──────────────────────────────────────────────────────────

    @Test
    void ligne_qs_sans_quantite_est_ignoree() {
        List<IngredientFormulation> lignes = new ArrayList<>();
        lignes.add(ligne("SLES", 80.0, false, false));
        lignes.add(ligne("SOUDE", null, false, true)); // q.s., dosage null, comptée MA
        Map<String, MatierePremiere> mp = new HashMap<>();
        mp.put("SLES", mp("SLES", "SLES", 70.0, true));
        mp.put("SOUDE", mp("SOUDE", "Soude", 100.0, true));

        SyntheseFormulation s = service.calculer(lignes, 1000.0, mp, null);

        assertThat(s.getMaTotaleKg()).isEqualByComparingTo("56"); // soude q.s. exclue
        assertThat(s.getTotalSaisiKg()).isEqualByComparingTo("80"); // q.s. exclue du total
        assertThat(s.getWarnings()).isEmpty();
    }

    @Test
    void aucune_ligne_qsp_ne_calcule_pas_l_eau() {
        List<IngredientFormulation> lignes = new ArrayList<>();
        lignes.add(ligne("SLES", 80.0, false, false));
        Map<String, MatierePremiere> mp = new HashMap<>();
        mp.put("SLES", mp("SLES", "SLES", 70.0, true));

        SyntheseFormulation s = service.calculer(lignes, 1000.0, mp, null);

        assertThat(s.getEauQspKg()).isNull();
        assertThat(s.getNbLignesComplement()).isZero();
        assertThat(s.isTotalConforme()).isFalse(); // 80 vs 1000 → hors tolérance
    }

    @Test
    void deux_lignes_qsp_sont_signalees() {
        List<IngredientFormulation> lignes = new ArrayList<>();
        lignes.add(ligne("EAU1", null, true, false));
        lignes.add(ligne("EAU2", null, true, false));
        Map<String, MatierePremiere> mp = new HashMap<>();

        SyntheseFormulation s = service.calculer(lignes, 1000.0, mp, null);

        assertThat(s.getNbLignesComplement()).isEqualTo(2);
        assertThat(s.getEauQspKg()).isNull(); // ambigu → non calculé
        assertThat(s.getWarnings()).anyMatch(w -> w.toLowerCase().contains("complément"));
    }

    @Test
    void somme_des_autres_superieure_au_lot_donne_eau_negative_signalee() {
        List<IngredientFormulation> lignes = new ArrayList<>();
        lignes.add(ligne("A", 700.0, false, false));
        lignes.add(ligne("B", 500.0, false, false));
        lignes.add(ligne("EAU", null, true, false));
        Map<String, MatierePremiere> mp = new HashMap<>();

        SyntheseFormulation s = service.calculer(lignes, 1000.0, mp, null);

        assertThat(s.getEauQspKg()).isNull(); // pas de quantité négative
        assertThat(s.getWarnings()).anyMatch(w -> w.toLowerCase().contains("dépassent"));
    }

    @Test
    void taille_de_lot_absente_ou_nulle() {
        List<IngredientFormulation> lignes = new ArrayList<>();
        lignes.add(ligne("SLES", 80.0, false, false));
        Map<String, MatierePremiere> mp = new HashMap<>();
        mp.put("SLES", mp("SLES", "SLES", 70.0, true));

        SyntheseFormulation sNull = service.calculer(lignes, null, mp, null);
        assertThat(sNull.getMaPct()).isNull();
        assertThat(sNull.getEcartTolerancePct()).isNull();
        assertThat(sNull.getMaTotaleKg()).isEqualByComparingTo("56"); // MA(kg) reste calculable
        assertThat(sNull.getWarnings()).anyMatch(w -> w.toLowerCase().contains("lot"));

        SyntheseFormulation sZero = service.calculer(lignes, 0.0, mp, null);
        assertThat(sZero.getMaPct()).isNull();
    }

    @Test
    void mp_comptee_sans_matiere_active_est_signalee_et_comptee_zero() {
        List<IngredientFormulation> lignes = new ArrayList<>();
        lignes.add(ligne("SLES", 80.0, false, false));
        lignes.add(ligne("X", 20.0, false, false)); // comptée mais sans MA
        Map<String, MatierePremiere> mp = new HashMap<>();
        mp.put("SLES", mp("SLES", "SLES", 70.0, true));
        mp.put("X", mp("X", "Inconnue", null, true));

        SyntheseFormulation s = service.calculer(lignes, 1000.0, mp, null);

        assertThat(s.getMaTotaleKg()).isEqualByComparingTo("56"); // X compte pour 0
        assertThat(s.getWarnings()).anyMatch(w -> w.contains("Inconnue"));
    }

    @Test
    void tolerance_personnalisee_bascule_le_verdict() {
        List<IngredientFormulation> lignes = new ArrayList<>();
        lignes.add(ligne("A", 999.0, false, false)); // écart 0,1 %
        Map<String, MatierePremiere> mp = new HashMap<>();

        // tolérance par défaut 0,1 % → conforme (0,1 ≤ 0,1)
        assertThat(service.calculer(lignes, 1000.0, mp, null).isTotalConforme()).isTrue();
        // tolérance stricte 0,05 % → non conforme
        assertThat(service.calculer(lignes, 1000.0, mp, new BigDecimal("0.05")).isTotalConforme()).isFalse();
    }
}
