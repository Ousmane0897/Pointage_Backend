package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Enum.rh.GenreEmploye;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Majorations des droits à congés : enfants à charge et ancienneté.
 *
 * <p>Toutes les dates sont <b>figées</b>. Âge et ancienneté s'apprécient au <b>31 décembre de
 * l'exercice</b> — ce qui se vérifie en calculant le même employé sur deux exercices
 * différents et en constatant que le résultat change avec l'exercice, pas avec la date du jour.
 */
class CongeAcquisCalculatorSupplementsTest {

    private final CongeAcquisCalculator calculator = new CongeAcquisCalculator();
    private final BaremeConges legal = BaremeConges.defaut();

    /** Aujourd'hui, choisi très postérieur pour que tous les exercices testés soient clos. */
    private static final LocalDate AUJOURDHUI = LocalDate.of(2040, 6, 15);

    private static DroitsEmployeSnapshot employe(GenreEmploye genre, LocalDate entree, int... anneesNaissance) {
        List<LocalDate> naissances = java.util.Arrays.stream(anneesNaissance)
                .mapToObj(a -> LocalDate.of(a, 6, 1))
                .toList();
        return new DroitsEmployeSnapshot(entree, genre, naissances);
    }

    // =========================================================================
    //  Enfants
    // =========================================================================

    @Test
    void une_mere_recoit_un_jour_par_enfant_de_moins_de_14_ans_au_31_decembre() {
        // Nés en juin 2012, 2015 et 2020. Au 31/12/2026 : 14 ans, 11 ans, 6 ans.
        // Le seuil est STRICT (« moins de 14 ans ») : l'aîné est exclu.
        DroitsEmployeSnapshot mere = employe(GenreEmploye.FEMME, LocalDate.of(2010, 1, 1), 2012, 2015, 2020);

        assertThat(calculator.enfantsBeneficiaires(2026, mere, legal)).isEqualTo(2);
        assertThat(calculator.supplementEnfants(2026, mere, legal)).isEqualTo(2);
    }

    @Test
    void le_meme_dossier_donne_un_droit_different_sur_un_exercice_anterieur() {
        // C'est LE test du recalcul rétroactif : au 31/12/2024 les trois enfants ont
        // 12, 9 et 4 ans — l'aîné est encore éligible. Un exercice clos doit donc rendre
        // 3 aujourd'hui comme dans dix ans, alors qu'un compteur d'enfants (ou un âge
        // apprécié « aujourd'hui ») ferait bouger ce passé à chaque anniversaire.
        DroitsEmployeSnapshot mere = employe(GenreEmploye.FEMME, LocalDate.of(2010, 1, 1), 2012, 2015, 2020);

        assertThat(calculator.enfantsBeneficiaires(2024, mere, legal)).isEqualTo(3);
        assertThat(calculator.enfantsBeneficiaires(2026, mere, legal)).isEqualTo(2);
    }

    @Test
    void un_pere_n_y_a_pas_droit_tant_que_la_reserve_aux_meres_est_active() {
        DroitsEmployeSnapshot pere = employe(GenreEmploye.HOMME, LocalDate.of(2010, 1, 1), 2015, 2020);

        assertThat(calculator.supplementEnfants(2026, pere, legal)).isZero();
    }

    @Test
    void la_reserve_aux_meres_desactivee_ouvre_le_droit_aux_deux_parents() {
        BaremeConges ouvert = new BaremeConges(2, true, 1, 14, false, null,
                true, BaremeConges.PALIERS_LEGAUX, false);
        DroitsEmployeSnapshot pere = employe(GenreEmploye.HOMME, LocalDate.of(2010, 1, 1), 2015, 2020);

        assertThat(calculator.supplementEnfants(2026, pere, ouvert)).isEqualTo(2);
    }

    @Test
    void un_genre_absent_n_ouvre_pas_le_droit_tant_que_la_reserve_est_active() {
        // On n'invente pas un droit sur une donnée manquante.
        DroitsEmployeSnapshot sansGenre = employe(null, LocalDate.of(2010, 1, 1), 2020);

        assertThat(calculator.supplementEnfants(2026, sansGenre, legal)).isZero();
    }

    @Test
    void un_enfant_ne_apres_la_date_de_reference_est_ignore() {
        DroitsEmployeSnapshot mere = employe(GenreEmploye.FEMME, LocalDate.of(2010, 1, 1), 2027);

        assertThat(calculator.enfantsBeneficiaires(2026, mere, legal)).isZero();
    }

    @Test
    void un_dossier_sans_enfants_dates_ne_recoit_aucune_majoration() {
        // Aucun repli sur nombreEnfants : sans date de naissance, pas de droit.
        DroitsEmployeSnapshot sansEnfants = employe(GenreEmploye.FEMME, LocalDate.of(2010, 1, 1));

        assertThat(calculator.supplementEnfants(2026, sansEnfants, legal)).isZero();
    }

    @Test
    void le_plafond_borne_la_majoration_enfants() {
        BaremeConges plafonne = new BaremeConges(2, true, 1, 14, true, 2,
                true, BaremeConges.PALIERS_LEGAUX, false);
        DroitsEmployeSnapshot mere = employe(GenreEmploye.FEMME, LocalDate.of(2010, 1, 1), 2015, 2018, 2020, 2022);

        assertThat(calculator.enfantsBeneficiaires(2026, mere, plafonne)).isEqualTo(4);
        assertThat(calculator.supplementEnfants(2026, mere, plafonne)).isEqualTo(2);
    }

