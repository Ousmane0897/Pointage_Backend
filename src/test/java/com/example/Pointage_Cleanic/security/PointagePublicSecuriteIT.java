package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C5 — plus de lecture publique du dernier pointage ; POST public minimal et limité. */
class PointagePublicSecuriteIT extends SecuriteITBase {

    /** Une IP distincte par test : le limiteur est un singleton partagé par le contexte. */
    private static RequestPostProcessor ip(String ip) {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    private static MockHttpServletRequestBuilder pointer(String code, String appareil, String ip) {
        return post("/api/pointages").with(ip(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codeSecret\":\"" + code + "\",\"deviceId\":\"" + appareil
                        + "\",\"latitude\":14.7,\"longitude\":-17.4}");
    }

    @Test
    void lecture_du_dernier_pointage_par_code_n_est_plus_publique() throws Exception {
        mockMvc.perform(get("/api/pointages/{code}", "1234")).andExpect(status().isUnauthorized());
    }

    @Test
    void la_reponse_du_pointage_ne_contient_que_l_affichage() throws Exception {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("agentId").is("8642")), DossierEmploye.class);
        DossierEmploye agent = new DossierEmploye();
        agent.setAgentId("8642");
        agent.setNom("NomDeFamilleFactice");
        agent.setPrenom("Aminata");
        agent.setAdresse("Adresse factice");
        mongoTemplate.save(agent);

        mockMvc.perform(pointer("8642", "appareil-c5-reponse", "10.50.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prenom").value("Aminata"))
                .andExpect(jsonPath("$.heureArrive").exists())
                .andExpect(jsonPath("$.nom").doesNotExist())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.longitude").doesNotExist())
                .andExpect(jsonPath("$.adresse").doesNotExist())
                .andExpect(jsonPath("$.deviceId").doesNotExist())
                .andExpect(jsonPath("$.codeSecret").doesNotExist())
                .andExpect(content().string(not(containsString("NomDeFamilleFactice"))));
    }

    /**
     * Un site fait pointer 30 agents (30 appareils) depuis la même IP en une minute : aucun 429.
     * Un agent par appel, pour que chaque pointage soit un vrai succès (arrivée).
     */
    @Test
    void trente_pointages_valides_meme_ip_sans_429() throws Exception {
        String ipSite = "10.50.0.3";
        for (int i = 0; i < 30; i++) {
            String code = String.format("75%02d", i);
            mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(
                    org.springframework.data.mongodb.core.query.Criteria.where("agentId").is(code)), DossierEmploye.class);
            DossierEmploye agent = new DossierEmploye();
            agent.setAgentId(code);
            agent.setPrenom("Agent" + i);
            mongoTemplate.save(agent);

            mockMvc.perform(pointer(code, "appareil-c5-site-" + i, ipSite))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Même appareil, IP différente à chaque appel, agent connu : le 1ᵉʳ appel pointe, les 2ᵉ à
     * 10ᵉ sont refusés par {@code canPoint} (appareil récemment utilisé, sans compter comme code
     * inconnu), le 11ᵉ est refusé par le limiteur — d'où la vérification de son message propre.
     */
    @Test
    void onzieme_appel_du_meme_appareil_refuse_par_le_limiteur() throws Exception {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("agentId").is("7600")), DossierEmploye.class);
        DossierEmploye agent = new DossierEmploye();
        agent.setAgentId("7600");
        agent.setPrenom("Fatou");
        mongoTemplate.save(agent);

        for (int i = 0; i < 10; i++) {
            String corps = mockMvc.perform(pointer("7600", "appareil-c5-unique", "10.51.0." + i))
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            org.assertj.core.api.Assertions.assertThat(corps).as("appel " + (i + 1))
                    .doesNotContain("Trop de tentatives");
        }
        mockMvc.perform(pointer("7600", "appareil-c5-unique", "10.51.1.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("Trop de tentatives")));
    }

    @Test
    void cinq_codes_inconnus_d_affilee_bloquent() throws Exception {
        String ipBlocage = "10.50.0.4";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(pointer("000" + i, "appareil-c5-blocage-" + i, ipBlocage)).andExpect(status().isNotFound());
        }
        mockMvc.perform(pointer("0009", "appareil-c5-blocage-final", ipBlocage)).andExpect(status().isTooManyRequests());
    }
}
