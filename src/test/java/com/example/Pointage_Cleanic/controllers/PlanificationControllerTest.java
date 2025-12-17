package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.*;
import com.example.Pointage_Cleanic.entities.Planification;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.PlanificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlanificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlanificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlanificationService planificationService;

    @MockBean
    private com.example.Pointage_Cleanic.repositories.PlanificationRepository planificationRepository;

    // === Sécurité (OBLIGATOIRE en WebMvcTest)
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    // =========================
    // CREATE
    // =========================
    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void create_planification_ok() throws Exception {
        Planification plan = new Planification();
        PlanificationDto dto = new PlanificationDto();

        when(planificationService.createPlanification(any()))
                .thenReturn(dto);

        mockMvc.perform(post("/api/planification")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(plan)))
                .andExpect(status().isOk());
    }

    // =========================
    // GET ALL
    // =========================
    @Test
    @WithMockUser(roles = "SUPERVISEUR")
    void get_all_planifications_ok() throws Exception {
        when(planificationService.getAll())
                .thenReturn(List.of(new PlanificationDto()));

        mockMvc.perform(get("/api/planification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================
    // GET BY ID
    // =========================
    @Test
    @WithMockUser(roles = "BACKOFFICE")
    void get_planification_by_id_ok() throws Exception {
        when(planificationService.getById("1"))
                .thenReturn(new PlanificationDto());

        mockMvc.perform(get("/api/planification/1"))
                .andExpect(status().isOk());
    }

    // =========================
    // CANCEL
    // =========================
    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void cancel_planification_ok() throws Exception {
        CancelRequestDto dto = new CancelRequestDto();
        dto.setPlanificationId("1");
        dto.setMotif("Erreur");

        Planification plan = new Planification();
        plan.setId("1");

        when(planificationService.cancelPlanification("1", "Erreur"))
                .thenReturn(true);
        when(planificationRepository.findById("1"))
                .thenReturn(Optional.of(plan));

        mockMvc.perform(post("/api/planification/cancel")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    // =========================
    // UPDATE
    // =========================
    @Test
    @WithMockUser(roles = "SUPERVISEUR")
    void update_planification_ok() throws Exception {
        when(planificationService.updatePlanification(eq("1"), any()))
                .thenReturn(Optional.of(new PlanificationDto()));

        mockMvc.perform(put("/api/planification/1")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Planification())))
                .andExpect(status().isOk());
    }

    // =========================
    // DELETE
    // =========================
    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void delete_planification_ok() throws Exception {
        doNothing().when(planificationService).delete("1");

        mockMvc.perform(delete("/api/planification/1"))
                .andExpect(status().isOk());
    }

    // =========================
    // DEMANDER ANNULATION
    // =========================
    @Test
    @WithMockUser(roles = "SUPERVISEUR")
    void demander_annulation_ok() throws Exception {
        CancelRequestDto dto = new CancelRequestDto();
        dto.setPlanificationId("1");
        dto.setMotif("Besoin personnel");
        dto.setRequestedBy("user1");

        when(planificationService.demanderAnnulation(any(), any(), any()))
                .thenReturn(dto);

        mockMvc.perform(post("/api/planification/demander")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    // =========================
    // VALIDER ANNULATION
    // =========================
    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void valider_annulation_ok() throws Exception {
        ValidationRequestDto dto = new ValidationRequestDto();
        dto.setId("1");
        dto.setAccepted(true);
        dto.setValidatedBy("admin");

        when(planificationService.validerAnnulation("1", true, "admin"))
                .thenReturn(new PlanificationDto());

        mockMvc.perform(post("/api/planification/valider")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    // =========================
    // PENDING
    // =========================
    @Test
    @WithMockUser(roles = "SUPERADMIN")
    void get_pending_requests_ok() throws Exception {
        when(planificationService.getPendingAnnulations())
                .thenReturn(List.of(new AnnulationRequestMessage()));

        mockMvc.perform(get("/api/planification/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
