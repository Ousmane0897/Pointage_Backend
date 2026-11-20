package com.example.Pointage_Cleanic.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** C3 — en prod, un secret manquant bloque le démarrage avec un message qui le nomme. */
class SecretsRequisVerificateurTest {

    private static MockEnvironment prodComplet() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        SecretsRequisVerificateur.SECRETS_REQUIS_PROD.forEach(n -> env.setProperty(n, "valeur-factice"));
        return env;
    }

    @Test
    void prod_complet_demarre() {
        assertThatCode(() -> SecretsRequisVerificateur.verifier(prodComplet())).doesNotThrowAnyException();
    }

    @Test
    void prod_variable_manquante_refuse_et_la_nomme() {
        MockEnvironment env = prodComplet();
        env.setProperty("JWT_SECRET", "");
        MockEnvironment sansMail = new MockEnvironment();
        sansMail.setActiveProfiles("prod");
        sansMail.setProperty("JWT_SECRET", "x");
        sansMail.setProperty("GOOGLE_MAPS_API_KEY", "x");

        assertThatThrownBy(() -> SecretsRequisVerificateur.verifier(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageNotContaining("valeur-factice");
        assertThatThrownBy(() -> SecretsRequisVerificateur.verifier(sansMail))
                .hasMessageContaining("MAIL_USERNAME")
                .hasMessageContaining("MAIL_PASSWORD");
    }

    @Test
    void hors_prod_aucune_verification() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        assertThatCode(() -> SecretsRequisVerificateur.verifier(dev)).doesNotThrowAnyException();
        assertThatCode(() -> SecretsRequisVerificateur.verifier(new MockEnvironment())).doesNotThrowAnyException();
    }

    @Test
    void enregistre_dans_spring_factories() throws Exception {
        try (var in = getClass().getResourceAsStream("/META-INF/spring.factories")) {
            assertThat(new String(in.readAllBytes())).contains(SecretsRequisVerificateur.class.getName());
        }
    }
}
