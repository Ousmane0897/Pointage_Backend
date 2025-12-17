package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.ResetPasswordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PasswordResetController.class)
@AutoConfigureMockMvc(addFilters = false)
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ✅ Dépendance métier du controller
    @MockBean
    private ResetPasswordService resetPasswordService;

    // ✅ Sécurité (obligatoire)
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    // =========================
    // FORGOT PASSWORD
    // =========================

    @Test
    void forgot_password_ok() throws Exception {
        Map<String, String> request = Map.of("email", "user@test.com");

        doNothing().when(resetPasswordService)
                .sendResetPasswordEmail("user@test.com");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Le Code de réinitialisation vous est envoyé par mail."));
    }

    @Test
    void forgot_password_runtime_exception() throws Exception {
        Map<String, String> request = Map.of("email", "unknown@test.com");

        doThrow(new RuntimeException("Utilisateur introuvable"))
                .when(resetPasswordService)
                .sendResetPasswordEmail("unknown@test.com");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void forgot_password_messaging_exception() throws Exception {
        Map<String, String> request = Map.of("email", "user@test.com");

        doThrow(new MessagingException("SMTP error"))
                .when(resetPasswordService)
                .sendResetPasswordEmail("user@test.com");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    // =========================
    // RESET PASSWORD
    // =========================

    @Test
    void reset_password_ok() throws Exception {
        Map<String, String> request = Map.of(
                "code", "123456",
                "newPassword", "newPass123"
        );

        doNothing().when(resetPasswordService)
                .resetPassword(request);

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Mot de passe réinitialisé avec succès"));
    }

    @Test
    void reset_password_invalid_code() throws Exception {
        Map<String, String> request = Map.of(
                "code", "000000",
                "newPassword", "newPass123"
        );

        doThrow(new RuntimeException("Code invalide"))
                .when(resetPasswordService)
                .resetPassword(request);

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Code invalide"));
    }
}
