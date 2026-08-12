package com.example.Pointage_Cleanic.services.rh;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Décompte en jours ouvrés. Auparavant le calcul portait sur des jours calendaires
 * (week-ends inclus) alors que le solde est exprimé en jours ouvrés : le solde se vidait
 * plus vite que les jours réellement posés.
 */
class CongeCalendrierTest {

    // Août 2026 : le 10 est un lundi, le 14 un vendredi, le 15 un samedi.
    private static final LocalDate LUNDI = LocalDate.of(2026, 8, 10);
    private static final LocalDate VENDREDI = LocalDate.of(2026, 8, 14);
    private static final LocalDate SAMEDI = LocalDate.of(2026, 8, 15);
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 8, 16);

    @Test
    void une_semaine_pleine_vaut_cinq_jours() {
        assertThat(CongeCalendrier.joursOuvres(LUNDI, VENDREDI)).isEqualTo(5);
    }

    @Test
    void deux_semaines_pleines_valent_dix_jours_et_non_douze() {
        assertThat(CongeCalendrier.joursOuvres(LUNDI, VENDREDI.plusWeeks(1))).isEqualTo(10);
    }

    @Test
    void le_week_end_encadre_n_est_pas_decompte() {
        // Vendredi → lundi suivant : seuls le vendredi et le lundi comptent.
        assertThat(CongeCalendrier.joursOuvres(VENDREDI, LUNDI.plusWeeks(1))).isEqualTo(2);
    }

    @Test
    void une_periode_entierement_sur_un_week_end_vaut_zero() {
        assertThat(CongeCalendrier.joursOuvres(SAMEDI, DIMANCHE)).isZero();
    }

    @Test
    void une_journee_ouvree_isolee_vaut_un_jour() {
        assertThat(CongeCalendrier.joursOuvres(LUNDI, LUNDI)).isEqualTo(1);
    }

    @Test
    void une_periode_demarrant_un_samedi_ignore_le_week_end() {
        assertThat(CongeCalendrier.joursOuvres(SAMEDI, VENDREDI.plusWeeks(1))).isEqualTo(5);
    }

    @Test
    void dates_absentes_ou_inversees_donnent_zero() {
        assertThat(CongeCalendrier.joursOuvres(null, VENDREDI)).isZero();
        assertThat(CongeCalendrier.joursOuvres(LUNDI, null)).isZero();
        assertThat(CongeCalendrier.joursOuvres(VENDREDI, LUNDI)).isZero();
    }

    @Test
    void samedi_et_dimanche_ne_sont_pas_ouvres() {
        assertThat(CongeCalendrier.estOuvre(LUNDI)).isTrue();
        assertThat(CongeCalendrier.estOuvre(VENDREDI)).isTrue();
        assertThat(CongeCalendrier.estOuvre(SAMEDI)).isFalse();
        assertThat(CongeCalendrier.estOuvre(DIMANCHE)).isFalse();
    }
}
