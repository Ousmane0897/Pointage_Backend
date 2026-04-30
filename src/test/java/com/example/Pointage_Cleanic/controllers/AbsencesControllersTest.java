package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Absent;
import com.example.Pointage_Cleanic.repositories.AbsentRepository;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.AbsentService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// IMPORTANT: Les tests sont valables pour n’importe quel rôle

@WebMvcTest(AbsencesControllers.class)
@AutoConfigureMockMvc(addFilters = false) // 🔥 Sécurité désactivée
class AbsencesControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AbsentService absentService;

    @MockBean private AbsentRepository absentRepository;

    // ✅ Sécurité
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------------------------------------------------
    // 🔹 GET /api/absences/temps-reel
    // ---------------------------------------------------

    @Test
    void getDynamicAbsences_OK() throws Exception {

        when(absentService.findAbsencesDynamiques()).thenReturn(List.of());

        mockMvc.perform(get("/api/absences/temps-reel"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // ---------------------------------------------------
    // 🔹 PUT /api/absences/{codeSecret} — SUCCESS
    // ---------------------------------------------------

    @Test
    void updateAbsent_OK() throws Exception {

        Absent existing = new Absent();
        existing.setCodeSecret("123");

        when(absentService.getBycodeSecret("123")).thenReturn(existing);
        when(absentRepository.save(any(Absent.class))).thenReturn(existing);

        Absent request = new Absent();
        request.setPrenom("Ali");
        request.setNom("Diop");
        request.setNumero("770000000");
        request.setDateAbsence(java.time.LocalDate.of(2025, 1, 1));
        request.setMotif("Maladie");
        request.setJustification("Certificat médical");

        mockMvc.perform(put("/api/absences/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------
    // 🔹 PUT /api/absences/{codeSecret} — NOT FOUND
    // ---------------------------------------------------

    @Test
    void updateAbsent_notFound_404() throws Exception {

        when(absentService.getBycodeSecret("999")).thenReturn(null);

        mockMvc.perform(put("/api/absences/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
