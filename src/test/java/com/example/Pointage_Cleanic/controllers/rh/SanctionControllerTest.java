package com.example.Pointage_Cleanic.controllers.rh;

import com.example.Pointage_Cleanic.Dto.rh.AlerteRecidiveDto;
import com.example.Pointage_Cleanic.Dto.rh.SanctionDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutSanction;
import com.example.Pointage_Cleanic.Enum.rh.TypeSanction;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.rh.SanctionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SanctionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SanctionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private SanctionService sanctionService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private SanctionDto dto(String id) {
        return SanctionDto.builder()
                .id(id)
                .employeId("emp-1")
                .type(TypeSanction.AVERTISSEMENT)
                .statut(StatutSanction.CONVOCATION)
                .dateSanction(LocalDate.of(2026, 4, 21))
                .motif("Retards répétés")
                .build();
    }

    @Test
    void create_ok() throws Exception {
        when(sanctionService.create(any())).thenReturn(dto("sa-1"));

        mockMvc.perform(post("/api/sanctions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("sa-1"))
                .andExpect(jsonPath("$.type").value("AVERTISSEMENT"));
    }

    @Test
    void search_ok() throws Exception {
        when(sanctionService.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(dto("sa-1")));

        mockMvc.perform(get("/api/sanctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sa-1"));
    }

    @Test
    void historique_ok() throws Exception {
        when(sanctionService.historique("emp-1"))
                .thenReturn(List.of(dto("sa-1"), dto("sa-2")));

        mockMvc.perform(get("/api/sanctions/historique/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sa-1"))
                .andExpect(jsonPath("$[1].id").value("sa-2"));
    }

    @Test
    void changer_statut_ok() throws Exception {
        SanctionDto executee = dto("sa-1");
        executee.setStatut(StatutSanction.EXECUTEE);
        when(sanctionService.changerStatut(eq("sa-1"), eq(StatutSanction.EXECUTEE)))
                .thenReturn(executee);

        mockMvc.perform(put("/api/sanctions/sa-1/statut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("statut", "EXECUTEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EXECUTEE"));
    }

    @Test
    void alertes_recidive_ok() throws Exception {
        AlerteRecidiveDto alerte = AlerteRecidiveDto.builder()
                .employeId("emp-1").nom("Diallo").prenom("A")
                .nombreSanctions(3).derniereType(TypeSanction.BLAME)
                .build();
        when(sanctionService.alertesRecidive()).thenReturn(List.of(alerte));

        mockMvc.perform(get("/api/sanctions/alertes-recidive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeId").value("emp-1"))
                .andExpect(jsonPath("$[0].nombreSanctions").value(3));
    }
}
