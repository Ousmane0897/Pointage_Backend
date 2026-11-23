package com.example.Pointage_Cleanic.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C7 — les appels aux routes en sursis sont comptés, sans donnée personnelle ; lecture SUPERADMIN. */
class SuiviRoutesLegacyIT extends SecuriteITBase {

    @BeforeEach
    void vider() {
        mongoTemplate.dropCollection(AppelRouteLegacy.class);
    }

    @Test
    void image_employe_comptee_sans_le_code_de_l_agent() throws Exception {
        mockMvc.perform(get("/api/employe-complet/image/{id}", "4321").header(HttpHeaders.USER_AGENT, "okhttp/4.9"));
        mockMvc.perform(get("/api/employe-complet/image/{id}", "8765"));

        List<AppelRouteLegacy> appels = mongoTemplate.findAll(AppelRouteLegacy.class);
        assertThat(appels).hasSize(1);
        AppelRouteLegacy a = appels.get(0);
        assertThat(a.getRoute()).isEqualTo("GET /api/employe-complet/image/**");
        assertThat(a.getCompteur()).isEqualTo(2);
        assertThat(a.getPremierAppel()).isNotNull();
        assertThat(a.toString()).doesNotContain("4321", "8765");
    }

    @Test
    void appel_refuse_compte_avec_son_code() throws Exception {
        mockMvc.perform(post("/api/terrain/pointages").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        AppelRouteLegacy a = mongoTemplate.findAll(AppelRouteLegacy.class).get(0);
        assertThat(a.getRoute()).isEqualTo("POST /api/terrain/pointages");
        assertThat(a.getStatuts()).containsEntry("401", 1L);
    }

    @Test
    void les_autres_routes_ne_sont_pas_comptees() throws Exception {
        mockMvc.perform(get("/api/terrain/pointages/historique"));
        mockMvc.perform(get("/api/produits/all"));

        assertThat(mongoTemplate.findAll(AppelRouteLegacy.class)).isEmpty();
    }

    @Test
    void lecture_reservee_au_superadmin() throws Exception {
        mockMvc.perform(get("/api/produits/image/{code}", "P001"));

        mockMvc.perform(get("/api/securite/routes-legacy")).andExpect(status().isUnauthorized());
        mockMvc.perform(avec(get("/api/securite/routes-legacy"), jetonSansDroit()))
                .andExpect(status().isForbidden());
        mockMvc.perform(avec(get("/api/securite/routes-legacy"), jetonSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].route").value("GET /api/produits/image/**"))
                .andExpect(jsonPath("$[0].compteur").value(1));
    }
}
