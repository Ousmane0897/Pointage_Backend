package com.example.Pointage_Cleanic.controllers.rh;

import com.example.Pointage_Cleanic.Dto.rh.KpiRhDto;
import com.example.Pointage_Cleanic.Dto.RepartitionItemDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.rh.TableauBordRhService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TableauBordRhController.class)
@AutoConfigureMockMvc(addFilters = false)
class TableauBordRhControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TableauBordRhService tableauBordRhService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void get_kpis_defaut_ok() throws Exception {
        KpiRhDto kpis = KpiRhDto.builder()
                .effectifTotal(42L)
                .turnover(3.5)
                .tauxAbsenteisme(4.2)
                .retardsMoyensMinutes(12.5)
                .masseSalarialeMensuelle(21_000_000L)
                .formationsRealisees(3L)
                .sanctionsParType(List.of(
                        RepartitionItemDto.builder().label("AVERTISSEMENT").value(5L).build()))
                .build();
        when(tableauBordRhService.calculer(any(), any(), any(), any())).thenReturn(kpis);

        mockMvc.perform(get("/api/developpement-rh/tableau-bord"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectifTotal").value(42))
                .andExpect(jsonPath("$.turnover").value(3.5))
                .andExpect(jsonPath("$.masseSalarialeMensuelle").value(21_000_000L))
                .andExpect(jsonPath("$.sanctionsParType[0].label").value("AVERTISSEMENT"));
    }

    @Test
    void get_kpis_avec_filtres_ok() throws Exception {
        when(tableauBordRhService.calculer(
                eq(LocalDate.of(2026, 4, 1)),
                eq(LocalDate.of(2026, 4, 30)),
                eq("Nettoyage"), eq("DKR-01")))
                .thenReturn(KpiRhDto.builder().effectifTotal(10L).build());

        mockMvc.perform(get("/api/developpement-rh/tableau-bord")
                        .param("dateDebut", "2026-04-01")
                        .param("dateFin", "2026-04-30")
                        .param("departement", "Nettoyage")
                        .param("site", "DKR-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectifTotal").value(10));
    }
}
