package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Pointage;
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

        Mockito.when(pointageServices.canPoint("DEV-1", 2)).thenReturn(true);
        Mockito.when(pointageServices.enregistrerPointage("1234", "DEV-1", Mockito.anyDouble(), Mockito.anyDouble()))
                .thenReturn(pointage);

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
