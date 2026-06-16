package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.UtilisateursService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UtilisateurController.class)
@AutoConfigureMockMvc(addFilters = false) // sécurité OFF
class UtilisateurControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // === Dépendances du controller ===
    @MockBean private UtilisateurRepository utilisateurRepository;
    @MockBean private UtilisateursService utilisateursService;
    @MockBean private LoginRepository loginRepository;
    @MockBean private PasswordEncoder passwordEncoder;

    // === Sécurité (OBLIGATOIRE en WebMvcTest)
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter; // 🔥 LE MANQUANT
    // =========================
    // POST /api/superadmin
    // =========================
    @Test
    void create_utilisateur_ok() throws Exception {

        Utilisateur u = new Utilisateur();
        u.setEmail("admin@test.com");
        u.setPassword("123456");
        u.setRole(RoleAdmin.SUPERVISEUR);

        when(utilisateurRepository.findAllByEmail("admin@test.com"))
                .thenReturn(List.of());
        when(loginRepository.findAllByEmail("admin@test.com"))
                .thenReturn(List.of());
        when(passwordEncoder.encode(any()))
                .thenReturn("ENCODED");

        mockMvc.perform(post("/api/superadmin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@test.com"));
    }

    // =========================
    // POST /api/superadmin (email existe)
    // =========================
    @Test
    void create_utilisateur_email_exists_409() throws Exception {

        Utilisateur u = new Utilisateur();
        u.setEmail("admin@test.com");

        when(utilisateurRepository.findAllByEmail("admin@test.com"))
                .thenReturn(List.of(new Utilisateur()));

        mockMvc.perform(post("/api/superadmin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(u)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_EXISTS"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("existe déjà")));
    }

    // =========================
    // GET /api/superadmin
    // =========================
    @Test
    void get_all_utilisateurs_ok() throws Exception {

        when(utilisateursService.getAll())
                .thenReturn(List.of(new Utilisateur(), new Utilisateur()));

        mockMvc.perform(get("/api/superadmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // =========================
    // GET /api/superadmin/{id}
    // =========================
    @Test
    void get_by_id_ok() throws Exception {

        Utilisateur u = new Utilisateur();
        u.setId("123");

        when(utilisateursService.getByid("123"))
                .thenReturn(u);

        mockMvc.perform(get("/api/superadmin/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("123"));
    }

    // =========================
    // GET /api/superadmin/{id} (404)
    // =========================
    @Test
    void get_by_id_not_found() throws Exception {

        when(utilisateursService.getByid("404"))
                .thenReturn(null);

        mockMvc.perform(get("/api/superadmin/404"))
                .andExpect(status().isNotFound());
    }

    // =========================
    // DELETE /api/superadmin/{id}
    // =========================
    @Test
    void delete_utilisateur_ok() throws Exception {

        Utilisateur u = new Utilisateur();
        u.setId("123");
        u.setEmail("admin@test.com");

        when(utilisateurRepository.findById("123"))
                .thenReturn(Optional.of(u));

        mockMvc.perform(delete("/api/superadmin/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        verify(loginRepository).deleteByEmail("admin@test.com");
        verify(utilisateurRepository).delete(u);
    }

    // =========================
    // PUT /api/superadmin/{id}
    // =========================
    @Test
    void update_utilisateur_ok() throws Exception {

        Utilisateur existing = new Utilisateur();
        existing.setId("123");

        when(utilisateursService.getByid("123"))
                .thenReturn(existing);
        when(utilisateursService.save(any()))
                .thenReturn(existing);

        mockMvc.perform(put("/api/superadmin/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existing)))
                .andExpect(status().isCreated());
    }

    // =========================
    // PUT /api/superadmin/{id} — resynchronise 'login' avec un hash encodé (pas de texte brut)
    // =========================
    @Test
    void update_utilisateur_resynchronise_login_avec_hash_encode() throws Exception {

        Utilisateur existing = new Utilisateur();
        existing.setId("123");
        existing.setEmail("admin@test.com");
        existing.setPassword("ANCIEN_HASH");

        com.example.Pointage_Cleanic.entities.User login =
                new com.example.Pointage_Cleanic.entities.User();
        login.setEmail("admin@test.com");
        login.setPassword("ANCIEN_HASH");
        login.setRole("SUPERVISEUR");

        when(utilisateursService.getByid("123")).thenReturn(existing);
        when(utilisateursService.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loginRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(login));
        when(passwordEncoder.encode("nouveauMotDePasse")).thenReturn("NOUVEAU_HASH");

        Utilisateur payload = new Utilisateur();
        payload.setEmail("admin@test.com");
        payload.setPassword("nouveauMotDePasse");
        payload.setRole(RoleAdmin.SUPERVISEUR);

        mockMvc.perform(put("/api/superadmin/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<com.example.Pointage_Cleanic.entities.User> captor =
                org.mockito.ArgumentCaptor.forClass(com.example.Pointage_Cleanic.entities.User.class);
        verify(loginRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("NOUVEAU_HASH", captor.getValue().getPassword());
    }

    // =========================
    // PUT /api/superadmin/{id} — mot de passe inchangé (hash réécho) : ne pas réencoder
    // =========================
    @Test
    void update_utilisateur_sans_changement_password_ne_reencode_pas() throws Exception {

        Utilisateur existing = new Utilisateur();
        existing.setId("123");
        existing.setEmail("admin@test.com");
        existing.setPassword("ANCIEN_HASH");

        com.example.Pointage_Cleanic.entities.User login =
                new com.example.Pointage_Cleanic.entities.User();
        login.setEmail("admin@test.com");
        login.setPassword("ANCIEN_HASH");

        when(utilisateursService.getByid("123")).thenReturn(existing);
        when(utilisateursService.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loginRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(login));

        // Le formulaire réécho le hash existant : ne doit PAS être réencodé.
        Utilisateur payload = new Utilisateur();
        payload.setEmail("admin@test.com");
        payload.setPassword("ANCIEN_HASH");

        mockMvc.perform(put("/api/superadmin/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        verify(passwordEncoder, never()).encode(any());
        org.mockito.ArgumentCaptor<com.example.Pointage_Cleanic.entities.User> captor =
                org.mockito.ArgumentCaptor.forClass(com.example.Pointage_Cleanic.entities.User.class);
        verify(loginRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("ANCIEN_HASH", captor.getValue().getPassword());
    }
}
