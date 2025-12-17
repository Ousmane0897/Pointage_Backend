package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Enum.StatutCommande;
import com.example.Pointage_Cleanic.entities.besoins.CollecteBesoins;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.CollecteBesoinService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CollecteBesoinController.class)
@AutoConfigureMockMvc(addFilters = false) // 🔥 Sécurité désactivée
class CollecteBesoinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CollecteBesoinService service;

    // ✅ Sécurité
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    // ---------------- CREATE ----------------

    @Test
    void creer_demande_ok() throws Exception {
        CollecteBesoins demande = new CollecteBesoins();

        when(service.creerDemande(any(), eq("ADMIN")))
                .thenReturn(demande);

        mockMvc.perform(post("/api/besoins")
                        .param("createdby", "ADMIN")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(demande)))
                .andExpect(status().isOk());
    }

    // ---------------- GET ALL ----------------

    @Test
    void lister_demandes() throws Exception {
        when(service.getAll()).thenReturn(List.of(new CollecteBesoins(), new CollecteBesoins()));

        mockMvc.perform(get("/api/besoins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------------- MOIS ACTUEL ----------------

    @Test
    void demandes_du_mois_actuel() throws Exception {
        when(service.getDemandesDuMois()).thenReturn(List.of(new CollecteBesoins()));

        mockMvc.perform(get("/api/besoins/moisActuel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------------- GET BY ID ----------------

    @Test
    void get_by_id() throws Exception {
        CollecteBesoins demande = new CollecteBesoins();
        demande.setId("123");

        when(service.getById("123")).thenReturn(demande);

        mockMvc.perform(get("/api/besoins/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("123"));
    }

    // ---------------- HISTORIQUE ----------------

    @Test
    void get_historique_modifications() throws Exception {
        when(service.getHistorique("123"))
                .thenReturn(List.of("log1", "log2"));

        mockMvc.perform(get("/api/besoins/historique-modifications/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------------- PAR DESTINATION ----------------

    @Test
    void par_destination() throws Exception {
        when(service.getByDestination("Dakar"))
                .thenReturn(List.of(new CollecteBesoins()));

        mockMvc.perform(get("/api/besoins/destination/Dakar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------------- UPDATE STATUT ----------------

    @Test
    void modifier_statut() throws Exception {
        CollecteBesoins demande = new CollecteBesoins();

        when(service.updateStatut(eq("123"), eq(StatutCommande.EN_COURS), eq("SUPERVISEUR")))
                .thenReturn(demande);

        mockMvc.perform(patch("/api/besoins/statut/123")
                        .contentType("application/json")
                        .content("""
                                {
                                  "statut": "EN_COURS",
                                  "modifiedBy": "SUPERVISEUR"
                                }
                                """))
                .andExpect(status().isOk());
    }

    // ---------------- HISTORIQUE LIVRAISONS ----------------

    @Test
    void get_historiques_livraisons() throws Exception {
        when(service.getHistoriques())
                .thenReturn(List.of(new CollecteBesoins()));

        mockMvc.perform(get("/api/besoins/historique-livraisons"))
                .andExpect(status().isOk());
    }

    // ---------------- UPDATE DEMANDE ----------------

    @Test
    void modifier_demande() throws Exception {
        CollecteBesoins demande = new CollecteBesoins();

        when(service.modifierDemande(eq("123"), any(), eq("ADMIN")))
                .thenReturn(demande);

        mockMvc.perform(put("/api/besoins/123")
                        .param("modifiedBy", "ADMIN")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(demande)))
                .andExpect(status().isOk());
    }
}
