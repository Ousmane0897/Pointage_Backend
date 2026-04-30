package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.AutoEvaluationRequest;
import com.example.Pointage_Cleanic.Dto.EvaluationManagerRequest;
import com.example.Pointage_Cleanic.Dto.EvaluationPeriodiqueDto;
import com.example.Pointage_Cleanic.Dto.ValidationEvaluationRequest;
import com.example.Pointage_Cleanic.Enum.StatutEvaluation;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.EvaluationPeriodiqueService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvaluationPeriodiqueController.class)
@AutoConfigureMockMvc(addFilters = false)
class EvaluationPeriodiqueControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private EvaluationPeriodiqueService evaluationService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private EvaluationPeriodiqueDto dto(String id, StatutEvaluation statut) {
        return EvaluationPeriodiqueDto.builder()
                .id(id)
                .employeId("emp-1")
                .periode("2026")
                .statut(statut)
                .build();
    }

    @Test
    void create_ok() throws Exception {
        when(evaluationService.create(any())).thenReturn(dto("ev-1", StatutEvaluation.BROUILLON));

        mockMvc.perform(post("/api/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto(null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ev-1"))
                .andExpect(jsonPath("$.statut").value("BROUILLON"));
    }

    @Test
    void search_ok() throws Exception {
        when(evaluationService.search(any(), any(), any(), any()))
                .thenReturn(List.of(dto("ev-1", StatutEvaluation.VALIDE)));

        mockMvc.perform(get("/api/evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ev-1"));
    }

    @Test
    void auto_evaluer_ok() throws Exception {
        when(evaluationService.autoEvaluer(eq("ev-1"), any(AutoEvaluationRequest.class)))
                .thenReturn(dto("ev-1", StatutEvaluation.AUTO_EVALUATION));

        mockMvc.perform(put("/api/evaluations/ev-1/auto-evaluer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AutoEvaluationRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("AUTO_EVALUATION"));
    }

    @Test
    void evaluer_manager_ok() throws Exception {
        when(evaluationService.evaluerManager(eq("ev-1"), any(EvaluationManagerRequest.class)))
                .thenReturn(dto("ev-1", StatutEvaluation.EVALUATION_MANAGER));

        mockMvc.perform(put("/api/evaluations/ev-1/evaluer-manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EvaluationManagerRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EVALUATION_MANAGER"));
    }

    @Test
    void valider_ok() throws Exception {
        when(evaluationService.valider(eq("ev-1"), any(ValidationEvaluationRequest.class)))
                .thenReturn(dto("ev-1", StatutEvaluation.VALIDE));

        mockMvc.perform(put("/api/evaluations/ev-1/valider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidationEvaluationRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));
    }
}
