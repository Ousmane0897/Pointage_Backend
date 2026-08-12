package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.MouvementStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.stockv2.MouvementStockService;
import com.example.Pointage_Cleanic.util.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les mouvements de stock sont en <b>lecture seule</b> via cette API.
 *
 * <p>Le test du {@code POST} n'a pas été supprimé mais retourné : il garde désormais la porte fermée.
 * Une simple absence de test se laisserait annuler sans bruit par quiconque rétablirait l'endpoint,
 * alors que celui-ci échouera — et dira pourquoi.
 */
@WebMvcTest(MouvementStockController.class)
@AutoConfigureMockMvc(addFilters = false)
class MouvementStockControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private MouvementStockService service;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    /**
     * ⚠ Garde-fou : la création directe d'un mouvement contournait le circuit de validation des bons
     * — stock fabriqué sans bon, sans validation et sans historique. Si ce test se met à échouer,
     * c'est qu'un endpoint d'écriture est réapparu : le corriger, pas l'adapter.
     */
    @Test
    void post_mouvement_est_refuse_405_ecriture_directe_retiree() throws Exception {
        String body = """
                { "produitId": "p1", "type": "ENTREE", "motif": "ACHAT", "quantite": 10 }
                """;

        mockMvc.perform(post("/api/stock/mouvements")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void get_mouvements_retourne_la_liste() throws Exception {
        when(service.list(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(MouvementStockDto.builder()
                        .id("1").reference("MVT-20260617-001")
                        .type(TypeMouvement.ENTREE).motif(MotifMouvement.ACHAT)
                        .build()), 1));

        mockMvc.perform(get("/api/stock/mouvements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference").value("MVT-20260617-001"));
    }

    @Test
    void get_mouvement_par_id_retourne_le_detail() throws Exception {
        when(service.getById(eq("1"))).thenReturn(MouvementStockDto.builder()
                .id("1").reference("MVT-20260617-001").type(TypeMouvement.SORTIE)
                .motif(MotifMouvement.CONSOMMATION).build());

        mockMvc.perform(get("/api/stock/mouvements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motif").value("CONSOMMATION"));
    }
}
