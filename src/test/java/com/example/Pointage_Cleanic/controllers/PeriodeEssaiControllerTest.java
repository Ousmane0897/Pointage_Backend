package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.DemandeValidationDto;
import com.example.Pointage_Cleanic.Dto.PeriodeEssaiDto;
import com.example.Pointage_Cleanic.Enum.ActionValidation;
import com.example.Pointage_Cleanic.Enum.StatutPeriodeEssai;
import com.example.Pointage_Cleanic.Enum.StatutValidation;
import com.example.Pointage_Cleanic.Enum.TypeContratRh;
import com.example.Pointage_Cleanic.exception.DemandeValidationConflictException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.DemandeValidationPeriodeEssaiService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.PeriodeEssaiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PeriodeEssaiController.class)
@AutoConfigureMockMvc(addFilters = false)
class PeriodeEssaiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PeriodeEssaiService periodeEssaiService;

    @MockBean
    private DemandeValidationPeriodeEssaiService demandeValidationService;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private PeriodeEssaiDto buildPeriode() {
        return PeriodeEssaiDto.builder()
                .id("p1")
                .employeId("emp1")
                .employeNom("Diop")
                .employePrenom("Mamadou")
                .contratId("c1")
                .typeContrat(TypeContratRh.CDI)
                .dateDebut(LocalDate.of(2026, 1, 1))
                .dateFin(LocalDate.of(2026, 4, 1))
                .dureeJours(90)
                .statut(StatutPeriodeEssai.EN_COURS)
                .build();
    }

    private DemandeValidationDto buildDemande() {
        return DemandeValidationDto.builder()
                .id("d1")
                .periodeEssaiId("p1")
                .employeId("emp1")
                .statut(StatutValidation.EN_ATTENTE_MANAGER)
                .dateCreation(LocalDateTime.of(2026, 4, 29, 10, 0))
                .build();
    }

    @Test
    void list_periodes_ok() throws Exception {
        Page<PeriodeEssaiDto> page = new PageImpl<>(List.of(buildPeriode()), PageRequest.of(0, 10), 1);
        Mockito.when(periodeEssaiService.list(0, 10, null)).thenReturn(page);

        mockMvc.perform(get("/api/gestion-personnel/periodes-essai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value("p1"));
    }

    @Test
    void list_periodes_with_statut_filter() throws Exception {
        Page<PeriodeEssaiDto> page = new PageImpl<>(List.of(buildPeriode()), PageRequest.of(0, 10), 1);
        Mockito.when(periodeEssaiService.list(0, 10, "EN_COURS")).thenReturn(page);

        mockMvc.perform(get("/api/gestion-personnel/periodes-essai").param("statut", "EN_COURS"))
                .andExpect(status().isOk());

        Mockito.verify(periodeEssaiService).list(0, 10, "EN_COURS");
    }

    @Test
    void get_periode_by_id_ok() throws Exception {
        Mockito.when(periodeEssaiService.getById("p1")).thenReturn(buildPeriode());

        mockMvc.perform(get("/api/gestion-personnel/periodes-essai/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("p1"))
                .andExpect(jsonPath("$.statut").value("EN_COURS"));
    }

    @Test
    void get_periode_not_found() throws Exception {
        Mockito.when(periodeEssaiService.getById("inconnu"))
                .thenThrow(new ResourceNotFoundException("Période d'essai introuvable : inconnu"));

        mockMvc.perform(get("/api/gestion-personnel/periodes-essai/inconnu"))
                .andExpect(status().isNotFound());
    }

    @Test
    void prolonger_ok() throws Exception {
        PeriodeEssaiDto prolonged = buildPeriode();
        prolonged.setStatut(StatutPeriodeEssai.PROLONGE);
        prolonged.setDateFin(LocalDate.of(2026, 6, 1));

        Mockito.when(periodeEssaiService.prolonger(Mockito.eq("p1"), Mockito.any(), Mockito.any()))
                .thenReturn(prolonged);

        String body = """
                {"nouvelleDateFin":"2026-06-01","commentaire":"motif"}""";

        mockMvc.perform(put("/api/gestion-personnel/periodes-essai/p1/prolonger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PROLONGE"));
    }

    @Test
    void prolonger_invalid_returns_400() throws Exception {
        Mockito.when(periodeEssaiService.prolonger(Mockito.eq("p1"), Mockito.any(), Mockito.any()))
                .thenThrow(new IllegalArgumentException(
                        "nouvelleDateFin doit être strictement postérieure à la date de fin actuelle"));

        String body = """
                {"nouvelleDateFin":"2026-01-15","commentaire":"motif"}""";

        mockMvc.perform(put("/api/gestion-personnel/periodes-essai/p1/prolonger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_alertes_ok() throws Exception {
        Mockito.when(periodeEssaiService.getAlertes()).thenReturn(List.of(buildPeriode()));

        mockMvc.perform(get("/api/gestion-personnel/periodes-essai/alertes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("p1"));
    }

    @Test
    void list_validations_ok() throws Exception {
        Mockito.when(demandeValidationService.list(null)).thenReturn(List.of(buildDemande()));

        mockMvc.perform(get("/api/gestion-personnel/periodes-essai/validations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("d1"));
    }

    @Test
    void list_validations_with_statut_filter() throws Exception {
        Mockito.when(demandeValidationService.list("EN_ATTENTE_MANAGER"))
                .thenReturn(List.of(buildDemande()));

        mockMvc.perform(get("/api/gestion-personnel/periodes-essai/validations")
                        .param("statut", "EN_ATTENTE_MANAGER"))
                .andExpect(status().isOk());

        Mockito.verify(demandeValidationService).list("EN_ATTENTE_MANAGER");
    }

    @Test
    void creer_validation_ok() throws Exception {
        Mockito.when(demandeValidationService.creer(Mockito.eq("p1"), Mockito.any()))
                .thenReturn(buildDemande());

        mockMvc.perform(post("/api/gestion-personnel/periodes-essai/p1/validations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commentaire\":\"avis manager\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("d1"))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_MANAGER"));
    }

    @Test
    void creer_validation_conflict_returns_409() throws Exception {
        Mockito.when(demandeValidationService.creer(Mockito.eq("p1"), Mockito.any()))
                .thenThrow(new DemandeValidationConflictException(
                        "Une demande de validation est déjà active pour cette période d'essai"));

        mockMvc.perform(post("/api/gestion-personnel/periodes-essai/p1/validations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commentaire\":\"avis manager\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void valider_demande_ok() throws Exception {
        DemandeValidationDto validee = buildDemande();
        validee.setStatut(StatutValidation.VALIDEE_MANAGER);

        Mockito.when(demandeValidationService.traiter(
                        Mockito.eq("d1"), Mockito.any(), Mockito.any()))
                .thenReturn(validee);

        String body = """
                {"decision":"VALIDER","commentaire":"OK manager"}""";

        mockMvc.perform(put("/api/gestion-personnel/periodes-essai/validations/d1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDEE_MANAGER"));

        Mockito.verify(demandeValidationService).traiter(
                Mockito.eq("d1"),
                Mockito.argThat(req -> req.decision() == ActionValidation.VALIDER
                        && "OK manager".equals(req.commentaire())),
                Mockito.any());
    }

    @Test
    void valider_demande_illegal_transition_returns_400() throws Exception {
        Mockito.when(demandeValidationService.traiter(
                        Mockito.eq("d1"), Mockito.any(), Mockito.any()))
                .thenThrow(new IllegalArgumentException(
                        "Transition CONFIRMER illégale depuis EN_ATTENTE_MANAGER (attendu VALIDEE_RH)"));

        mockMvc.perform(put("/api/gestion-personnel/periodes-essai/validations/d1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRMER\",\"commentaire\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}