package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.*;
import com.example.Pointage_Cleanic.Enum.StatutContrat;
import com.example.Pointage_Cleanic.Enum.TypeContratRh;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.ContratService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContratController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContratControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContratService contratService;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private ContratDto buildDto() {
        ContratDto dto = new ContratDto();
        dto.setId("c1");
        dto.setEmployeId("emp1");
        dto.setTypeContrat(TypeContratRh.CDI);
        dto.setStatut(StatutContrat.ACTIF);
        return dto;
    }

    @Test
    void get_all_contrats_ok() throws Exception {
        Mockito.when(contratService.getAll()).thenReturn(List.of(buildDto()));

        mockMvc.perform(get("/api/contrats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void get_contrat_by_id_ok() throws Exception {
        Mockito.when(contratService.getById("c1")).thenReturn(buildDto());

        mockMvc.perform(get("/api/contrats/c1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("c1"));
    }

    @Test
    void get_contrat_not_found() throws Exception {
        Mockito.when(contratService.getById("inconnu"))
                .thenThrow(new ResourceNotFoundException("Contrat introuvable : inconnu"));

        mockMvc.perform(get("/api/contrats/inconnu"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_contrat_ok() throws Exception {
        ContratDto input = new ContratDto();
        input.setEmployeId("emp1");
        input.setTypeContrat(TypeContratRh.CDD);

        Mockito.when(contratService.create(Mockito.any())).thenReturn(buildDto());

        mockMvc.perform(post("/api/contrats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("c1"));
    }

    @Test
    void update_contrat_ok() throws Exception {
        ContratDto updated = buildDto();
        updated.setStatut(StatutContrat.RENOUVELE);

        Mockito.when(contratService.update(Mockito.eq("c1"), Mockito.any())).thenReturn(updated);

        mockMvc.perform(put("/api/contrats/c1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(buildDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RENOUVELE"));
    }

    @Test
    void delete_contrat_ok() throws Exception {
        mockMvc.perform(delete("/api/contrats/c1"))
                .andExpect(status().isNoContent());

        Mockito.verify(contratService).delete("c1");
    }

    @Test
    void renouveler_contrat_ok() throws Exception {
        RenouvellerContratRequest request = new RenouvellerContratRequest();
        request.setNouvelleDateFin(LocalDate.of(2026, 12, 31));
        request.setMotif("Renouvellement annuel");

        ContratDto result = buildDto();
        result.setStatut(StatutContrat.RENOUVELE);

        Mockito.when(contratService.renouveler(Mockito.eq("c1"), Mockito.any())).thenReturn(result);

        mockMvc.perform(post("/api/contrats/c1/renouveler")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RENOUVELE"));
    }

    @Test
    void ajouter_avenant_ok() throws Exception {
        AjouterAvenantRequest request = new AjouterAvenantRequest();
        request.setObjet("Augmentation salariale");
        request.setDescription("Hausse de 10%");
        request.setDateEffet(LocalDate.of(2026, 6, 1));

        Mockito.when(contratService.ajouterAvenant(Mockito.eq("c1"), Mockito.any())).thenReturn(buildDto());

        mockMvc.perform(post("/api/contrats/c1/avenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk());
    }

    @Test
    void resilier_contrat_ok() throws Exception {
        ContratDto resilie = buildDto();
        resilie.setStatut(StatutContrat.RESILIE);

        Mockito.when(contratService.resilier("c1")).thenReturn(resilie);

        mockMvc.perform(put("/api/contrats/c1/resilier"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RESILIE"));
    }

    @Test
    void get_alertes_echeance_ok() throws Exception {
        AlerteContratDto alerte = AlerteContratDto.builder()
                .contratId("c1")
                .employeId("emp1")
                .employeNom("Diop")
                .employePrenom("Mamadou")
                .typeContrat(TypeContratRh.CDD)
                .dateFin(LocalDate.now().plusDays(10))
                .joursRestants(10)
                .build();

        Mockito.when(contratService.getAlertesEcheance()).thenReturn(List.of(alerte));

        mockMvc.perform(get("/api/contrats/alertes-echeance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contratId").value("c1"));
    }
}
