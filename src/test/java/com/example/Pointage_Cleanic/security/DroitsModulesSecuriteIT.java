package com.example.Pointage_Cleanic.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * C4 — une route représentative par contrôleur (hors paie et dossiers, testés à part) :
 * 401 sans jeton, 403 sans droit et avec un drapeau voisin, accès avec le bon drapeau.
 */
class DroitsModulesSecuriteIT extends SecuriteITBase {

    static Stream<Arguments> routes() {
        return Stream.of(
                // --- RH (reste) ---
                a("GET", "/api/gestion-personnel/documents", "{\"rh\":{\"documents\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/gestion-personnel/organigramme", "{\"rh\":{\"organigramme\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/temps-presences/pointages", "{\"rh\":{\"pointageCentralise\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/temps-presences/recapitulatif?mois=1&annee=2026", "{\"rh\":{\"recapitulatif\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/temps-presences/heures-supplementaires", "{\"rh\":{\"heuresSupplementaires\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/temps-presences/absences", "{\"rh\":{\"absences\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/developpement-rh/formations", "{\"rh\":{\"formations\":true}}", "{\"rh\":{\"sanctions\":true}}"),
                a("GET", "/api/developpement-rh/formations/besoins", "{\"rh\":{\"formations\":true}}", "{\"rh\":{\"sanctions\":true}}"),
                a("GET", "/api/developpement-rh/evaluations", "{\"rh\":{\"evaluations\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/developpement-rh/sanctions", "{\"rh\":{\"sanctions\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/developpement-rh/tableau-bord", "{\"rh\":{\"tableauBordRh\":true}}", "{\"rh\":{\"formations\":true}}")
        );
    }

    private static Arguments a(String methode, String url, String autorise, String refuse) {
        return Arguments.of(methode, url, autorise, refuse);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("routes")
    void droit_exige(String methode, String url, String autorise, String refuse) throws Exception {
        verifierAcces(() -> request(HttpMethod.valueOf(methode), url)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                autorise, refuse, "{\"dashboard\":true}");
    }

    /**
     * Congés et jours fériés : libre-service ouvert à tout compte connecté (« Mes demandes »,
     * solde, consultation des fériés) ; le périmètre est filtré par CongeIdentiteService.
     */
    @org.junit.jupiter.api.Test
    void conges_et_feries_restent_en_libre_service() throws Exception {
        String jeton = jetonSansDroit();
        for (String url : new String[]{"/api/temps-presences/conges/moi",
                "/api/temps-presences/conges/demandes/mes-demandes", "/api/temps-presences/jours-feries"}) {
            int statut = mockMvc.perform(avec(request(HttpMethod.GET, url), jeton)).andReturn().getResponse().getStatus();
            org.assertj.core.api.Assertions.assertThat(statut).as(url).isNotIn(401, 403);
        }
    }
}
