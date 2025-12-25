package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.Dto.ImportEmployeResponse;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.EmployeCompletService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeCompletController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmployeCompletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeCompletService employeCompletService;

    @MockBean
    private com.example.Pointage_Cleanic.repositories.EmployeCompletRepository employeCompletRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // ✅ Sécurité
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;


    // ============================================
    // CREATE EMPLOYEE
    // ============================================

    @Test
    void should_create_employe() throws Exception {

        EmployeCompletDto dto = new EmployeCompletDto();
        dto.setPrenom("Mamadou");
        dto.setNom("Diop");

        EmployeComplet employe = new EmployeComplet();

        Mockito.when(employeCompletService.create(Mockito.any(), Mockito.any()))
                .thenReturn(employe);

        MockMultipartFile employeJson = new MockMultipartFile(
                "employe", "employe.json", "application/json",
                objectMapper.writeValueAsBytes(dto));

        MockMultipartFile photo = new MockMultipartFile(
                "photo", "photo.jpg", "image/jpeg", new byte[1]); // 🔥 important

        mockMvc.perform(multipart("/api/employe-complet/employe")
                        .file(employeJson)
                        .file(photo))
                .andExpect(status().isOk());
    }


    // ============================================
    // IMPORT EXCEL
    // ============================================

    @Test
    void should_import_excel() throws Exception {

        ImportEmployeResponse response =
                new ImportEmployeResponse(List.of(), List.of());

        Mockito.when(employeCompletService.importMany(Mockito.any()))
                .thenReturn(response);

        // 🔥 Envoyer un vrai tableau JSON conforme
        EmployeCompletDto dto = new EmployeCompletDto();
        List<EmployeCompletDto> body = List.of(dto);

        mockMvc.perform(post("/api/employe-complet/import-excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk());
    }


    // ============================================
    // DELETE
    // ============================================

    @Test
    void should_delete_employe() throws Exception {

        mockMvc.perform(delete("/api/employe-complet/by-agent/AG-001"))
                .andExpect(status().is2xxSuccessful());

        Mockito.verify(employeCompletService).delete("AG-001");
    }


    // ============================================
    // UPDATE EMPLOYEE
    // ============================================

    @Test
    void should_update_employe() throws Exception {

        EmployeCompletDto dto = new EmployeCompletDto();
        EmployeComplet employe = new EmployeComplet();

        Mockito.when(employeCompletService.update(
                        Mockito.eq("AG-001"),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(employe);

        MockMultipartFile employeJson = new MockMultipartFile(
                "employe", "employe.json", "application/json",
                objectMapper.writeValueAsBytes(dto));

        MockMultipartFile photo = new MockMultipartFile(
                "photo", "photo.jpg", "image/jpeg", new byte[1]);

        mockMvc.perform(multipart("/api/employe-complet/complet/AG-001")
                        .file(employeJson)
                        .file(photo)
                        .with(req -> {
                            req.setMethod("PUT");
                            return req;
                        }))
                .andExpect(status().isOk());
    }

}
