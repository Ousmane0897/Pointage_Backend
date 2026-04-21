package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.ParametresPaie;
import com.example.Pointage_Cleanic.entities.TrancheIr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs du moteur de calcul — aucune dépendance Spring.
 * Chaque méthode publique de CalculPaieService est testée isolément
 * avec des valeurs connues et des cas limites (plafonds, tranches IR...).
 */
class CalculPaieServiceTest {

    private CalculPaieService service;
    private ParametresPaie params;

    @BeforeEach
    void setUp() {
        service = new CalculPaieService(null, null, null, null, null);

        params = ParametresPaie.builder()
                .tauxIpresGeneralSalarie(0.056)
                .tauxIpresGeneralEmployeur(0.084)
                .plafondIpresGeneral(432_000L)
                .tauxIpresComplementaireSalarie(0.024)
                .tauxIpresComplementaireEmployeur(0.036)
                .plafondIpresComplementaire(1_296_000L)
                .tauxCssPrestationsFamilialesEmployeur(0.07)
                .plafondCss(63_000L)
                .tauxAtMpDefaut(0.01)
                .montantTrimfMensuel(900L)
                .joursOuvrablesStandardMois(22)
                .baremeIr(List.of(
                        TrancheIr.builder().borneMin(0L).borneMax(630_000L).taux(0.0).build(),
                        TrancheIr.builder().borneMin(630_001L).borneMax(1_500_000L).taux(0.20).build(),
                        TrancheIr.builder().borneMin(1_500_001L).borneMax(4_000_000L).taux(0.30).build(),
                        TrancheIr.builder().borneMin(4_000_001L).borneMax(8_000_000L).taux(0.35).build(),
                        TrancheIr.builder().borneMin(8_000_001L).borneMax(13_500_000L).taux(0.37).build(),
                        TrancheIr.builder().borneMin(13_500_001L).borneMax(null).taux(0.40).build()
                ))
                .build();
    }

    // ─── Salaire base proraté ──────────────────────────────────────────

    @Test
    void salaireBaseProrata_mois_complet() {
        assertThat(service.calculSalaireBaseProrata(300_000L, 22, 22)).isEqualTo(300_000L);
    }

    @Test
    void salaireBaseProrata_mois_partiel() {
        assertThat(service.calculSalaireBaseProrata(300_000L, 11, 22)).isEqualTo(150_000L);
    }

    @Test
    void salaireBaseProrata_zero_jour() {
        assertThat(service.calculSalaireBaseProrata(300_000L, 0, 22)).isEqualTo(0L);
    }

    @Test
    void salaireBaseProrata_depasse_jours_ouvrables() {
        assertThat(service.calculSalaireBaseProrata(300_000L, 25, 22)).isEqualTo(300_000L);
    }

    @Test
    void salaireBaseProrata_jours_ouvrables_nul() {
        assertThat(service.calculSalaireBaseProrata(300_000L, 10, 0)).isEqualTo(0L);
    }

    // ─── Heures supplémentaires ────────────────────────────────────────

    @Test
    void montantHeuresSup_calcul_basique() {
        // salaire 176_000 / (22j * 8h) = 1000 FCFA / heure
        // 2h HS à 15% → majoré équivalent 0.3h → 2.3h payées à 1000 = 2300
        long result = service.calculMontantHeuresSup(176_000L, 22, 2.0, 0.3);
        assertThat(result).isEqualTo(2_300L);
    }

    @Test
    void montantHeuresSup_aucune_heure() {
        assertThat(service.calculMontantHeuresSup(176_000L, 22, 0.0, 0.0)).isEqualTo(0L);
    }

    @Test
    void montantHeuresSup_salaire_nul() {
        assertThat(service.calculMontantHeuresSup(0L, 22, 5.0, 2.0)).isEqualTo(0L);
    }

    // ─── IPRES Régime Général ──────────────────────────────────────────

    @Test
    void ipresGeneralSalarie_base_sous_plafond() {
        // 200 000 * 5.6% = 11 200
        assertThat(service.calculIpresGeneralSalarie(200_000L, params)).isEqualTo(11_200L);
    }

    @Test
    void ipresGeneralSalarie_base_au_plafond() {
        assertThat(service.calculIpresGeneralSalarie(432_000L, params)).isEqualTo(24_192L);
    }

    @Test
    void ipresGeneralSalarie_base_sur_plafond_ecretage() {
        // 500_000 écrêté à 432_000 → 432_000 * 5.6% = 24 192
        assertThat(service.calculIpresGeneralSalarie(500_000L, params)).isEqualTo(24_192L);
    }

    @Test
    void ipresGeneralEmployeur_base_sous_plafond() {
        assertThat(service.calculIpresGeneralEmployeur(200_000L, params)).isEqualTo(16_800L);
    }

    @Test
    void assietteIpresGeneral_ecretee_au_plafond() {
        assertThat(service.calculAssietteIpresGeneral(1_000_000L, params)).isEqualTo(432_000L);
    }

    // ─── IPRES Régime Complémentaire ────────────────────────────────────

