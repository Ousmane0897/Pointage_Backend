package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C2 — {@code /api/superadmin} réservé au SUPERADMIN ; compte désactivé refusé. */
class GestionComptesSecuriteIT extends SecuriteITBase {

    @Autowired
    private MyUserDetailsService userDetailsService;

    private static final String CORPS = """
            {"prenom":"Awa","nom":"Ndiaye","email":"%s","password":"MotDePasse1","poste":"Agent",
             "role":"SUPERVISEUR","active":true,"modulesAutorises":{"dashboard":true}}""";

    @Test
    void le_role_superadmin_devient_une_autorite_spring() {
        jetonSuperAdmin();
        assertThat(userDetailsService.loadUserByUsername("superadmin.test@cleanic.sn").getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_SUPERADMIN");
    }

    @Test
    void sans_jeton_401() throws Exception {
        mockMvc.perform(get("/api/superadmin")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/superadmin").contentType(MediaType.APPLICATION_JSON)
                .content(CORPS.formatted("x@cleanic.sn"))).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/superadmin/{id}", "1").contentType(MediaType.APPLICATION_JSON)
                .content(CORPS.formatted("x@cleanic.sn"))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/superadmin/{id}", "1")).andExpect(status().isUnauthorized());
    }

    /** Un compte RH avec tous les drapeaux, même « admin », n'est pas super-admin. */
    @Test
    void compte_non_superadmin_403() throws Exception {
        String jeton = jetonUtilisateur("rh.test@cleanic.sn", RoleAdmin.RH,
                "{\"admin\":true,\"dashboard\":true}", true);
        mockMvc.perform(avec(get("/api/superadmin"), jeton))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCES_REFUSE"));
        mockMvc.perform(avec(post("/api/superadmin"), jeton).contentType(MediaType.APPLICATION_JSON)
                .content(CORPS.formatted("pirate@cleanic.sn"))).andExpect(status().isForbidden());
        mockMvc.perform(avec(put("/api/superadmin/{id}", "1"), jeton).contentType(MediaType.APPLICATION_JSON)
                .content(CORPS.formatted("pirate@cleanic.sn"))).andExpect(status().isForbidden());
        mockMvc.perform(avec(delete("/api/superadmin/{id}", "1"), jeton)).andExpect(status().isForbidden());
    }

    @Test
    void superadmin_gere_les_comptes() throws Exception {
        String jeton = jetonSuperAdmin();
        String email = "nouveau.compte@cleanic.sn";
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("email").is(email)), Utilisateur.class);
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("email").is(email)),
                com.example.Pointage_Cleanic.entities.User.class);

        mockMvc.perform(avec(post("/api/superadmin"), jeton).contentType(MediaType.APPLICATION_JSON)
                        .content(CORPS.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(avec(get("/api/superadmin"), jeton)).andExpect(status().isOk());

        String id = mongoTemplate.findOne(new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("email").is(email)), Utilisateur.class).getId();
        mockMvc.perform(avec(put("/api/superadmin/{id}", id), jeton).contentType(MediaType.APPLICATION_JSON)
                        .content(CORPS.formatted(email).replace("\"password\":\"MotDePasse1\"", "\"password\":\"\"")))
                .andExpect(status().isCreated());
        mockMvc.perform(avec(delete("/api/superadmin/{id}", id), jeton)).andExpect(status().isOk());
    }

    /** Le JWT (8 h) ne survit pas à une désactivation : refus immédiat, même sur une route sans drapeau. */
    @Test
    void compte_desactive_avec_jeton_valide_403() throws Exception {
        String jeton = jetonUtilisateur("desactive.test@cleanic.sn", RoleAdmin.RH,
                "{\"admin\":true}", false);
        mockMvc.perform(avec(get("/api/superadmin"), jeton))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("COMPTE_DESACTIVE"));
        mockMvc.perform(avec(get("/api/temps-presences/jours-feries"), jeton))
                .andExpect(status().isForbidden());
    }

    /** Compte supprimé après émission du jeton : 401 (et non 500). */
    @Test
    void compte_supprime_avec_jeton_valide_401() throws Exception {
        String jeton = jetonSansDroit();
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("email").is("sans.droit.test@cleanic.sn")),
                com.example.Pointage_Cleanic.entities.User.class);
        mockMvc.perform(avec(get("/api/superadmin"), jeton)).andExpect(status().isUnauthorized());
    }
}
