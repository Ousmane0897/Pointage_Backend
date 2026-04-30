package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.RhAbsenceDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.RhAbsenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RhAbsenceController.class)
@AutoConfigureMockMvc(addFilters = false)
class RhAbsenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RhAbsenceService rhAbsenceService;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private RhAbsenceDto buildDto(String id) {
        RhAbsenceDto dto = new RhAbsenceDto();
        dto.setId(id);
        dto.setEmployeId("emp-1");
        dto.setMotif("Maladie");
        return dto;
    }

    @Test
    void get_all_ok() throws Exception {
        when(rhAbsenceService.getAll()).thenReturn(List.of(buildDto("abs-1")));

        mockMvc.perform(get("/api/rh/absences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("abs-1"));
    }

    @Test
    void get_by_id_ok() throws Exception {
        when(rhAbsenceService.getById("abs-1")).thenReturn(buildDto("abs-1"));

        mockMvc.perform(get("/api/rh/absences/abs-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeId").value("emp-1"));
    }

    @Test
    void get_by_employe_ok() throws Exception {
        when(rhAbsenceService.getByEmployeId("emp-1")).thenReturn(List.of(buildDto("abs-1")));

        mockMvc.perform(get("/api/rh/absences/employe/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("abs-1"));
    }

    @Test
    void create_ok() throws Exception {
        RhAbsenceDto input = buildDto(null);
        when(rhAbsenceService.create(any(RhAbsenceDto.class))).thenReturn(buildDto("abs-new"));

        mockMvc.perform(post("/api/rh/absences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("abs-new"));
    }

    @Test
    void update_ok() throws Exception {
        RhAbsenceDto input = buildDto("abs-1");
        when(rhAbsenceService.update(eq("abs-1"), any(RhAbsenceDto.class))).thenReturn(input);

        mockMvc.perform(put("/api/rh/absences/abs-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abs-1"));
    }

    @Test
    void delete_ok() throws Exception {
        doNothing().when(rhAbsenceService).delete("abs-1");

        mockMvc.perform(delete("/api/rh/absences/abs-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void upload_justificatif_ok() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "fichier", "doc.pdf", "application/pdf", "pdf-content".getBytes());
        when(rhAbsenceService.uploadJustificatif(eq("abs-1"), any())).thenReturn(buildDto("abs-1"));

        mockMvc.perform(multipart("/api/rh/absences/abs-1/justificatif").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abs-1"));
    }

    @Test
    void download_justificatif_ok() throws Exception {
        com.example.Pointage_Cleanic.Dto.PieceJustificativeDto pj =
                new com.example.Pointage_Cleanic.Dto.PieceJustificativeDto();
        pj.setMimeType("application/pdf");
        pj.setNom("doc.pdf");

        RhAbsenceDto dto = buildDto("abs-1");
        dto.setPieceJustificative(pj);

        when(rhAbsenceService.getById("abs-1")).thenReturn(dto);
        when(rhAbsenceService.downloadJustificatif("abs-1")).thenReturn("pdf-bytes".getBytes());

        mockMvc.perform(get("/api/rh/absences/abs-1/justificatif"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"doc.pdf\""));
    }
}