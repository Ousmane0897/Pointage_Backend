package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ConsommationPlafondDto;
import com.example.Pointage_Cleanic.Dto.stockv2.PlafondDto;
import com.example.Pointage_Cleanic.Enum.stockv2.GranularitePlafond;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.stockv2.PlafondService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlafondController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlafondControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PlafondService service;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void post_plafond_retourne_201() throws Exception {
        when(service.creer(any())).thenReturn(PlafondDto.builder()
                .id("1").siteId("siteA").granularite(GranularitePlafond.PRODUIT)
                .cibleId("p1").plafondMensuel(100).actif(true).build());

        String body = """
                { "siteId": "siteA", "granularite": "PRODUIT", "cibleId": "p1",
                  "plafondMensuel": 100, "actif": true }
                """;

        mockMvc.perform(post("/api/stock/plafonds")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.granularite").value("PRODUIT"));
    }

    @Test
    void get_consommation_retourne_jauges() throws Exception {
        when(service.consommation(eq("2026-06"), any())).thenReturn(List.of(
                ConsommationPlafondDto.builder()
                        .plafondId("1").siteId("siteA").granularite(GranularitePlafond.PRODUIT)
                        .cibleId("p1").plafondMensuel(100).consomme(120).pourcentage(120).depassement(true)
                        .mois("2026-06").build()));

        mockMvc.perform(get("/api/stock/plafonds/consommation").param("mois", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].depassement").value(true))
                .andExpect(jsonPath("$[0].pourcentage").value(120.0));
    }
}