    // =========================================================================
    //  Ancienneté
    // =========================================================================

    @Test
    void les_paliers_d_anciennete_s_ouvrent_a_10_15_20_et_25_ans() {
        LocalDate entree = LocalDate.of(2010, 1, 1);

        // Au 31/12/2019 : 9 ans révolus, le palier de 10 n'est pas atteint.
        assertThat(calculator.supplementAnciennete(2019, entree, legal)).isZero();
        assertThat(calculator.supplementAnciennete(2020, entree, legal)).isEqualTo(1);
        assertThat(calculator.supplementAnciennete(2025, entree, legal)).isEqualTo(2);
        assertThat(calculator.supplementAnciennete(2030, entree, legal)).isEqualTo(3);
        assertThat(calculator.supplementAnciennete(2035, entree, legal)).isEqualTo(6);
    }

    @Test
    void les_paliers_ne_se_cumulent_pas() {
        // 25 ans d'ancienneté valent 6 jours, et non 1 + 2 + 3 + 6 = 12.
        assertThat(calculator.supplementAnciennete(2035, LocalDate.of(2010, 1, 1), legal)).isEqualTo(6);
    }

    @Test
    void l_ordre_des_paliers_en_base_est_indifferent() {
        BaremeConges desordre = new BaremeConges(2, true, 1, 14, true, null, true,
                List.of(new BaremeConges.Palier(25, 6), new BaremeConges.Palier(10, 1),
                        new BaremeConges.Palier(20, 3), new BaremeConges.Palier(15, 2)),
                false);

        assertThat(calculator.supplementAnciennete(2025, LocalDate.of(2010, 1, 1), desordre)).isEqualTo(2);
    }

    @Test
    void sans_date_d_entree_l_anciennete_est_nulle() {
        assertThat(calculator.anneesAnciennete(2026, null)).isZero();
        assertThat(calculator.supplementAnciennete(2026, null, legal)).isZero();
    }

    // =========================================================================
    //  Composition et gardes
    // =========================================================================

    @Test
    void l_acquis_total_est_la_somme_de_la_base_et_des_deux_majorations() {
        // Mère entrée le 01/01/2010 avec deux enfants éligibles : 24 de base sur une
        // année pleine, +2 enfants, +2 ancienneté (16 ans au 31/12/2026).
        DroitsEmployeSnapshot mere = employe(GenreEmploye.FEMME, LocalDate.of(2010, 1, 1), 2015, 2020);

        DetailAcquis detail = calculator.acquis(2026, mere, AUJOURDHUI, legal);

        assertThat(detail.acquisBase()).isEqualTo(24);
        assertThat(detail.supplementEnfants()).isEqualTo(2);
        assertThat(detail.supplementAnciennete()).isEqualTo(2);
        assertThat(detail.enfantsBeneficiaires()).isEqualTo(2);
        assertThat(detail.anneesAnciennete()).isEqualTo(16);
        assertThat(detail.total()).isEqualTo(28);
    }

    @Test
    void un_exercice_sans_aucun_mois_de_service_ne_donne_aucune_majoration() {
        // Exercice antérieur à l'embauche : sans droit de base, pas de majoration non plus,
        // sinon ces jours remonteraient dans le reliquat reporté.
        DroitsEmployeSnapshot mere = employe(GenreEmploye.FEMME, LocalDate.of(2020, 1, 1), 2015, 2018);

        DetailAcquis detail = calculator.acquis(2015, mere, AUJOURDHUI, legal);

        assertThat(detail.acquisBase()).isZero();
        assertThat(detail.supplementEnfants()).isZero();
        assertThat(detail.supplementAnciennete()).isZero();
        assertThat(detail.total()).isZero();
    }

    @Test
    void majorations_desactivees_l_acquis_se_reduit_a_la_base() {
        // Garantit la rétro-compatibilité : c'est exactement le comportement d'avant ce lot.
        BaremeConges sansMajorations = new BaremeConges(2, false, 1, 14, true, null,
                false, BaremeConges.PALIERS_LEGAUX, false);
        DroitsEmployeSnapshot mere = employe(GenreEmploye.FEMME, LocalDate.of(2010, 1, 1), 2015, 2020);

        DetailAcquis detail = calculator.acquis(2026, mere, AUJOURDHUI, sansMajorations);

        assertThat(detail.total()).isEqualTo(detail.acquisBase()).isEqualTo(24);
    }

    @Test
    void la_proratisation_optionnelle_ramene_les_majorations_aux_mois_de_service() {
        // 6 mois de service ⇒ la moitié des majorations (division entière).
        BaremeConges proratise = new BaremeConges(2, true, 1, 14, true, null,
                true, BaremeConges.PALIERS_LEGAUX, true);
        DroitsEmployeSnapshot mere = employe(GenreEmploye.FEMME, LocalDate.of(2010, 1, 1), 2015, 2018, 2020, 2022);

        DetailAcquis detail = calculator.acquis(2026, mere, LocalDate.of(2026, 7, 1), proratise);

        assertThat(detail.moisAcquis()).isEqualTo(6);
        assertThat(detail.supplementEnfants()).isEqualTo(2); // 4 × 6 / 12
        assertThat(detail.supplementAnciennete()).isEqualTo(1); // 2 × 6 / 12
    }
}
