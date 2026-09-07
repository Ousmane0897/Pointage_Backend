package com.example.Pointage_Cleanic.services.rh;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Formule de l'<b>acquis de base</b> : 2 jours ouvrables par mois de service effectif.
 *
 * <p>Toutes les dates sont <b>figées</b> — c'est ici, et nulle part ailleurs, que le nombre de
 * jours de base est pinné. Les majorations (enfants, ancienneté) le sont par
 * {@link CongeAcquisCalculatorSupplementsTest} ; {@link DemandeCongeServiceSoldeTest} ne teste
 * que la composition du solde.
 *
 * <p>Le barème n'est plus une valeur injectée par Spring mais un argument : plus besoin de
 * {@code ReflectionTestUtils} pour le poser.
 */
class CongeAcquisCalculatorTest {

    private final CongeAcquisCalculator calculator = new CongeAcquisCalculator();
    private final BaremeConges bareme = BaremeConges.defaut();

    @Test
    void une_annee_pleine_vaut_douze_mois_soit_24_jours() {
        // Borne exclusive au 1er janvier suivant : avec le 31 décembre, MONTHS.between
        // rendrait 11 et amputerait d'un mois toute année pleine.
        assertThat(calculator.moisAcquis(2025, LocalDate.of(2020, 5, 10), LocalDate.of(2026, 9, 2)))
                .isEqualTo(12);
        assertThat(calculator.acquisDeBase(2025, LocalDate.of(2020, 5, 10), LocalDate.of(2026, 9, 2), bareme))
                .isEqualTo(24);
    }

    @Test
    void l_exercice_courant_ne_compte_que_les_mois_revolus() {
        // Au 02/09/2026, l'employé a 8 mois révolus (janvier à août), pas 9.
        assertThat(calculator.acquisDeBase(2026, LocalDate.of(2020, 5, 10), LocalDate.of(2026, 9, 2), bareme))
                .isEqualTo(16);
    }

    @Test
    void le_mois_se_compte_de_quantieme_a_quantieme() {
        // Entré le 15/03, au 02/09 : 15/03 → 15/08 = 5 mois révolus, le 6e n'est pas terminé.
        assertThat(calculator.moisAcquis(2026, LocalDate.of(2026, 3, 15), LocalDate.of(2026, 9, 2)))
                .isEqualTo(5);
        assertThat(calculator.acquisDeBase(2026, LocalDate.of(2026, 3, 15), LocalDate.of(2026, 9, 2), bareme))
                .isEqualTo(10);
        // Un jour plus tard, le 6e mois est révolu.
        assertThat(calculator.moisAcquis(2026, LocalDate.of(2026, 3, 15), LocalDate.of(2026, 9, 15)))
                .isEqualTo(6);
    }

    @Test
    void une_entree_en_cours_d_exercice_clos_est_proratisee() {
        // Embauché le 1er novembre 2025 : 2 mois sur l'exercice 2025, pas 12.
        assertThat(calculator.acquisDeBase(2025, LocalDate.of(2025, 11, 1), LocalDate.of(2026, 9, 2), bareme))
                .isEqualTo(4);
    }

    @Test
    void un_exercice_anterieur_a_l_entree_ne_donne_aucun_droit() {
        assertThat(calculator.moisAcquis(2023, LocalDate.of(2025, 11, 1), LocalDate.of(2026, 9, 2)))
                .isZero();
    }

    @Test
    void une_entree_dans_le_futur_ne_donne_aucun_droit_et_jamais_de_negatif() {
        assertThat(calculator.moisAcquis(2026, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 9, 2)))
                .isZero();
    }

    @Test
    void sans_date_d_entree_l_employe_est_repute_present_depuis_le_1er_janvier() {
        // Repli pour les dossiers antérieurs, où le champ n'a jamais été obligatoire.
        assertThat(calculator.acquisDeBase(2026, null, LocalDate.of(2026, 9, 2), bareme)).isEqualTo(16);
    }

    @Test
    void le_premier_jour_de_l_exercice_ne_donne_encore_aucun_droit() {
        assertThat(calculator.moisAcquis(2026, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1)))
                .isZero();
        // Le droit s'ouvre au premier mois révolu.
        assertThat(calculator.acquisDeBase(2026, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 2, 1), bareme))
                .isEqualTo(2);
    }

    @Test
    void le_taux_mensuel_est_surchargeable_par_configuration() {
        BaremeConges troisJours = new BaremeConges(3, false, 0, 14, true, null, false, java.util.List.of(), false);

        assertThat(calculator.acquisDeBase(2025, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 9, 2), troisJours))
                .isEqualTo(36);
    }
}
