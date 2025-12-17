package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.DashboardParSite;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardParAgence.class)
@AutoConfigureMockMvc(addFilters = false) // 🔥 Sécurité désactivée
class DashboardParAgenceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardParSite dashboardParSite;

    // ✅ Sécurité
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    // ---------------- GET DASHBOARD PAR AGENCE ----------------

    @Test
    void get_dashboard_par_agence_ok() throws Exception {

        Map<String, Map<String, Long>> stats = Map.of(
                "Dakar", Map.of("total", 10L, "present", 8L, "absent", 2L),
                "Thies", Map.of("total", 5L, "present", 3L, "absent", 2L)
        );

        when(dashboardParSite.getDashboardStatsBySite()).thenReturn(stats);

        mockMvc.perform(get("/api/dashboard_par_agence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Dakar.total").value(10))
                .andExpect(jsonPath("$.Dakar.present").value(8))
                .andExpect(jsonPath("$.Dakar.absent").value(2))
                .andExpect(jsonPath("$.Thies.total").value(5));
    }

}
