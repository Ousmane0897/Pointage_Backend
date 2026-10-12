package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.pointage.PointageResponse;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.exception.PointageRefuseException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.models.PointageRequest;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.PointageServices;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PointagesController.class)
@AutoConfigureMockMvc(addFilters = false)
class PointagesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PointageServices pointageServices;

    @MockBean
    private PointageRepository pointageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    // ==========================================
    // POST /api/pointages — SUCCESS
    // ==========================================

    @Test
    void should_pointer_successfully() throws Exception {

        PointageRequest request = new PointageRequest();
        request.setCodeSecret("1234");
        request.setDeviceId("DEV-1");

        Pointage pointage = new Pointage();

        Mockito.when(pointageServices.canPoint(eq("DEV-1"), Mockito.anyInt())).thenReturn(true);
        Mockito.when(pointageServices.pointer(eq("1234"), eq("DEV-1"), any(), any(), any(), any()))
                .thenReturn(PointageResponse.builder().pointage(pointage).type("ENTREE").build());

        mockMvc.perform(post("/api/pointages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk());
    }

    // ==========================================
    // POST /api/pointages — DEVICE LOCKED
    // ==========================================

    @Test
    void should_reject_when_device_recently_used() throws Exception {

        PointageRequest request = new PointageRequest();
        request.setCodeSecret("1234");
        request.setDeviceId("DEV-1");

        Mockito.when(pointageServices.canPoint("DEV-1", 2)).thenReturn(false);

        mockMvc.perform(post("/api/pointages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("téléphone")));
    }

    // ==========================================
    // POST /api/pointages — VALIDATION (corps invalide → 400, plus de NPE/500)
    // ==========================================

    @Test
    void should_return_400_when_body_empty() throws Exception {

        // Corps {} : codeSecret + deviceId absents → @Valid rejette (avant : NPE → 500)
        mockMvc.perform(post("/api/pointages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void should_return_400_when_device_id_blank() throws Exception {

        PointageRequest request = new PointageRequest();
        request.setCodeSecret("1234");
        request.setDeviceId("   "); // blank → @NotBlank

        mockMvc.perform(post("/api/pointages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ==========================================
    // POST /api/pointages — EMPLOYÉ INTROUVABLE → 404 JSON
    // ==========================================

    @Test
    void should_return_404_when_employe_not_found() throws Exception {

        PointageRequest request = new PointageRequest();
        request.setCodeSecret("9999");
        request.setDeviceId("DEV-1");

        Mockito.when(pointageServices.canPoint(eq("DEV-1"), Mockito.anyInt())).thenReturn(true);
        Mockito.when(pointageServices.pointer(eq("9999"), eq("DEV-1"), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Employé introuvable"));

        mockMvc.perform(post("/api/pointages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // ==========================================
    // POST /api/pointages — QR D'AGENCE
    // ==========================================

    @Test
    void should_relay_qr_payload_and_return_agence() throws Exception {

        PointageRequest request = new PointageRequest();
        request.setCodeSecret("1234");
        request.setDeviceId("DEV-1");
        request.setLatitude(14.7);
        request.setLongitude(-17.4);
        request.setPrecisionMetres(12.0);
        request.setQrPayload("POINTIC:AG:abc");

        Pointage pointage = new Pointage();
        pointage.setHeureArrive("08:00");

        Mockito.when(pointageServices.canPoint(eq("DEV-1"), Mockito.anyInt())).thenReturn(true);
        Mockito.when(pointageServices.pointer("1234", "DEV-1", 14.7, -17.4, 12.0, "POINTIC:AG:abc"))
                .thenReturn(PointageResponse.builder()
                        .pointage(pointage).type("ENTREE")
                        .agenceId("ag-1").agenceNom("SGS Mermoz").distanceMetres(18.0)
                        .build());

        mockMvc.perform(post("/api/pointages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                // Le pointage est déplié à la racine : l'ancien contrat reste lisible.
                .andExpect(jsonPath("$.heureArrive").value("08:00"))
                .andExpect(jsonPath("$.type").value("ENTREE"))
                .andExpect(jsonPath("$.agenceNom").value("SGS Mermoz"));
    }

    @Test
    void should_return_422_with_message_when_scan_refused() throws Exception {

        PointageRequest request = new PointageRequest();
        request.setCodeSecret("1234");
        request.setDeviceId("DEV-1");
        request.setQrPayload("autre-chose");

        Mockito.when(pointageServices.canPoint(eq("DEV-1"), Mockito.anyInt())).thenReturn(true);
        Mockito.when(pointageServices.pointer(eq("1234"), eq("DEV-1"), any(), any(), any(), eq("autre-chose")))
                .thenThrow(new PointageRefuseException("QR code non reconnu."));

        mockMvc.perform(post("/api/pointages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("POINTAGE_REFUSE"))
                .andExpect(jsonPath("$.message").value("QR code non reconnu."));
    }

    @Test
    void should_return_agence_name_for_valid_qr() throws Exception {

        Mockito.when(pointageServices.nomAgenceQr("POINTIC:AG:abc")).thenReturn(Optional.of("BGFI Tann"));

        mockMvc.perform(get("/api/pointages/agence-qr").param("payload", "POINTIC:AG:abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("BGFI Tann"));
    }

    @Test
    void should_return_404_for_unknown_qr() throws Exception {

        Mockito.when(pointageServices.nomAgenceQr(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/pointages/agence-qr").param("payload", "POINTIC:AG:zzz"))
                .andExpect(status().isNotFound());
    }

    // ==========================================
    // GET /api/pointages
    // ==========================================

    @Test
    void should_return_all_pointages() throws Exception {

        Mockito.when(pointageRepository.findAll())
                .thenReturn(List.of(new Pointage()));

        mockMvc.perform(get("/api/pointages"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").isArray());
    }

    // ==========================================
    // GET /api/pointages/today
    // ==========================================

    @Test
    void should_return_paginated_today_pointages() throws Exception {

        Pointage pointage = new Pointage();
        PageImpl<Pointage> pagePayload = new PageImpl<>(
                List.of(pointage),
                PageRequest.of(0, 20),
                1
        );

        Mockito.when(pointageRepository.findByDate(eq(LocalDate.now()), any(Pageable.class)))
                .thenReturn(pagePayload);

        mockMvc.perform(get("/api/pointages/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void should_return_empty_page_today() throws Exception {

        PageImpl<Pointage> empty = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0);

        Mockito.when(pointageRepository.findByDate(eq(LocalDate.now()), any(Pageable.class)))
                .thenReturn(empty);

        mockMvc.perform(get("/api/pointages/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void should_expose_total_elements_greater_than_size() throws Exception {

        // 1 élément sur la page mais 25 au total → pagination
        PageImpl<Pointage> page = new PageImpl<>(
                List.of(new Pointage()), PageRequest.of(0, 20), 25);

        Mockito.when(pointageRepository.findByDate(eq(LocalDate.now()), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/pointages/today").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void should_return_en_cours_pointage_with_nullable_fields() throws Exception {

        Pointage p = Pointage.builder()
                .id("p1")
                .codeSecret("1234")
                .prenom("Mamadou")
                .nom("Diop")
                .site(new String[]{"yoff"})
                .date(LocalDate.now())
                .heureArrive("08:12")
                .heureDepart(null)
                .duree(null)
                .status("EN COURS...")
                .build();

        Mockito.when(pointageRepository.findByDate(eq(LocalDate.now()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/pointages/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].codeSecret").value("1234"))
                .andExpect(jsonPath("$.content[0].status").value("EN COURS..."))
                .andExpect(jsonPath("$.content[0].site").isArray())
                .andExpect(jsonPath("$.content[0].site[0]").value("yoff"))
                .andExpect(jsonPath("$.content[0].heureArrive").value("08:12"))
                .andExpect(jsonPath("$.content[0].heureDepart").isEmpty())
                .andExpect(jsonPath("$.content[0].duree").isEmpty());
    }

    @Test
    void should_return_termine_pointage() throws Exception {

        Pointage p = Pointage.builder()
                .id("p2")
                .codeSecret("5678")
                .status("TERMINÉ")
                .heureArrive("08:00")
                .heureDepart("17:30")
                .duree("8h")
                .build();

        Mockito.when(pointageRepository.findByDate(eq(LocalDate.now()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/pointages/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("TERMINÉ"))
                .andExpect(jsonPath("$.content[0].heureDepart").value("17:30"))
                .andExpect(jsonPath("$.content[0].duree").value("8h"));
    }

    // ==========================================
    // GET /api/pointages/{codeSecret}
    // ==========================================

    @Test
    void should_return_pointage_when_found() throws Exception {

        Pointage pointage = new Pointage();

        Mockito.when(pointageServices.getPointageBycodeSecret("1234"))
                .thenReturn(pointage);

        mockMvc.perform(get("/api/pointages/1234"))
                .andExpect(status().isOk());
    }

    @Test
    void should_return_404_when_not_found() throws Exception {

        Mockito.when(pointageServices.getPointageBycodeSecret("9999"))
                .thenReturn(null);

        mockMvc.perform(get("/api/pointages/9999"))
                .andExpect(status().isNotFound());
    }
}
