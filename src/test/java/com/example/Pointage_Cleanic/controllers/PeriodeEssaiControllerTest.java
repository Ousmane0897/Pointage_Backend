package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.PeriodeEssaiDto;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.PeriodeEssaiService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PeriodeEssaiController.class)
@AutoConfigureMockMvc(addFilters = false)
class PeriodeEssaiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PeriodeEssaiService periodeEssaiService;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void get_alertes_ok() throws Exception {
        PeriodeEssaiDto dto = PeriodeEssaiDto.builder()
                .id("emp1")
                .nomComplet("DIOP Mamadou")
                .dureeEssaiMois(3)
                .joursRestants(15)
                .statut("EN_ESSAI")
                .build();

        Mockito.when(periodeEssaiService.getAlertes()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/periodes-essai/alertes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].statut").value("EN_ESSAI"));
    }

    @Test
    void titulariser_ok() throws Exception {
        PeriodeEssaiDto dto = PeriodeEssaiDto.builder()
                .id("emp1")
                .essaiTermine(true)
                .statut("TITULARISE")
                .build();

        Mockito.when(periodeEssaiService.titulariser("emp1")).thenReturn(dto);

        mockMvc.perform(put("/api/periodes-essai/emp1/titulariser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.essaiTermine").value(true))
                .andExpect(jsonPath("$.statut").value("TITULARISE"));
    }

    @Test
    void titulariser_not_found() throws Exception {
        Mockito.when(periodeEssaiService.titulariser("inconnu"))
                .thenThrow(new ResourceNotFoundException("Employé introuvable : inconnu"));

        mockMvc.perform(put("/api/periodes-essai/inconnu/titulariser"))
                .andExpect(status().isNotFound());
    }
}