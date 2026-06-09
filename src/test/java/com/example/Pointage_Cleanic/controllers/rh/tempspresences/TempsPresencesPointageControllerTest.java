package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.ResumeJourneeDto;
import com.example.Pointage_Cleanic.Dto.rh.PointageCentraliseDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.rh.PointageCentraliseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TempsPresencesPointageController.class)
@AutoConfigureMockMvc(addFilters = false)
class TempsPresencesPointageControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PointageCentraliseService pointageCentraliseService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private PointageCentraliseDto dto() {
        return PointageCentraliseDto.builder().id("p1").employeId("emp-1").statut("PRESENT").build();
    }

    @Test
    void pointages_par_jour_renvoie_pageresponse() throws Exception {
        when(pointageCentraliseService.getPointages(isNull(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(dto()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/temps-presences/pointages?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("p1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pointages_avec_intervalle_utilise_range() throws Exception {
        when(pointageCentraliseService.getPointagesRange(
                eq(LocalDate.of(2026, 6, 1)), eq(LocalDate.of(2026, 6, 30)),
                any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(dto()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/temps-presences/pointages?dateDebut=2026-06-01&dateFin=2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].employeId").value("emp-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void resume_ok() throws Exception {
        when(pointageCentraliseService.getResume(any()))
                .thenReturn(ResumeJourneeDto.builder().totalEmployes(10).presents(8).absents(2).build());

        mockMvc.perform(get("/api/temps-presences/pointages/resume?date=2026-06-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployes").value(10))
                .andExpect(jsonPath("$.presents").value(8));
    }
}
