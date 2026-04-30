package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.ParametresPaieDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.ParametresPaieService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParametresPaieController.class)
@AutoConfigureMockMvc(addFilters = false)
class ParametresPaieControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ParametresPaieService parametresPaieService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private ParametresPaieDto build() {
        ParametresPaieDto dto = new ParametresPaieDto();
        dto.setId("p-1");
        dto.setTauxIpresGeneralSalarie(0.056);
        dto.setPlafondIpresGeneral(432_000L);
        dto.setMontantTrimfMensuel(900L);
        return dto;
    }

    @Test
    void get_ok() throws Exception {
        when(parametresPaieService.get()).thenReturn(build());

        mockMvc.perform(get("/api/parametres-paie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("p-1"))
                .andExpect(jsonPath("$.tauxIpresGeneralSalarie").value(0.056))
                .andExpect(jsonPath("$.montantTrimfMensuel").value(900));
    }

    @Test
    void update_ok() throws Exception {
        when(parametresPaieService.update(any(ParametresPaieDto.class), eq("u-1"), eq("Admin")))
                .thenReturn(build());

        mockMvc.perform(put("/api/parametres-paie")
                        .param("modifieParId", "u-1")
                        .param("modifieParNom", "Admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("p-1"));
    }
}