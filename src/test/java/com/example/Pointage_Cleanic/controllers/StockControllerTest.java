package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.SortieBatchRequest;
import com.example.Pointage_Cleanic.Enum.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stock.MouvementEntreeStock;
import com.example.Pointage_Cleanic.entities.stock.MouvementSortieStock;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.StockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StockController.class)
@AutoConfigureMockMvc(addFilters = false)
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StockService stockService;

    // === Sécurité (OBLIGATOIRE en WebMvcTest)
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter; // 🔥 LE MANQUANT

    // =========================
    // POST /api/stock/mouvement
    // =========================
    @Test
    void create_mouvement_ok() throws Exception {
        MouvementEntreeStock mouvement = new MouvementEntreeStock();
        mouvement.setCodeProduit("P001");

        when(stockService.enregistrerMouvement(any())).thenReturn(mouvement);

        mockMvc.perform(post("/api/stock/mouvement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mouvement)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeProduit").value("P001"));
    }

    // =========================
    // GET /api/stock/produit/quantite/{code}
    // =========================
    @Test
    void get_stock_quantite_ok() throws Exception {
        when(stockService.getStockCurrent("P001")).thenReturn(25);

        mockMvc.perform(get("/api/stock/produit/quantite/P001"))
                .andExpect(status().isOk())
                .andExpect(content().string("25"));
    }

    // =========================
    // GET /api/stock/produit/{id}/historique
    // =========================
    @Test
    void get_historique_ok() throws Exception {
        when(stockService.getHistorique("P001"))
                .thenReturn(List.of(new MouvementEntreeStock()));

        mockMvc.perform(get("/api/stock/produit/P001/historique"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================
    // GET /api/stock/entrees
    // =========================
    @Test
    void get_all_entrees_ok() throws Exception {
        when(stockService.getAllEntree())
                .thenReturn(List.of(new MouvementEntreeStock()));

        mockMvc.perform(get("/api/stock/entrees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================
    // GET /api/stock/sorties
    // =========================
    @Test
    void get_all_sorties_ok() throws Exception {
        when(stockService.getAllSorties())
                .thenReturn(List.of(new MouvementSortieStock()));

        mockMvc.perform(get("/api/stock/sorties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================
    // POST /api/stock/sortie/simple
    // =========================
    @Test
    void sortie_simple_ok() throws Exception {
        MouvementSortieStock sortie = new MouvementSortieStock();
        sortie.setCodeProduit("P001");

        when(stockService.sortieSimple(any())).thenReturn(sortie);

        mockMvc.perform(post("/api/stock/sortie/simple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sortie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeProduit").value("P001"));
    }

    // =========================
    // POST /api/stock/sorties/batch
    // =========================
    @Test
    void sortie_batch_ok() throws Exception {
        SortieBatchRequest request = new SortieBatchRequest();
        request.setDestination("Agence A");
        request.setResponsable("Admin");
        request.setTypeMouvement(TypeMouvement.SORTIE);

        when(stockService.sortieBatch(
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of(new MouvementSortieStock()));

        mockMvc.perform(post("/api/stock/sorties/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================
    // GET /api/stock/suivi
    // =========================
    @Test
    void get_suivi_stock_ok() throws Exception {
        when(stockService.getSuiviGlobal())
                .thenReturn(List.of(Map.of("stockActuel", 50)));

        mockMvc.perform(get("/api/stock/suivi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================
    // GET statistiques
    // =========================
    @Test
    void get_stats_produit_destination_mois_ok() throws Exception {
        when(stockService.getQuantiteProduitParDestinationParMois("Savon", "Agence A", 2025))
                .thenReturn(Map.of("labels", List.of("Janvier"), "data", List.of(10)));

        mockMvc.perform(get("/api/stock/stats/produit-destination-mois/Savon/Agence A/2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels").exists());
    }
}
