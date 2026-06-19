package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ComparatifCoutSitesDto;
import com.example.Pointage_Cleanic.Dto.stockv2.CoutProduitDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ParametrageValorisationDto;
import com.example.Pointage_Cleanic.Dto.stockv2.SyntheseMargesDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ValeurStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.stockv2.CoutMouvementService;
import com.example.Pointage_Cleanic.services.stockv2.CoutProduitService;
import com.example.Pointage_Cleanic.services.stockv2.CoutSiteService;
import com.example.Pointage_Cleanic.services.stockv2.MargesService;
import com.example.Pointage_Cleanic.services.stockv2.ParametrageValorisationService;
import com.example.Pointage_Cleanic.services.stockv2.TableauBordFinancierService;
import com.example.Pointage_Cleanic.services.stockv2.ValeurStockService;
import com.example.Pointage_Cleanic.services.stockv2.ValorisationChantierService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ValorisationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ValorisationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ParametrageValorisationService parametrageService;
    @MockBean private CoutProduitService coutProduitService;
    @MockBean private CoutMouvementService coutMouvementService;
    @MockBean private ValeurStockService valeurStockService;
    @MockBean private CoutSiteService coutSiteService;
    @MockBean private ValorisationChantierService chantierService;
    @MockBean private MargesService margesService;
    @MockBean private TableauBordFinancierService tableauBordService;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void get_parametrage_ok() throws Exception {
        when(parametrageService.get()).thenReturn(
                ParametrageValorisationDto.builder().methodeDefaut(MethodeValorisation.CUMP).build());
        mockMvc.perform(get("/api/stock/valorisation/parametrage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methodeDefaut").value("CUMP"));
    }

    @Test
    void put_parametrage_bind_le_body() throws Exception {
        when(parametrageService.update(any())).thenReturn(
                ParametrageValorisationDto.builder().methodeDefaut(MethodeValorisation.DERNIER_PRIX).build());
        mockMvc.perform(put("/api/stock/valorisation/parametrage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"methodeDefaut\":\"DERNIER_PRIX\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methodeDefaut").value("DERNIER_PRIX"));
    }

    @Test
    void couts_produits_retourne_page() throws Exception {
        when(coutProduitService.list(eq(0), eq(20), any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(
                        CoutProduitDto.builder().produitId("1").produitCode("P1").coutCourant(1000L)
                                .methodeEffective(MethodeValorisation.FIXE).build()), 1));
        mockMvc.perform(get("/api/stock/valorisation/couts-produits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].produitCode").value("P1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void valeur_stock_ok() throws Exception {
        when(valeurStockService.valeur(any(), any(), any())).thenReturn(
                ValeurStockDto.builder()
                        .kpis(ValeurStockDto.Kpis.builder().valeurTotale(50_000L).nbProduits(3).build())
                        .dateCalcul("2026-06-19T10:00:00").build());
        mockMvc.perform(get("/api/stock/valorisation/valeur-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.valeurTotale").value(50000));
    }

    @Test
    void marges_exige_les_dates() throws Exception {
        when(margesService.synthese(any(), any(), any())).thenReturn(
                SyntheseMargesDto.builder().margeGlobaleTotale(1000L).build());
        mockMvc.perform(get("/api/stock/valorisation/marges")
                        .param("dateDebut", "2026-06-01").param("dateFin", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.margeGlobaleTotale").value(1000));
    }

    @Test
    void cout_site_ok() throws Exception {
        when(coutSiteService.comparatif(any(), any(), any())).thenReturn(
                ComparatifCoutSitesDto.builder().coutTotalGlobal(11_000L).nbSitesSurconsommation(1).build());
        mockMvc.perform(get("/api/stock/valorisation/cout-site")
                        .param("dateDebut", "2026-06-01").param("dateFin", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coutTotalGlobal").value(11000));
    }
}
