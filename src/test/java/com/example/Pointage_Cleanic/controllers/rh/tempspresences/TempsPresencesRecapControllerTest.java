package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.rh.RecapitulatifMensuelDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.rh.RecapitulatifMensuelService;
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

@WebMvcTest(TempsPresencesRecapController.class)
@AutoConfigureMockMvc(addFilters = false)
class TempsPresencesRecapControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private RecapitulatifMensuelService recapitulatifMensuelService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void recapitulatif_renvoie_tableau() throws Exception {
        RecapitulatifMensuelDto ligne = RecapitulatifMensuelDto.builder()
                .employeId("emp-1").matricule("M-1").nom("Diop").prenom("Awa")
                .mois(6).annee(2026).joursOuvrables(22).joursTravailles(20)
                .nombreRetards(3).minutesRetardTotal(45).heuresSupTotal(8)
                .heuresSupParType(RecapitulatifMensuelDto.HeuresSupParTypeDto.builder().t15(4).t40(4).build())
                .build();
        when(recapitulatifMensuelService.getRecapitulatifDetaille(eq(6), eq(2026), any(), any(), any()))
                .thenReturn(List.of(ligne));

        mockMvc.perform(get("/api/temps-presences/recapitulatif?mois=6&annee=2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matricule").value("M-1"))
                .andExpect(jsonPath("$[0].nombreRetards").value(3))
                .andExpect(jsonPath("$[0].heuresSupParType.t15").value(4));
    }

    @Test
    void recapitulatif_sans_mois_renvoie_400() throws Exception {
        mockMvc.perform(get("/api/temps-presences/recapitulatif?annee=2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void export_excel_renvoie_fichier() throws Exception {
        when(recapitulatifMensuelService.exportExcel(eq(6), eq(2026), any()))
                .thenReturn("xlsx".getBytes());

        mockMvc.perform(get("/api/temps-presences/recapitulatif/export/excel?mois=6&annee=2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=recapitulatif-6-2026.xlsx"))
                .andExpect(content().bytes("xlsx".getBytes()));
    }

    @Test
    void export_pdf_renvoie_fichier() throws Exception {
        when(recapitulatifMensuelService.exportPdf(eq(6), eq(2026), any()))
                .thenReturn("pdf".getBytes());

        mockMvc.perform(get("/api/temps-presences/recapitulatif/export/pdf?mois=6&annee=2026"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=recapitulatif-6-2026.pdf"))
                .andExpect(content().bytes("pdf".getBytes()));
    }
}
