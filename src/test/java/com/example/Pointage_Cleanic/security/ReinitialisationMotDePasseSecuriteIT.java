package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.entities.ResetPasswordToken;
import com.example.Pointage_Cleanic.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C6 — mot de passe oublié de bout en bout, filtres actifs et vraie base. */
class ReinitialisationMotDePasseSecuriteIT extends SecuriteITBase {

    private static final String EMAIL = "reinit.test@cleanic.sn";
    private static final String CODE = "482913";

    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void compte() {
        jetonLogin(EMAIL, "SUPERVISEUR");
        mongoTemplate.remove(new Query(Criteria.where("email").is(EMAIL)), ResetPasswordToken.class);
    }

    private void poserCode() {
        ResetPasswordToken t = new ResetPasswordToken();
        t.setEmail(EMAIL);
        t.setToken(passwordEncoder.encode(CODE));
        t.setExpiration(LocalDateTime.now().plusMinutes(10));
        mongoTemplate.save(t);
    }

    private int reinitialiser(String email, String code) throws Exception {
        return mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"newPassword\":\"Nouveau123\"}"))
                .andReturn().getResponse().getStatus();
    }

    /** Pas d'énumération : réponse octet pour octet identique, compte connu ou non. */
    @Test
    void demande_meme_reponse_compte_connu_ou_inconnu() throws Exception {
        var connu = mockMvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\"}")).andReturn().getResponse();
        var inconnu = mockMvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"personne@cleanic.sn\"}")).andReturn().getResponse();

        assertThat(connu.getStatus()).isEqualTo(200);
        assertThat(inconnu.getStatus()).isEqualTo(200);
        assertThat(connu.getContentAsString()).isEqualTo(inconnu.getContentAsString());
        ResetPasswordToken stocke = mongoTemplate.findOne(new Query(Criteria.where("email").is(EMAIL)), ResetPasswordToken.class);
        assertThat(stocke).isNotNull();
        assertThat(stocke.getToken()).as("code stocké haché, jamais en clair").startsWith("$2");
    }

    @Test
    void code_valide_pour_son_email_seulement() throws Exception {
        poserCode();
        assertThat(reinitialiser("autre@cleanic.sn", CODE)).isEqualTo(400);
        assertThat(reinitialiser(EMAIL, CODE)).isEqualTo(200);
        User u = mongoTemplate.findOne(new Query(Criteria.where("email").is(EMAIL)), User.class);
        assertThat(passwordEncoder.matches("Nouveau123", u.getPassword())).isTrue();
        assertThat(reinitialiser(EMAIL, CODE)).as("code à usage unique").isEqualTo(400);
    }

    @Test
    void cinq_essais_puis_code_invalide_meme_correct() throws Exception {
        poserCode();
        for (int i = 0; i < 5; i++) {
            assertThat(reinitialiser(EMAIL, "000000")).isEqualTo(400);
        }
        assertThat(reinitialiser(EMAIL, CODE)).isEqualTo(400);
    }
}
