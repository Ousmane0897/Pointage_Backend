package com.example.Pointage_Cleanic.services.pointage;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** C5 — limite de débit du pointage public. */
class PointageLimiteurDebitTest {

    /** Horloge réglable. */
    private static final class Horloge extends Clock {
        private Instant maintenant = Instant.parse("2026-11-21T08:00:00Z");

        void avancer(Duration d) {
            maintenant = maintenant.plus(d);
        }

        @Override
        public Instant instant() {
            return maintenant;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private static final int PAR_IP = 60;
    private static final int PAR_APPAREIL = 10;
    private static final int CODES_INCONNUS = 5;
    private static final Duration BLOCAGE = Duration.ofMinutes(15);

    private final Horloge horloge = new Horloge();
    private final PointageLimiteurDebit limiteur =
            new PointageLimiteurDebit(horloge, PAR_IP, PAR_APPAREIL, CODES_INCONNUS, BLOCAGE.toMinutes());

    /** Un site qui fait pointer 30 agents depuis la même IP en une minute n'est pas bloqué. */
    @Test
    void trente_pointages_d_appareils_differents_meme_ip_sans_refus() {
        for (int i = 0; i < 30; i++) {
            assertThat(limiteur.verifier("1.1.1.1", "app-" + i)).as("pointage " + (i + 1)).isEmpty();
        }
    }

    @Test
    void soixante_appels_par_ip_puis_refus() {
        for (int i = 0; i < PAR_IP; i++) {
            assertThat(limiteur.verifier("1.1.1.1", "app-" + i)).as("appel " + (i + 1)).isEmpty();
        }
        assertThat(limiteur.verifier("1.1.1.1", "app-x")).isPresent();
        assertThat(limiteur.verifier("2.2.2.2", "app-y")).as("autre IP").isEmpty();
    }

    @Test
    void dix_appels_par_appareil_puis_refus_meme_en_changeant_d_ip() {
        for (int i = 0; i < PAR_APPAREIL; i++) {
            assertThat(limiteur.verifier("10.0.0." + i, "meme-appareil")).isEmpty();
        }
        assertThat(limiteur.verifier("10.0.1.1", "meme-appareil")).isPresent();
    }

    @Test
    void les_seuils_viennent_de_la_configuration() {
        PointageLimiteurDebit strict = new PointageLimiteurDebit(horloge, 2, 2, 1, 1);
        assertThat(strict.verifier("9.9.9.9", "a")).isEmpty();
        assertThat(strict.verifier("9.9.9.9", "b")).isEmpty();
        assertThat(strict.verifier("9.9.9.9", "c")).as("3ᵉ appel, seuil IP = 2").isPresent();
        strict.signalerCodeInconnu("8.8.8.8", "z");
        assertThat(strict.verifier("8.8.8.8", "y")).as("bloqué dès le 1ᵉʳ code inconnu").isPresent();
        horloge.avancer(Duration.ofMinutes(1));
        assertThat(strict.verifier("8.8.8.8", "y")).as("blocage d'une minute expiré").isEmpty();
    }

    @Test
    void la_fenetre_est_glissante() {
        for (int i = 0; i < PAR_IP; i++) {
            limiteur.verifier("1.1.1.1", "a" + i);
        }
        assertThat(limiteur.verifier("1.1.1.1", "b")).isPresent();
        horloge.avancer(Duration.ofSeconds(61));
        assertThat(limiteur.verifier("1.1.1.1", "c")).isEmpty();
    }

    @Test
    void cinq_codes_inconnus_d_affilee_bloquent_quinze_minutes() {
        for (int i = 0; i < CODES_INCONNUS; i++) {
            limiteur.signalerCodeInconnu("3.3.3.3", "app");
        }
        horloge.avancer(Duration.ofMinutes(2)); // fenêtre d'appels vide : seul le blocage joue
        assertThat(limiteur.verifier("3.3.3.3", "autre")).as("IP bloquée").isPresent();
        assertThat(limiteur.verifier("4.4.4.4", "app")).as("appareil bloqué").isPresent();
        horloge.avancer(BLOCAGE);
        assertThat(limiteur.verifier("3.3.3.3", "autre")).as("blocage expiré").isEmpty();
    }

    @Test
    void un_pointage_reussi_remet_le_compteur_a_zero() {
        for (int i = 0; i < CODES_INCONNUS - 1; i++) {
            limiteur.signalerCodeInconnu("5.5.5.5", "app5");
        }
        limiteur.signalerSucces("5.5.5.5", "app5");
        limiteur.signalerCodeInconnu("5.5.5.5", "app5");
        assertThat(limiteur.verifier("5.5.5.5", "app5")).isEmpty();
    }
}
