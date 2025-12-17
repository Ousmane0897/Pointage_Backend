package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Ferie;
import com.example.Pointage_Cleanic.repositories.FerieRepository;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.FerieService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FerieController.class)
@AutoConfigureMockMvc(addFilters = false) // 🔥 Sécurité désactivée
class FerieControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ===== Dépendances du controller =====
    @MockBean private FerieService ferieService;
    @MockBean private FerieRepository ferieRepository;

    // ✅ Sécurité
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    // =========================
    // POST /api/ferie
    // =========================
    @Test
    void save_ferie_ok() throws Exception {

        Ferie ferie = new Ferie();
        ferie.setNom("Korité");
        ferie.setDate("10.04.2024");

        when(ferieService.save(any())).thenReturn(ferie);

        mockMvc.perform(post("/api/ferie")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ferie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Korité"))
                .andExpect(jsonPath("$.date").value("10.04.2024"));
    }

    // =========================
    // GET /api/ferie
    // =========================
    @Test
    void get_all_feries_ok() throws Exception {

        when(ferieService.getAll())
                .thenReturn(List.of(new Ferie(), new Ferie()));

        mockMvc.perform(get("/api/ferie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // =========================
    // PUT /api/ferie/{date}
    // =========================
    @Test
    void update_ferie_ok() throws Exception {

        Ferie existing = new Ferie();
        existing.setNom("Ancien");
        existing.setDate("10.04.2024");

        Ferie updated = new Ferie();
        updated.setNom("Korité");
        updated.setDate("10.04.2024");

        when(ferieService.getById("10.04.2024"))
                .thenReturn(existing);
        when(ferieService.save(any()))
                .thenReturn(updated);

        mockMvc.perform(put("/api/ferie/10.04.2024")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Korité"));
    }

    @Test
    void update_ferie_not_found() throws Exception {

        when(ferieService.getById("01.01.2025"))
                .thenReturn(null);

        mockMvc.perform(put("/api/ferie/01.01.2025")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // =========================
    // DELETE /api/ferie/{date}
    // =========================
    @Test
    void delete_ferie_ok() throws Exception {

        Ferie ferie = new Ferie();

        when(ferieService.getById("10.04.2024"))
                .thenReturn(ferie);

        mockMvc.perform(delete("/api/ferie/10.04.2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Deleted").value(true));

        verify(ferieRepository).delete(ferie);
    }

    @Test
    void delete_ferie_not_found() throws Exception {

        when(ferieService.getById("01.01.2025"))
                .thenReturn(null);

        mockMvc.perform(delete("/api/ferie/01.01.2025"))
                .andExpect(status().isNotFound());
    }
}
