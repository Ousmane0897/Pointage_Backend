package com.example.Pointage_Cleanic.controllers.terrain;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaîne de filtres réelle (contrairement au slice {@code PlanningControllerTest} qui la désactive) :
 * vérifie que les routes d'annulation et de compteurs sont bien couvertes par la règle
 * {@code /api/terrain/** → authenticated()} de {@code SecurityConfig}.
 *
 * <p><b>Codes renvoyés</b>, communs à toutes les routes protégées du backend depuis l'ajout de
 * l'{@code authenticationEntryPoint} global (2026-07-21) :
 * <ul>
 *   <li><b>Aucun header {@code Authorization}</b> → <b>401</b> (avant : 403, défaut
 *       {@code Http403ForbiddenEntryPoint} de Spring Security).</li>
 *   <li><b>Token présent mais invalide / expiré</b> → <b>401</b>, émis par
 *       {@code JwtRequestFilter} ({@code sendError(SC_UNAUTHORIZED)}).</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
@AutoConfigureMockMvc
class PlanningSecuriteIT extends MongoTestContainer {

    @Autowired
    private MockMvc mockMvc;

    private static final String ANNULER = "/api/terrain/planning/affectations/{id}/annuler";
    private static final String STATS = "/api/terrain/planning/affectations/stats";
    private static final String BODY = "{\"motif\":\"Client a reporté l'intervention\"}";

    @Test
    void annuler_sans_token_renvoie_401() throws Exception {
        mockMvc.perform(post(ANNULER, "a1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void annuler_avec_token_invalide_renvoie_401() throws Exception {
        mockMvc.perform(post(ANNULER, "a1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer pas-un-vrai-jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stats_sans_token_renvoie_401() throws Exception {
        mockMvc.perform(get(STATS)).andExpect(status().isUnauthorized());
    }

    /** Une route publique reste publique : l'entry point ne doit pas déborder. */
    @Test
    void une_route_publique_reste_accessible_sans_token() throws Exception {
        mockMvc.perform(get("/api/pointages/{codeSecret}", "0000"))
                .andExpect(status().is(not(401)));
    }

    @Test
    void stats_avec_token_invalide_renvoie_401() throws Exception {
        mockMvc.perform(get(STATS).header(HttpHeaders.AUTHORIZATION, "Bearer pas-un-vrai-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
