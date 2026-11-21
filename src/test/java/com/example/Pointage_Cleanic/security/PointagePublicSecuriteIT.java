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

    /** Code connu, appareils distincts, même IP : les 10 premiers passent, le 11ᵉ est refusé. */
    @Test
    void onzieme_appel_dans_la_minute_429() throws Exception {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("agentId").is("7531")), DossierEmploye.class);
        DossierEmploye agent = new DossierEmploye();
        agent.setAgentId("7531");
        agent.setPrenom("Ibrahima");
        mongoTemplate.save(agent);

        String ipDebit = "10.50.0.3";
        for (int i = 0; i < 10; i++) {
            int statut = mockMvc.perform(pointer("7531", "appareil-c5-debit-" + i, ipDebit))
                    .andReturn().getResponse().getStatus();
            org.assertj.core.api.Assertions.assertThat(statut).as("appel " + (i + 1)).isNotEqualTo(429);
        }
        mockMvc.perform(pointer("7531", "appareil-c5-debit-final", ipDebit)).andExpect(status().isTooManyRequests());
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
