package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.BesoinFormationDto;
import com.example.Pointage_Cleanic.Enum.PrioriteBesoin;
import com.example.Pointage_Cleanic.Enum.SourceBesoin;
import com.example.Pointage_Cleanic.Enum.StatutBesoin;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.BesoinFormationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BesoinFormationController.class)
@AutoConfigureMockMvc(addFilters = false)
class BesoinFormationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private BesoinFormationService besoinFormationService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private BesoinFormationDto dto(String id) {
        return BesoinFormationDto.builder()
                .id(id)
                .employeId("emp-1")
                .departement("Nettoyage")
                .competenceLacune("Sécurité")
                .priorite(PrioriteBesoin.HAUTE)
                .source(SourceBesoin.MANAGER)
                .statut(StatutBesoin.IDENTIFIE)
                .build();
    }

    @Test
    void create_ok() throws Exception {
        when(besoinFormationService.create(any())).thenReturn(dto("b-1"));

        mockMvc.perform(post("/api/besoins-formation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("b-1"))
                .andExpect(jsonPath("$.priorite").value("HAUTE"));
    }

    @Test
    void search_ok() throws Exception {
        when(besoinFormationService.search(any(), any(), any()))
                .thenReturn(List.of(dto("b-1")));

        mockMvc.perform(get("/api/besoins-formation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("b-1"));
    }

    @Test
    void get_by_employe_ok() throws Exception {
        when(besoinFormationService.getByEmploye("emp-1"))
                .thenReturn(List.of(dto("b-1")));

        mockMvc.perform(get("/api/besoins-formation/employe/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeId").value("emp-1"));
    }

    @Test
    void changer_statut_ok() throws Exception {
        BesoinFormationDto planifie = dto("b-1");
        planifie.setStatut(StatutBesoin.PLANIFIE);
        when(besoinFormationService.changerStatut(eq("b-1"), eq(StatutBesoin.PLANIFIE)))
                .thenReturn(planifie);

        mockMvc.perform(put("/api/besoins-formation/b-1/statut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("statut", "PLANIFIE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PLANIFIE"));
    }
}
