package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreeDto;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.exception.StockOperationException;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.stockv2.BonEntreeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BonEntreeController.class)
@AutoConfigureMockMvc(addFilters = false)
class BonEntreeControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private BonEntreeService service;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private static final String CREATE_BODY = """
            { "type": "ACHAT_FOURNISSEUR", "siteDestinationId": "siteA",
              "lignes": [ { "produitId": "p1", "quantite": 10 } ] }
            """;

    @Test
    void post_bon_entree_retourne_201() throws Exception {
        when(service.creer(any())).thenReturn(BonEntreeDto.builder()
                .id("1").reference("BE-20260618-001").type(TypeEntree.ACHAT_FOURNISSEUR)
                .statut(StatutBon.BROUILLON).build());

        mockMvc.perform(post("/api/stock/bons-entree")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("BE-20260618-001"))
                .andExpect(jsonPath("$.statut").value("BROUILLON"));
    }

    @Test
    void valider_stock_insuffisant_retourne_422() throws Exception {
        when(service.valider(eq("1"), any())).thenThrow(new StockOperationException("Stock insuffisant"));

        mockMvc.perform(post("/api/stock/bons-entree/1/valider")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("STOCK_OPERATION_ERROR"));
    }

    @Test
    void soumettre_transition_invalide_retourne_409() throws Exception {
        when(service.soumettre(eq("1"))).thenThrow(new StockConflitException("Statut invalide"));

        mockMvc.perform(post("/api/stock/bons-entree/1/soumettre"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("STOCK_CONFLICT"));
    }

    @Test
    void refuser_sans_commentaire_retourne_400() throws Exception {
        when(service.refuser(eq("1"), any())).thenThrow(new IllegalArgumentException("commentaire obligatoire"));

        mockMvc.perform(post("/api/stock/bons-entree/1/refuser")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
