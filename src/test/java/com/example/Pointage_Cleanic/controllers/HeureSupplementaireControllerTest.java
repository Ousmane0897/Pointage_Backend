package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.HeureSupplementaireDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.HeureSupplementaireService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HeureSupplementaireController.class)
@AutoConfigureMockMvc(addFilters = false)
class HeureSupplementaireControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HeureSupplementaireService heureSupplementaireService;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private HeureSupplementaireDto buildDto(String id) {
        HeureSupplementaireDto dto = new HeureSupplementaireDto();
        dto.setId(id);
        dto.setEmployeId("emp-1");
        dto.setNombreHeures(2.5);
        return dto;
    }

    @Test
    void get_all_ok() throws Exception {
        when(heureSupplementaireService.getAll()).thenReturn(List.of(buildDto("hs-1")));

        mockMvc.perform(get("/api/heures-supplementaires"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("hs-1"));
    }

    @Test
    void get_by_id_ok() throws Exception {
        when(heureSupplementaireService.getById("hs-1")).thenReturn(buildDto("hs-1"));

        mockMvc.perform(get("/api/heures-supplementaires/hs-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeId").value("emp-1"))
                .andExpect(jsonPath("$.nombreHeures").value(2.5));
    }

    @Test
    void get_by_employe_ok() throws Exception {
        when(heureSupplementaireService.getByEmployeId("emp-1")).thenReturn(List.of(buildDto("hs-1")));

        mockMvc.perform(get("/api/heures-supplementaires/employe/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("hs-1"));
    }

    @Test
    void create_ok() throws Exception {
        when(heureSupplementaireService.create(any(HeureSupplementaireDto.class))).thenReturn(buildDto("hs-new"));

        mockMvc.perform(post("/api/heures-supplementaires")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("hs-new"));
    }

    @Test
    void update_ok() throws Exception {
        HeureSupplementaireDto input = buildDto("hs-1");
        when(heureSupplementaireService.update(eq("hs-1"), any(HeureSupplementaireDto.class))).thenReturn(input);

        mockMvc.perform(put("/api/heures-supplementaires/hs-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("hs-1"));
    }

    @Test
    void delete_ok() throws Exception {
        doNothing().when(heureSupplementaireService).delete("hs-1");

        mockMvc.perform(delete("/api/heures-supplementaires/hs-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void valider_ok() throws Exception {
        HeureSupplementaireDto validated = buildDto("hs-1");
        when(heureSupplementaireService.valider(eq("hs-1"), any(), any(), any())).thenReturn(validated);

        mockMvc.perform(put("/api/heures-supplementaires/hs-1/valider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("decideurId", "mgr-1", "decideurNom", "Seck", "commentaire", "Validé"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("hs-1"));
    }

    @Test
    void refuser_ok() throws Exception {
        HeureSupplementaireDto refused = buildDto("hs-1");
        when(heureSupplementaireService.refuser(eq("hs-1"), any(), any(), any())).thenReturn(refused);

        mockMvc.perform(put("/api/heures-supplementaires/hs-1/refuser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("decideurId", "mgr-1", "commentaire", "Refusé"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("hs-1"));
    }

    @Test
    void valider_sans_body_ok() throws Exception {
        HeureSupplementaireDto validated = buildDto("hs-1");
        when(heureSupplementaireService.valider(eq("hs-1"), isNull(), isNull(), isNull())).thenReturn(validated);

        mockMvc.perform(put("/api/heures-supplementaires/hs-1/valider"))
                .andExpect(status().isOk());
    }
}