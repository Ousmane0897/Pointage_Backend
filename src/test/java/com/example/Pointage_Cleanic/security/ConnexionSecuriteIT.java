package com.example.Pointage_Cleanic.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C1 — routes de connexion : seul {@code POST /api/login} reste public. */
class ConnexionSecuriteIT extends SecuriteITBase {

    @Test
    void post_login_reste_public() throws Exception {
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"inconnu@cleanic.sn\",\"password\":\"faux\"}"))
                .andExpect(status().is(not(403)))
                .andExpect(content().string(not(containsString("Authentification requise"))));
    }

    @Test
    void get_login_sans_jeton_401() throws Exception {
        mockMvc.perform(get("/api/login")).andExpect(status().isUnauthorized());
    }

    @Test
    void delete_login_sans_jeton_401() throws Exception {
        mockMvc.perform(delete("/api/login/{id}", "1")).andExpect(status().isUnauthorized());
    }

    /** Routes supprimées : même un super-admin ne peut plus lister ni supprimer par cette voie. */
    @Test
    void get_et_delete_login_supprimes() throws Exception {
        String jeton = jetonSuperAdmin();
        mockMvc.perform(avec(get("/api/login"), jeton)).andExpect(status().is(not(200)));
        mockMvc.perform(avec(delete("/api/login/{id}", "1"), jeton)).andExpect(status().is(not(200)));
    }

    @Test
    void change_password_sans_jeton_401() throws Exception {
        mockMvc.perform(post("/api/login/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@cleanic.sn\",\"oldPassword\":\"a\",\"newPassword\":\"bbbbbb\",\"confirmPassword\":\"bbbbbb\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Authentifié : la requête atteint le contrôleur (400 = ancien mot de passe faux). */
    @Test
    void change_password_avec_jeton_atteint_le_controleur() throws Exception {
        mockMvc.perform(avec(post("/api/login/change-password"), jetonSansDroit())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"faux\",\"newPassword\":\"bbbbbb\",\"confirmPassword\":\"bbbbbb\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_superadmin_ne_contient_aucun_mot_de_passe() throws Exception {
        jetonSansDroit(); // au moins un utilisateur en base
        mockMvc.perform(avec(get("/api/superadmin"), jetonSuperAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("password"))));
    }
}
