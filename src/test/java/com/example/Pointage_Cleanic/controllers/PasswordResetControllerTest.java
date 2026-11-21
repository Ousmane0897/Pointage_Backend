package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.ResetPasswordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordResetController.class)
@AutoConfigureMockMvc(addFilters = false)
class PasswordResetControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ResetPasswordService resetPasswordService;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    /** Réponse identique que l'e-mail existe ou non (le service ne lève rien). */
    @Test
    void forgot_password_reponse_neutre() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "inconnu@test.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(PasswordResetController.MESSAGE_DEMANDE));
        verify(resetPasswordService).sendResetPasswordEmail("inconnu@test.com");
    }

    @Test
    void reset_password_ok() throws Exception {
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "a@test.com", "code", "123456", "newPassword", "nouveau1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mot de passe réinitialisé avec succès"));
        verify(resetPasswordService).resetPassword("a@test.com", "123456", "nouveau1");
    }

    @Test
    void reset_password_code_invalide_400() throws Exception {
        doThrow(new IllegalArgumentException("Code invalide ou expiré."))
                .when(resetPasswordService).resetPassword("a@test.com", "000000", "nouveau1");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "a@test.com", "code", "000000", "newPassword", "nouveau1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Code invalide ou expiré."))
                .andExpect(jsonPath("$.message").value("Code invalide ou expiré."));
    }
}
