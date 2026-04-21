package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.RecapitulatifMensuelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecapitulatifMensuelController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecapitulatifMensuelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecapitulatifMensuelService recapitulatifMensuelService;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private RecapitulatifMensuelService.LigneRecapDto buildLigne(String nom) {
        RecapitulatifMensuelService.LigneRecapDto ligne = new RecapitulatifMensuelService.LigneRecapDto();
        ligne.setNom(nom);
        ligne.setPrenom("Test");
        ligne.setJoursPresents(20);
        ligne.setJoursAbsents(2);
        return ligne;
    }

    @Test
    void get_recapitulatif_ok() throws Exception {
        when(recapitulatifMensuelService.getRecapitulatif(4, 2026, ""))
                .thenReturn(List.of(buildLigne("Diop")));

        mockMvc.perform(get("/api/recapitulatif-mensuel")
                        .param("mois", "4")
                        .param("annee", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nom").value("Diop"))
                .andExpect(jsonPath("$[0].joursPresents").value(20));
    }

    @Test
    void get_recapitulatif_with_departement_ok() throws Exception {
        when(recapitulatifMensuelService.getRecapitulatif(4, 2026, "RH"))
                .thenReturn(List.of(buildLigne("Fall")));

        mockMvc.perform(get("/api/recapitulatif-mensuel")
                        .param("mois", "4")
                        .param("annee", "2026")
                        .param("departement", "RH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nom").value("Fall"));
    }

    @Test
    void export_excel_ok() throws Exception {
        when(recapitulatifMensuelService.exportExcel(4, 2026, ""))
                .thenReturn("excel-bytes".getBytes());

        mockMvc.perform(get("/api/recapitulatif-mensuel/export/excel")
                        .param("mois", "4")
                        .param("annee", "2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=recapitulatif-4-2026.xlsx"));
    }

    @Test
    void export_pdf_ok() throws Exception {
        when(recapitulatifMensuelService.exportPdf(4, 2026, ""))
                .thenReturn("pdf-bytes".getBytes());

        mockMvc.perform(get("/api/recapitulatif-mensuel/export/pdf")
                        .param("mois", "4")
                        .param("annee", "2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=recapitulatif-4-2026.pdf"));
    }

    @Test
    void export_excel_with_departement_ok() throws Exception {
        when(recapitulatifMensuelService.exportExcel(3, 2026, "IT"))
                .thenReturn("excel".getBytes());

        mockMvc.perform(get("/api/recapitulatif-mensuel/export/excel")
                        .param("mois", "3")
                        .param("annee", "2026")
                        .param("departement", "IT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=recapitulatif-3-2026.xlsx"));
    }
}