package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.DeclarationSocialeDto;
import com.example.Pointage_Cleanic.Enum.StatutDeclaration;
import com.example.Pointage_Cleanic.Enum.TypeDeclaration;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.DeclarationSocialeService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeclarationSocialeController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeclarationSocialeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private DeclarationSocialeService declarationSocialeService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private DeclarationSocialeDto build(String id, TypeDeclaration type) {
        DeclarationSocialeDto dto = new DeclarationSocialeDto();
        dto.setId(id);
        dto.setType(type);
        dto.setLibelle("Test");
        dto.setAnnee(2026);
        dto.setMois(4);
        dto.setEffectif(10);
        dto.setTotalBrut(5_000_000L);
        dto.setStatut(StatutDeclaration.GENEREE);
        return dto;
    }

    @Test
    void generer_ipres_mensuelle_avec_periode_iso() throws Exception {
        when(declarationSocialeService.genererIpresMensuelle(4, 2026))
                .thenReturn(build("d-1", TypeDeclaration.IPRES_MENSUELLE));

        mockMvc.perform(get("/api/declarations-sociales/ipres").param("periode", "2026-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("IPRES_MENSUELLE"));
    }

    @Test
    void generer_ipres_mensuelle_avec_mois_annee() throws Exception {
        when(declarationSocialeService.genererIpresMensuelle(4, 2026))
                .thenReturn(build("d-1", TypeDeclaration.IPRES_MENSUELLE));

        mockMvc.perform(get("/api/declarations-sociales/ipres")
                        .param("mois", "4").param("annee", "2026"))
                .andExpect(status().isOk());
    }

    @Test
    void generer_ipres_annuelle_sans_mois() throws Exception {
        when(declarationSocialeService.genererIpresAnnuelle(2026))
                .thenReturn(build("d-2", TypeDeclaration.IPRES_ANNUELLE));

        mockMvc.perform(get("/api/declarations-sociales/ipres").param("annee", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("IPRES_ANNUELLE"));
    }

    @Test
    void generer_css_mensuelle_ok() throws Exception {
        when(declarationSocialeService.genererCssMensuelle(4, 2026))
                .thenReturn(build("d-3", TypeDeclaration.CSS_MENSUELLE));

        mockMvc.perform(get("/api/declarations-sociales/css").param("periode", "2026-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CSS_MENSUELLE"));
    }

    @Test
    void get_all_ok() throws Exception {
        when(declarationSocialeService.getAll(any(), any(), any(), any()))
                .thenReturn(List.of(build("d-1", TypeDeclaration.IPRES_MENSUELLE)));

        mockMvc.perform(get("/api/declarations-sociales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("d-1"));
    }

    @Test
    void get_by_id_ok() throws Exception {
        when(declarationSocialeService.getById("d-1"))
                .thenReturn(build("d-1", TypeDeclaration.IPRES_MENSUELLE));

        mockMvc.perform(get("/api/declarations-sociales/d-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Test"));
    }

    @Test
    void transmettre_ok() throws Exception {
        DeclarationSocialeDto transmise = build("d-1", TypeDeclaration.IPRES_MENSUELLE);
        transmise.setStatut(StatutDeclaration.TRANSMISE);
        transmise.setReferenceExterne("REF-123");
        when(declarationSocialeService.transmettre("d-1", "REF-123")).thenReturn(transmise);

        mockMvc.perform(put("/api/declarations-sociales/d-1/transmettre")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("referenceExterne", "REF-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("TRANSMISE"));
    }

    @Test
    void export_pdf_ok() throws Exception {
        when(declarationSocialeService.exportPdf("d-1")).thenReturn("PDF".getBytes());

        mockMvc.perform(get("/api/declarations-sociales/export/pdf").param("id", "d-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void export_excel_ok() throws Exception {
        when(declarationSocialeService.exportExcel("d-1")).thenReturn("XLS".getBytes());

        mockMvc.perform(get("/api/declarations-sociales/export/excel").param("id", "d-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=declaration-d-1.xlsx"));
    }
}