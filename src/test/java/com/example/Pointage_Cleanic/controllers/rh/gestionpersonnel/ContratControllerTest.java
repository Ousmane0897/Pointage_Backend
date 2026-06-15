package com.example.Pointage_Cleanic.controllers.rh.gestionpersonnel;

import com.example.Pointage_Cleanic.Dto.*;
import com.example.Pointage_Cleanic.Dto.rh.*;
import com.example.Pointage_Cleanic.Enum.rh.StatutContrat;
import com.example.Pointage_Cleanic.Enum.rh.TypeContratRh;
import com.example.Pointage_Cleanic.entities.rh.Avenant;
import com.example.Pointage_Cleanic.entities.rh.Contrat;
import com.example.Pointage_Cleanic.entities.rh.Renouvellement;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.rh.ContratService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
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
import org.springframework.mock.web.MockMultipartFile;
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
    void list_contrats_ok() throws Exception {
        Page<ContratDto> page = new PageImpl<>(List.of(buildDto()), PageRequest.of(0, 10), 1);
        Mockito.when(contratService.list(0, 10, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/gestion-personnel/contrats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value("c1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_contrats_with_query_and_type_ok() throws Exception {
        Page<ContratDto> page = new PageImpl<>(List.of(buildDto()), PageRequest.of(0, 10), 1);
        Mockito.when(contratService.list(0, 10, "Diop", "CDI")).thenReturn(page);

        mockMvc.perform(get("/api/gestion-personnel/contrats")
                        .param("q", "Diop")
                        .param("typeContrat", "CDI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        Mockito.verify(contratService).list(0, 10, "Diop", "CDI");
    }

    @Test
    void list_contrats_invalid_type_returns_400() throws Exception {
        Mockito.when(contratService.list(0, 10, null, "FOOBAR"))
                .thenThrow(new IllegalArgumentException("Type de contrat invalide : FOOBAR"));

        mockMvc.perform(get("/api/gestion-personnel/contrats").param("typeContrat", "FOOBAR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_contrat_by_id_ok() throws Exception {
        Mockito.when(contratService.getById("c1")).thenReturn(buildDto());

        mockMvc.perform(get("/api/gestion-personnel/contrats/c1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("c1"));
    }

    @Test
    void get_contrat_not_found() throws Exception {
        Mockito.when(contratService.getById("inconnu"))
                .thenThrow(new ResourceNotFoundException("Contrat introuvable : inconnu"));

        mockMvc.perform(get("/api/gestion-personnel/contrats/inconnu"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_contrat_ok() throws Exception {
        ContratDto input = new ContratDto();
        input.setEmployeId("emp1");
        input.setTypeContrat(TypeContratRh.CDD);

        Mockito.when(contratService.create(Mockito.any(), Mockito.any())).thenReturn(buildDto());

        MockMultipartFile contrat = new MockMultipartFile(
                "contrat", "contrat", "application/json",
                objectMapper.writeValueAsBytes(input));

        mockMvc.perform(multipart("/api/gestion-personnel/contrats").file(contrat))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("c1"));
    }

    @Test
    void update_contrat_ok() throws Exception {
        ContratDto updated = buildDto();
        updated.setStatut(StatutContrat.RENOUVELE);

        Mockito.when(contratService.update(Mockito.eq("c1"), Mockito.any(), Mockito.any()))
                .thenReturn(updated);

        MockMultipartFile contrat = new MockMultipartFile(
                "contrat", "contrat", "application/json",
                objectMapper.writeValueAsBytes(buildDto()));

        mockMvc.perform(multipart("/api/gestion-personnel/contrats/c1").file(contrat).with(req -> {
                    req.setMethod("PUT");
                    return req;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RENOUVELE"));
    }

    @Test
    void get_fichier_not_found_ok() throws Exception {
        Contrat contrat = Contrat.builder().id("c1").build();
        Mockito.when(contratService.getFichier("c1")).thenReturn(contrat);

        mockMvc.perform(get("/api/gestion-personnel/contrats/c1/fichier"))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_fichier_ok() throws Exception {
        Contrat contrat = Contrat.builder()
                .id("c1")
                .fichierContrat(new byte[]{1, 2, 3})
                .fichierContratNom("contrat.pdf")
                .fichierContratMimeType("application/pdf")
                .build();
        Mockito.when(contratService.getFichier("c1")).thenReturn(contrat);

        mockMvc.perform(get("/api/gestion-personnel/contrats/c1/fichier"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void delete_fichier_ok() throws Exception {
        mockMvc.perform(delete("/api/gestion-personnel/contrats/c1/fichier"))
                .andExpect(status().isNoContent());
        Mockito.verify(contratService).deleteFichier("c1");
    }

    @Test
    void delete_contrat_ok() throws Exception {
        mockMvc.perform(delete("/api/gestion-personnel/contrats/c1"))
                .andExpect(status().isNoContent());

        Mockito.verify(contratService).delete("c1");
    }

    @Test
    void get_renouvellements_ok() throws Exception {
        ContratDto dto = buildDto();
        Renouvellement renouvellement = Renouvellement.builder()
                .id("r1")
                .contratId("c1")
                .nouvelleDateFin(LocalDate.of(2026, 12, 31))
                .motif("Renouvellement annuel")
                .build();
        dto.setRenouvellements(List.of(renouvellement));

        Mockito.when(contratService.getById("c1")).thenReturn(dto);

        mockMvc.perform(get("/api/gestion-personnel/contrats/c1/renouvellements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("r1"));
    }

    @Test
    void renouveler_contrat_ok() throws Exception {
        RenouvellerContratRequest request = new RenouvellerContratRequest();
        request.setNouvelleDateFin(LocalDate.of(2026, 12, 31));
        request.setMotif("Renouvellement annuel");

        ContratDto result = buildDto();
        result.setStatut(StatutContrat.RENOUVELE);

        Mockito.when(contratService.renouveler(Mockito.eq("c1"), Mockito.any())).thenReturn(result);

        mockMvc.perform(post("/api/gestion-personnel/contrats/c1/renouveler")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RENOUVELE"));
    }

    @Test
    void get_avenants_ok() throws Exception {
        ContratDto dto = buildDto();
        Avenant avenant = Avenant.builder()
                .id("a1")
                .contratId("c1")
                .objet("Augmentation")
                .build();
        dto.setAvenants(List.of(avenant));

        Mockito.when(contratService.getById("c1")).thenReturn(dto);

        mockMvc.perform(get("/api/gestion-personnel/contrats/c1/avenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("a1"));
    }

    @Test
    void ajouter_avenant_ok() throws Exception {
        AjouterAvenantRequest request = new AjouterAvenantRequest();
        request.setObjet("Augmentation salariale");
        request.setDescription("Hausse de 10%");
        request.setDateEffet(LocalDate.of(2026, 6, 1));

        Mockito.when(contratService.ajouterAvenant(Mockito.eq("c1"), Mockito.any())).thenReturn(buildDto());

        mockMvc.perform(post("/api/gestion-personnel/contrats/c1/avenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk());
    }

    @Test
    void resilier_contrat_ok() throws Exception {
        ContratDto resilie = buildDto();
        resilie.setStatut(StatutContrat.RESILIE);

        Mockito.when(contratService.resilier("c1")).thenReturn(resilie);

        mockMvc.perform(put("/api/gestion-personnel/contrats/c1/resilier"))
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

        mockMvc.perform(get("/api/gestion-personnel/contrats/alertes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contratId").value("c1"));
    }
}
