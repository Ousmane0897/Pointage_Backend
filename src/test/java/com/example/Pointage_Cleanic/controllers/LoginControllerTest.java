package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.AuthRequest;
import com.example.Pointage_Cleanic.Dto.ChangePasswordRequest;
import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.UserRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.LoginService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.rh.CongeModuleEnricher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginController.class)
@AutoConfigureMockMvc(addFilters = false) // 🔥 Désactive Spring Security
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // === Dépendances DIRECTES du controller ===
    @MockBean private AuthenticationManager authenticationManager;
    @MockBean private LoginService loginService;
    @MockBean private LoginRepository loginRepository;
    @MockBean private UserRepository userRepository;
    @MockBean private UtilisateurRepository utilisateurRepository;
    @MockBean private PasswordEncoder passwordEncoder;
    // Enrichit modules.rh.conges* à l'émission du token (droits de validation des congés).
    @MockBean private CongeModuleEnricher congeModuleEnricher;

    // ✅ Sécurité
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;


    // ===================== LOGIN =====================

    @Test
    void login_ok() throws Exception {

        AuthRequest request = new AuthRequest("user@test.com", "password");

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail("user@test.com");
        utilisateur.setPrenom("Ali");
        utilisateur.setNom("Diallo");
        utilisateur.setRole(RoleAdmin.SUPERVISEUR);

        when(authenticationManager.authenticate(any()))
                .thenReturn(mock(org.springframework.security.core.Authentication.class));

        when(myUserDetailsService.loadUserByUsername("user@test.com"))
                .thenReturn(mock(org.springframework.security.core.userdetails.UserDetails.class));

        when(utilisateurRepository.findByEmail("user@test.com"))
                .thenReturn(Optional.of(utilisateur));

        when(jwtUtil.generateToken(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn("FAKE_JWT");

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("FAKE_JWT"));
    }


    @Test
    void login_bad_credentials() throws Exception {

        AuthRequest request = new AuthRequest("user@test.com", "wrong");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }


    // ===================== CHANGE PASSWORD =====================

    @Test
    void change_password_ok() throws Exception {

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setEmail("user@test.com");
        request.setOldPassword("old");
        request.setNewPassword("new123");
        request.setConfirmPassword("new123");

        User user = new User();
        user.setPassword("ENC_OLD");

        when(userRepository.findByEmail("user@test.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("old", "ENC_OLD")).thenReturn(true);
        when(passwordEncoder.encode("new123")).thenReturn("ENC_NEW");
        when(jwtUtil.generateToken2(any(), any(), any(), anyBoolean()))
                .thenReturn("NEW_JWT");

        mockMvc.perform(post("/api/login/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("NEW_JWT"));
    }

    @Test
    void change_password_old_password_wrong() throws Exception {

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setEmail("user@test.com");
        request.setOldPassword("wrong");
        request.setNewPassword("new123");
        request.setConfirmPassword("new123");

        User user = new User();
        user.setPassword("ENC_OLD");

        when(userRepository.findByEmail("user@test.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        mockMvc.perform(post("/api/login/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ===================== GET =====================

    @Test
    void get_all_users() throws Exception {

        when(loginRepository.findAll())
                .thenReturn(List.of(new User(), new User()));

        mockMvc.perform(get("/api/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ===================== DELETE =====================

    @Test
    void delete_user_ok() throws Exception {

        User user = new User();
        when(loginService.getById("1")).thenReturn(user);

        mockMvc.perform(delete("/api/login/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Deleted").value(true));

        verify(loginRepository).delete(user);
    }

    @Test
    void delete_user_not_found() throws Exception {

        when(loginService.getById("99")).thenReturn(null);

        mockMvc.perform(delete("/api/login/99"))
                .andExpect(status().isNotFound());
    }


}
