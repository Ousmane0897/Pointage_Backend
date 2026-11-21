package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C6 — profil {@code prod} : Swagger et {@code /v3/api-docs} fermés. Les secrets requis par
 * {@code SecretsRequisVerificateur} sont fournis avec des valeurs factices.
 */
@ActiveProfiles("prod")
@SpringBootTest(properties = {
        "JWT_SECRET=valeur_factice_de_test_au_moins_32_caracteres",
        "MAIL_USERNAME=factice@exemple.sn",
        "MAIL_PASSWORD=factice",
        "GOOGLE_MAPS_API_KEY=factice",
        "spring.mail.host=localhost",
        "spring.mail.port=25"
})
@AutoConfigureMockMvc
class ProfilProdSecuriteIT extends MongoTestContainer {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentation_api_fermee_en_prod() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
