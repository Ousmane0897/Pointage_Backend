package com.example.Pointage_Cleanic.security;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Témoin de {@link ProfilProdSecuriteIT} : hors prod, la documentation de l'API reste servie. */
class DocumentationDevSecuriteIT extends SecuriteITBase {

    @Test
    void documentation_api_servie_hors_prod() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