    @Test
    void assietteIpresComplementaire_sous_plafond_general_renvoie_zero() {
        assertThat(service.calculAssietteIpresComplementaire(400_000L, params)).isEqualTo(0L);
    }

    @Test
    void assietteIpresComplementaire_ecretee_au_plafond_comp() {
        // excedent 600_000 - 432_000 = 168_000 ; plafond comp = 1_296_000 - 432_000 = 864_000
        assertThat(service.calculAssietteIpresComplementaire(600_000L, params)).isEqualTo(168_000L);
    }

    @Test
    void assietteIpresComplementaire_depasse_plafond_comp() {
        // base 2 000 000 → excedent 1_568_000 > plafond comp 864_000 → retour 864_000
        assertThat(service.calculAssietteIpresComplementaire(2_000_000L, params)).isEqualTo(864_000L);
    }

    @Test
    void ipresComplementaireSalarie_exemple() {
        // assiette comp = 168 000, salarié 2.4% = 4 032
        assertThat(service.calculIpresComplementaireSalarie(600_000L, params)).isEqualTo(4_032L);
    }

    // ─── CSS ───────────────────────────────────────────────────────────

    @Test
    void assietteCss_sous_plafond() {
        assertThat(service.calculAssietteCss(50_000L, params)).isEqualTo(50_000L);
    }

    @Test
    void assietteCss_ecretee() {
        assertThat(service.calculAssietteCss(200_000L, params)).isEqualTo(63_000L);
    }

    @Test
    void cssPrestationsFamiliales() {
        // min(100k, 63k) = 63k * 7% = 4 410
        assertThat(service.calculCssPrestationsFamiliales(100_000L, params)).isEqualTo(4_410L);
    }

    @Test
    void cssAtMp_taux_defaut() {
        // 63k * 1% = 630
        assertThat(service.calculCssAtMp(100_000L, null, params)).isEqualTo(630L);
    }

    @Test
    void cssAtMp_taux_override_categorie() {
        // 63k * 5% = 3 150 (secteur à risque)
        assertThat(service.calculCssAtMp(100_000L, 0.05, params)).isEqualTo(3_150L);
    }

    // ─── Impôt sur le revenu (barème progressif) ────────────────────────

    @Test
    void ir_sous_seuil_exoneration() {
        // 500k annuel → tranche 0-630k à 0%
        assertThat(service.calculIrSurBaseAnnuelle(500_000L, params)).isEqualTo(0L);
    }

    @Test
    void ir_tranche_1_seule() {
        // 1 000 000 annuel
        // Tranche 1 : 0-630k à 0% = 0
        // Tranche 2 : 630 001-1 000 000 = 369 999 à 20% = 74 000 (arrondi)
        long ir = service.calculIrSurBaseAnnuelle(1_000_000L, params);
        assertThat(ir).isBetween(73_000L, 75_000L);
    }

    @Test
    void ir_tranches_multiples() {
        // 5 000 000 annuel → touche tranches 1, 2, 3, 4
        // T1 : 0
        // T2 : (1 500 000 - 630 001 + 1) * 0.20 = 870 000 * 0.20 = 174 000
        // T3 : (4 000 000 - 1 500 001 + 1) * 0.30 = 2 500 000 * 0.30 = 750 000
        // T4 : (5 000 000 - 4 000 001 + 1) * 0.35 = 1 000 000 * 0.35 = 350 000
        // Total ≈ 1 274 000
        long ir = service.calculIrSurBaseAnnuelle(5_000_000L, params);
        assertThat(ir).isBetween(1_270_000L, 1_280_000L);
    }

    @Test
    void ir_barème_vide_retourne_zero() {
        ParametresPaie vide = ParametresPaie.builder().baremeIr(List.of()).build();
        assertThat(service.calculIrSurBaseAnnuelle(5_000_000L, vide)).isEqualTo(0L);
    }

    @Test
    void irMensuel_avec_deduction_cotisations() {
        // Brut imposable mensuel 200 000, IPRES sal = 11 200
        // Base mensuelle 188 800 → annuelle 2 265 600
        // T1: 0 | T2: 870k * 0.20 = 174 000 | T3: (2 265 600 - 1 500 001 + 1) * 0.30 = 765 600 * 0.30 = 229 680
        // Total annuel ≈ 403 680 → mensuel ≈ 33 640
        long ir = service.calculIrMensuel(200_000L, 11_200L, params);
        assertThat(ir).isBetween(33_000L, 34_500L);
    }

    @Test
    void irMensuel_sous_seuil() {
        // Brut 50 000 * 12 = 600 000 < 630 000 → IR = 0
        assertThat(service.calculIrMensuel(50_000L, 2_800L, params)).isEqualTo(0L);
    }

    // ─── TRIMF ──────────────────────────────────────────────────────────

    @Test
    void trimfMensuel_depuis_parametres() {
        assertThat(service.calculTrimfMensuel(params)).isEqualTo(900L);
    }

    @Test
    void trimfMensuel_non_configure_retourne_zero() {
        ParametresPaie vide = ParametresPaie.builder().build();
        assertThat(service.calculTrimfMensuel(vide)).isEqualTo(0L);
    }
}
