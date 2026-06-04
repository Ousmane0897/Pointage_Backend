package com.example.Pointage_Cleanic.controllers.rh;

import com.example.Pointage_Cleanic.Dto.rh.BulletinPaieDto;
import com.example.Pointage_Cleanic.Dto.rh.CalculBulletinRequest;
import com.example.Pointage_Cleanic.Dto.rh.ValiderBulletinRequest;
import com.example.Pointage_Cleanic.Enum.rh.StatutBulletin;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.rh.BulletinPaieService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BulletinPaieController.class)
@AutoConfigureMockMvc(addFilters = false)
class BulletinPaieControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private BulletinPaieService bulletinPaieService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private BulletinPaieDto buildDto(String id) {
        BulletinPaieDto dto = new BulletinPaieDto();
        dto.setId(id);
        dto.setEmployeId("emp-1");
        dto.setMatricule("M001");
        dto.setSalaireBrut(500_000L);
        dto.setNetAPayer(420_000L);
        dto.setStatut(StatutBulletin.BROUILLON);
        return dto;
    }

    @Test
    void calculer_ok() throws Exception {
        CalculBulletinRequest req = new CalculBulletinRequest("emp-1", 4, 2026, null);
        when(bulletinPaieService.calculerEtSauvegarder("emp-1", 4, 2026))
                .thenReturn(buildDto("bp-new"));

        mockMvc.perform(post("/api/bulletins-paie/calculer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("bp-new"))
                .andExpect(jsonPath("$.statut").value("BROUILLON"));
    }

    @Test
    void get_historique_ok() throws Exception {
        when(bulletinPaieService.getHistorique("emp-1", 2026))
                .thenReturn(List.of(buildDto("bp-1"), buildDto("bp-2")));

        mockMvc.perform(get("/api/bulletins-paie/historique")
                        .param("employeId", "emp-1")
                        .param("annee", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bp-1"))
                .andExpect(jsonPath("$[1].id").value("bp-2"));
    }

    @Test
    void get_all_ok() throws Exception {
        when(bulletinPaieService.getAll(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(buildDto("bp-1")));

        mockMvc.perform(get("/api/bulletins-paie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bp-1"));
    }

    @Test
    void get_by_id_ok() throws Exception {
        when(bulletinPaieService.getById("bp-1")).thenReturn(buildDto("bp-1"));

        mockMvc.perform(get("/api/bulletins-paie/bp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricule").value("M001"));
    }

    @Test
    void update_ok() throws Exception {
        BulletinPaieDto input = buildDto("bp-1");
        when(bulletinPaieService.update(eq("bp-1"), any(BulletinPaieDto.class))).thenReturn(input);

        mockMvc.perform(put("/api/bulletins-paie/bp-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk());
    }

    @Test
    void delete_ok() throws Exception {
        doNothing().when(bulletinPaieService).delete("bp-1");

        mockMvc.perform(delete("/api/bulletins-paie/bp-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void valider_ok() throws Exception {
        BulletinPaieDto validé = buildDto("bp-1");
        validé.setStatut(StatutBulletin.VALIDE);
        when(bulletinPaieService.valider(eq("bp-1"), any(ValiderBulletinRequest.class)))
                .thenReturn(validé);

        mockMvc.perform(put("/api/bulletins-paie/bp-1/valider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ValiderBulletinRequest("mgr-1", "Diallo", "OK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));
    }

    @Test
    void payer_ok() throws Exception {
        BulletinPaieDto payé = buildDto("bp-1");
        payé.setStatut(StatutBulletin.PAYE);
        when(bulletinPaieService.marquerPaye("bp-1")).thenReturn(payé);

        mockMvc.perform(put("/api/bulletins-paie/bp-1/payer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PAYE"));
    }

    @Test
    void annuler_ok() throws Exception {
        BulletinPaieDto annulé = buildDto("bp-1");
        annulé.setStatut(StatutBulletin.ANNULE);
        when(bulletinPaieService.annuler(eq("bp-1"), eq("erreur"))).thenReturn(annulé);

        mockMvc.perform(put("/api/bulletins-paie/bp-1/annuler")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("commentaire", "erreur"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULE"));
    }

    @Test
    void export_pdf_ok() throws Exception {
        when(bulletinPaieService.exportPdf("bp-1")).thenReturn("PDFDATA".getBytes());

        mockMvc.perform(get("/api/bulletins-paie/bp-1/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }
}