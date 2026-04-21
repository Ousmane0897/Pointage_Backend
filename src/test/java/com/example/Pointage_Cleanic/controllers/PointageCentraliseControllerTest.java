package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.PointageCentraliseDto;
import com.example.Pointage_Cleanic.Dto.ResumeJourneeDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.PointageCentraliseService;
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

@WebMvcTest(PointageCentraliseController.class)
@AutoConfigureMockMvc(addFilters = false)
class PointageCentraliseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PointageCentraliseService pointageCentraliseService;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void get_pointages_no_filters_ok() throws Exception {
        PointageCentraliseDto dto = new PointageCentraliseDto();
        dto.setNom("Diop");
        dto.setPrenom("Amadou");
        dto.setStatut("PRESENT");

        when(pointageCentraliseService.getPointages(
                isNull(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/pointages-centralises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nom").value("Diop"))
                .andExpect(jsonPath("$.content[0].statut").value("PRESENT"));
    }

    @Test
    void get_pointages_with_date_ok() throws Exception {
        PointageCentraliseDto dto = new PointageCentraliseDto();
        dto.setNom("Fall");
        dto.setStatut("ABSENT");

        when(pointageCentraliseService.getPointages(
                any(LocalDate.class), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/pointages-centralises")
                        .param("date", "2026-04-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nom").value("Fall"));
    }

    @Test
    void get_resume_ok() throws Exception {
        ResumeJourneeDto resume = new ResumeJourneeDto();
        resume.setTotalEmployes(10);
        resume.setPresents(8);
        resume.setAbsents(2);

        when(pointageCentraliseService.getResume(isNull())).thenReturn(resume);

        mockMvc.perform(get("/api/pointages-centralises/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployes").value(10))
                .andExpect(jsonPath("$.presents").value(8))
                .andExpect(jsonPath("$.absents").value(2));
    }

    @Test
    void get_resume_with_date_ok() throws Exception {
        ResumeJourneeDto resume = new ResumeJourneeDto();
        resume.setTotalEmployes(5);
        resume.setPresents(3);

        when(pointageCentraliseService.getResume(any(LocalDate.class))).thenReturn(resume);

        mockMvc.perform(get("/api/pointages-centralises/resume")
                        .param("date", "2026-04-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployes").value(5));
    }
}