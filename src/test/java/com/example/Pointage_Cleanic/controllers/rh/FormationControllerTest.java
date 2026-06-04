package com.example.Pointage_Cleanic.controllers.rh;

import com.example.Pointage_Cleanic.Dto.rh.EvaluationFormationDto;
import com.example.Pointage_Cleanic.Dto.rh.FormationDto;
import com.example.Pointage_Cleanic.Dto.rh.ParticipationFormationDto;
import com.example.Pointage_Cleanic.Dto.rh.SessionFormationDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutSession;
import com.example.Pointage_Cleanic.Enum.rh.TypeFormateur;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.rh.FormationService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FormationController.class)
@AutoConfigureMockMvc(addFilters = false)
class FormationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private FormationService formationService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void create_formation_ok() throws Exception {
        FormationDto dto = FormationDto.builder()
                .titre("Sécurité")
                .dureeHeures(8)
                .typeFormateur(TypeFormateur.INTERNE)
                .coutFcfa(0L)
                .actif(true)
                .build();
        when(formationService.create(any())).thenReturn(FormationDto.builder().id("f-1").titre("Sécurité").build());

        mockMvc.perform(post("/api/formations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("f-1"))
                .andExpect(jsonPath("$.titre").value("Sécurité"));
    }

    @Test
    void search_ok() throws Exception {
        when(formationService.search(any(), any()))
                .thenReturn(List.of(FormationDto.builder().id("f-1").build()));

        mockMvc.perform(get("/api/formations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("f-1"));
    }

    @Test
    void get_by_id_ok() throws Exception {
        when(formationService.getById("f-1"))
                .thenReturn(FormationDto.builder().id("f-1").titre("Sécurité").build());

        mockMvc.perform(get("/api/formations/f-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Sécurité"));
    }

    @Test
    void update_ok() throws Exception {
        FormationDto dto = FormationDto.builder().titre("Sécurité v2").build();
        when(formationService.update(eq("f-1"), any())).thenReturn(dto);

        mockMvc.perform(put("/api/formations/f-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Sécurité v2"));
    }

    @Test
    void delete_ok() throws Exception {
        doNothing().when(formationService).delete("f-1");

        mockMvc.perform(delete("/api/formations/f-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void add_session_ok() throws Exception {
        SessionFormationDto dto = SessionFormationDto.builder()
                .lieu("Dakar").capaciteMax(20).build();
        when(formationService.addSession(eq("f-1"), any()))
                .thenReturn(SessionFormationDto.builder().id("s-1").statut(StatutSession.PLANIFIEE).build());

        mockMvc.perform(post("/api/formations/f-1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("s-1"))
                .andExpect(jsonPath("$.statut").value("PLANIFIEE"));
    }

    @Test
    void list_sessions_ok() throws Exception {
        when(formationService.listSessions("f-1"))
                .thenReturn(List.of(SessionFormationDto.builder().id("s-1").build()));

        mockMvc.perform(get("/api/formations/f-1/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("s-1"));
    }

    @Test
    void add_participant_ok() throws Exception {
        ParticipationFormationDto dto = ParticipationFormationDto.builder()
                .employeId("emp-1").build();
        when(formationService.addParticipant(eq("s-1"), any()))
                .thenReturn(ParticipationFormationDto.builder().id("p-1").employeId("emp-1").build());

        mockMvc.perform(post("/api/formations/sessions/s-1/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("p-1"));
    }

    @Test
    void marquer_presence_ok() throws Exception {
        when(formationService.marquerPresence(eq("p-1"), eq(true)))
                .thenReturn(ParticipationFormationDto.builder().id("p-1").present(true).build());

        mockMvc.perform(put("/api/formations/participations/p-1/presence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("present", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(true));
    }

    @Test
    void add_evaluation_ok() throws Exception {
        EvaluationFormationDto dto = EvaluationFormationDto.builder()
                .employeId("emp-1").note(4).build();
        when(formationService.addEvaluation(eq("s-1"), any()))
                .thenReturn(EvaluationFormationDto.builder().id("e-1").note(4).build());

        mockMvc.perform(post("/api/formations/sessions/s-1/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value(4));
    }
}
