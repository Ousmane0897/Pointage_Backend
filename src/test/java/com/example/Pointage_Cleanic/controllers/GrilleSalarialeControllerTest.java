package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.CategorieProfessionnelleDto;
import com.example.Pointage_Cleanic.Enum.RegimeIpres;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.CategorieProfessionnelleService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GrilleSalarialeController.class)
@AutoConfigureMockMvc(addFilters = false)
class GrilleSalarialeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CategorieProfessionnelleService categorieProfessionnelleService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private CategorieProfessionnelleDto buildDto(String id, String code) {
        CategorieProfessionnelleDto dto = new CategorieProfessionnelleDto();
        dto.setId(id);
        dto.setCode(code);
        dto.setLibelle("Cadre");
        dto.setSalaireBase(500_000L);
        dto.setRegimeIpres(RegimeIpres.REGIME_COMPLEMENTAIRE);
        dto.setActif(true);
        return dto;
    }

    @Test
    void get_all_ok() throws Exception {
        when(categorieProfessionnelleService.getAll(any(), any(), any()))
                .thenReturn(List.of(buildDto("cat-1", "CADRE")));

        mockMvc.perform(get("/api/grille-salariale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CADRE"));
    }

    @Test
    void get_all_with_filters_ok() throws Exception {
        when(categorieProfessionnelleService.getAll(eq("cad"), eq(RegimeIpres.REGIME_COMPLEMENTAIRE), eq(true)))
                .thenReturn(List.of(buildDto("cat-1", "CADRE")));

        mockMvc.perform(get("/api/grille-salariale")
                        .param("q", "cad")
                        .param("regimeIpres", "REGIME_COMPLEMENTAIRE")
                        .param("actif", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cat-1"));
    }

    @Test
    void get_by_id_ok() throws Exception {
        when(categorieProfessionnelleService.getById("cat-1"))
                .thenReturn(buildDto("cat-1", "CADRE"));

        mockMvc.perform(get("/api/grille-salariale/cat-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Cadre"));
    }

    @Test
    void create_ok() throws Exception {
        when(categorieProfessionnelleService.create(any(CategorieProfessionnelleDto.class)))
                .thenReturn(buildDto("cat-new", "EMPLOYE"));

        mockMvc.perform(post("/api/grille-salariale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto(null, "EMPLOYE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("cat-new"));
    }

    @Test
    void update_ok() throws Exception {
        CategorieProfessionnelleDto input = buildDto("cat-1", "CADRE");
        when(categorieProfessionnelleService.update(eq("cat-1"), any(CategorieProfessionnelleDto.class)))
                .thenReturn(input);

        mockMvc.perform(put("/api/grille-salariale/cat-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cat-1"));
    }

    @Test
    void delete_ok() throws Exception {
        doNothing().when(categorieProfessionnelleService).delete("cat-1");

        mockMvc.perform(delete("/api/grille-salariale/cat-1"))
                .andExpect(status().isNoContent());
    }
}