package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.rh.RhAbsenceDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.rh.RhAbsenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TempsPresencesAbsenceController.class)
@AutoConfigureMockMvc(addFilters = false)
class TempsPresencesAbsenceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RhAbsenceService rhAbsenceService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private RhAbsenceDto dto(String id) {
        return RhAbsenceDto.builder().id(id).employeId("emp-1").motif("Maladie").build();
    }

    @Test
    void search_renvoie_pageresponse() throws Exception {
        when(rhAbsenceService.search(any(), any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(dto("abs-1")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/temps-presences/absences?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("abs-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void create_multipart_sans_fichier() throws Exception {
        when(rhAbsenceService.create(any(RhAbsenceDto.class))).thenReturn(dto("abs-new"));

        MockMultipartFile absence = new MockMultipartFile(
                "absence", "", "application/json",
                objectMapper.writeValueAsString(dto(null)).getBytes());

        mockMvc.perform(multipart("/api/temps-presences/absences").file(absence))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("abs-new"));

        verify(rhAbsenceService, never()).uploadJustificatif(any(), any());
    }

    @Test
    void create_multipart_avec_fichier_appelle_upload() throws Exception {
        when(rhAbsenceService.create(any(RhAbsenceDto.class))).thenReturn(dto("abs-new"));
        when(rhAbsenceService.uploadJustificatif(eq("abs-new"), any())).thenReturn(dto("abs-new"));

        MockMultipartFile absence = new MockMultipartFile(
                "absence", "", "application/json",
                objectMapper.writeValueAsString(dto(null)).getBytes());
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "doc.pdf", "application/pdf", "pdf-content".getBytes());

        mockMvc.perform(multipart("/api/temps-presences/absences").file(absence).file(fichier))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("abs-new"));

        verify(rhAbsenceService).uploadJustificatif(eq("abs-new"), any());
    }

    @Test
    void delete_ok() throws Exception {
        mockMvc.perform(delete("/api/temps-presences/absences/abs-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getByEmployeId_renvoie_liste() throws Exception {
        when(rhAbsenceService.getByEmployeId("emp-1")).thenReturn(List.of(dto("abs-1")));

        mockMvc.perform(get("/api/temps-presences/absences/employe/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("abs-1"));
    }

    @Test
    void downloadJustificatif_renvoie_fichier() throws Exception {
        when(rhAbsenceService.getById("abs-1")).thenReturn(dto("abs-1"));
        when(rhAbsenceService.downloadJustificatif("abs-1")).thenReturn("pdf-bytes".getBytes());

        mockMvc.perform(get("/api/temps-presences/absences/abs-1/justificatif"))
                .andExpect(status().isOk())
                .andExpect(content().bytes("pdf-bytes".getBytes()));
    }
}
