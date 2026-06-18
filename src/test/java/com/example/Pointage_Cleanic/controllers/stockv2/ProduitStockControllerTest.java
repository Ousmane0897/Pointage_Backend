package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ProduitBulkResponse;
import com.example.Pointage_Cleanic.Dto.stockv2.ProduitDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.stockv2.ProduitStockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProduitStockController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProduitStockControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProduitStockService service;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void create_multipart_retourne_201() throws Exception {
        when(service.create(any(), any(), any()))
                .thenReturn(ProduitDto.builder().id("1").code("P1").libelle("Savon").build());

        MockMultipartFile produit = new MockMultipartFile(
                "produit", "p.json", MediaType.APPLICATION_JSON_VALUE,
                "{\"code\":\"P1\",\"libelle\":\"Savon\"}".getBytes());

        mockMvc.perform(multipart("/api/stock/produits").file(produit))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("P1"));
    }

    @Test
    void bulk_succes_total_retourne_200() throws Exception {
        when(service.importBulk(any())).thenReturn(ProduitBulkResponse.builder()
                .total(2).inserted(2).failed(0).insertedIds(List.of("a", "b")).errors(List.of()).build());

        mockMvc.perform(post("/api/stock/produits/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produits\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(2));
    }

    @Test
    void bulk_echec_retourne_422() throws Exception {
        when(service.importBulk(any())).thenReturn(ProduitBulkResponse.builder()
                .total(1).inserted(0).failed(1).insertedIds(List.of())
                .errors(List.of(ProduitBulkResponse.BulkError.builder()
                        .numeroLigne(1).field("code").message("Code obligatoire").build()))
                .build());

        mockMvc.perform(post("/api/stock/produits/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produits\":[{}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0].numeroLigne").value(1));
    }

    @Test
    void actifs_retourne_la_liste() throws Exception {
        when(service.listActifs()).thenReturn(List.of(ProduitDto.builder().id("1").code("P1").build()));
        mockMvc.perform(get("/api/stock/produits/actifs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("P1"));
    }
}
