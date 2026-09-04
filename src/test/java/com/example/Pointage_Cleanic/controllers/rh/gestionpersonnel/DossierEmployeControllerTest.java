package com.example.Pointage_Cleanic.controllers.rh.gestionpersonnel;

import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeDto;
import com.example.Pointage_Cleanic.Enum.rh.GenreEmploye;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.exception.AffectationInvalideException;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.rh.DossierEmployeService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DossierEmployeController.class)
@AutoConfigureMockMvc(addFilters = false)
class DossierEmployeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DossierEmployeService service;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void list_ok() throws Exception {
        DossierEmployeDto dto = DossierEmployeDto.builder()
                .id("1").matricule("M001").nom("DIOP").prenom("Mamadou")
                .genre(GenreEmploye.HOMME)
                .statut(StatutDossierEmploye.ACTIF)
                .build();
        Page<DossierEmployeDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);
        Mockito.when(service.list(0, 10, null, null, null, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/gestion-personnel/employes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].matricule").value("M001"));
    }

    @Test
    void get_by_id_ok() throws Exception {
        DossierEmployeDto dto = DossierEmployeDto.builder()
                .id("1").matricule("M001").nom("DIOP").prenom("Mamadou")
                .build();
        Mockito.when(service.getById("1")).thenReturn(dto);

        mockMvc.perform(get("/api/gestion-personnel/employes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("DIOP"));
    }

    @Test
    void create_ok() throws Exception {
        DossierEmployeDto created = DossierEmployeDto.builder()
                .id("1").matricule("M001").nom("DIOP").prenom("Mamadou")
                .build();
        Mockito.when(service.create(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(created);

        MockMultipartFile dossier = new MockMultipartFile(
                "dossier", "dossier", "application/json",
                "{\"matricule\":\"M001\",\"nom\":\"DIOP\",\"prenom\":\"Mamadou\"}".getBytes());

        mockMvc.perform(multipart("/api/gestion-personnel/employes").file(dossier))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matricule").value("M001"));
    }

    /**
     * Une écriture qui ferait disparaître une affectation close doit ressortir en
     * <b>422</b>, et non en 500 : sans handler dédié, l'exception passerait par le
     * catch-all {@code RuntimeException} du {@code GlobalExceptionHandler}. Le front
     * affiche le champ {@code message} tel quel.
     */
    @Test
    void update_retourne_422_quand_une_affectation_close_disparait() throws Exception {
        Mockito.when(service.update(ArgumentMatchers.eq("1"), ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenThrow(new AffectationInvalideException(
                        "L'affectation close sur Yoff (du 2026-01-01 au 2026-06-30) "
                                + "ne peut pas être retirée du dossier."));

        MockMultipartFile dossier = new MockMultipartFile(
                "dossier", "dossier", "application/json",
                "{\"matricule\":\"M001\",\"nom\":\"DIOP\",\"prenom\":\"Mamadou\"}".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/gestion-personnel/employes/1")
                        .file(dossier))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("AFFECTATION_INVALIDE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Yoff")));
    }

    @Test
    void update_statut_ok() throws Exception {
        DossierEmployeDto updated = DossierEmployeDto.builder()
                .id("1").matricule("M001").statut(StatutDossierEmploye.ACTIF)
                .build();
        Mockito.when(service.updateStatut(ArgumentMatchers.eq("1"), ArgumentMatchers.any()))
                .thenReturn(updated);

        mockMvc.perform(put("/api/gestion-personnel/employes/1/statut")
                        .contentType("application/json")
                        .content("{\"statut\":\"ACTIF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIF"));
    }

    @Test
    void get_photo_not_found_ok() throws Exception {
        Mockito.when(service.getPhoto("1")).thenReturn(null);

        mockMvc.perform(get("/api/gestion-personnel/employes/1/photo"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_ok() throws Exception {
        mockMvc.perform(delete("/api/gestion-personnel/employes/1"))
                .andExpect(status().isNoContent());
    }
}