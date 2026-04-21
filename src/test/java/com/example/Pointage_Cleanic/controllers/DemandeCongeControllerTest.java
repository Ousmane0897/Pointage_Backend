package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.DemandeCongeDto;
import com.example.Pointage_Cleanic.Dto.SoldeCongeDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.DemandeCongeService;
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

@WebMvcTest(DemandeCongeController.class)
@AutoConfigureMockMvc(addFilters = false)
class DemandeCongeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DemandeCongeService demandeCongeService;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private DemandeCongeDto buildDto(String id) {
        DemandeCongeDto dto = new DemandeCongeDto();
        dto.setId(id);
        dto.setEmployeId("emp-1");
        dto.setMotif("Congé annuel");
        return dto;
    }

    @Test
    void get_solde_ok() throws Exception {
        SoldeCongeDto solde = new SoldeCongeDto();
        solde.setSoldeDisponible(18);
        solde.setJoursPris(12);
        solde.setJoursEnAttente(0);

        when(demandeCongeService.getSolde("emp-1")).thenReturn(solde);

        mockMvc.perform(get("/api/conges/solde/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldeDisponible").value(18))
                .andExpect(jsonPath("$.joursPris").value(12));
    }

    @Test
    void get_all_ok() throws Exception {
        when(demandeCongeService.getAll()).thenReturn(List.of(buildDto("cg-1")));

        mockMvc.perform(get("/api/conges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cg-1"));
    }

    @Test
    void get_by_id_ok() throws Exception {
        when(demandeCongeService.getById("cg-1")).thenReturn(buildDto("cg-1"));

        mockMvc.perform(get("/api/conges/cg-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeId").value("emp-1"));
    }

    @Test
    void get_by_employe_ok() throws Exception {
        when(demandeCongeService.getByEmployeId("emp-1")).thenReturn(List.of(buildDto("cg-1")));

        mockMvc.perform(get("/api/conges/employe/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cg-1"));
    }

    @Test
    void create_ok() throws Exception {
        when(demandeCongeService.create(any(DemandeCongeDto.class))).thenReturn(buildDto("cg-new"));

        mockMvc.perform(post("/api/conges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("cg-new"));
    }

    @Test
    void update_ok() throws Exception {
        DemandeCongeDto input = buildDto("cg-1");
        when(demandeCongeService.update(eq("cg-1"), any(DemandeCongeDto.class))).thenReturn(input);

        mockMvc.perform(put("/api/conges/cg-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cg-1"));
    }

    @Test
    void delete_ok() throws Exception {
        doNothing().when(demandeCongeService).delete("cg-1");

        mockMvc.perform(delete("/api/conges/cg-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void approuver_ok() throws Exception {
        DemandeCongeDto approved = buildDto("cg-1");
        when(demandeCongeService.approuver(eq("cg-1"), any(), any(), any())).thenReturn(approved);

        mockMvc.perform(put("/api/conges/cg-1/approuver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("decideurId", "mgr-1", "decideurNom", "Diallo", "commentaire", "OK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cg-1"));
    }

    @Test
    void refuser_ok() throws Exception {
        DemandeCongeDto refused = buildDto("cg-1");
        when(demandeCongeService.refuser(eq("cg-1"), any(), any(), any())).thenReturn(refused);

        mockMvc.perform(put("/api/conges/cg-1/refuser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("decideurId", "mgr-1", "commentaire", "Refus"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cg-1"));
    }

    @Test
    void approuver_sans_body_ok() throws Exception {
        DemandeCongeDto approved = buildDto("cg-1");
        when(demandeCongeService.approuver(eq("cg-1"), isNull(), isNull(), isNull())).thenReturn(approved);

        mockMvc.perform(put("/api/conges/cg-1/approuver"))
                .andExpect(status().isOk());
    }
}