package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.MouvementStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.stockv2.MouvementStockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MouvementStockController.class)
@AutoConfigureMockMvc(addFilters = false)
class MouvementStockControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private MouvementStockService service;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void post_mouvement_retourne_201() throws Exception {
        when(service.create(any())).thenReturn(MouvementStockDto.builder()
                .id("1").reference("MVT-20260617-001").type(TypeMouvement.ENTREE).motif(MotifMouvement.ACHAT)
                .build());

        String body = """
                { "produitId": "p1", "type": "ENTREE", "motif": "ACHAT", "quantite": 10 }
                """;

        mockMvc.perform(post("/api/stock/mouvements")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("MVT-20260617-001"));
    }
}
