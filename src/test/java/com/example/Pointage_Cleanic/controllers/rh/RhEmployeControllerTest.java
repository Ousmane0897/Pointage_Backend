package com.example.Pointage_Cleanic.controllers.rh;

import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.Dto.rh.RhEmployeStatutRequest;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.rh.RhEmployeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RhEmployeController.class)
@AutoConfigureMockMvc(addFilters = false)
class RhEmployeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RhEmployeService rhEmployeService;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void get_all_employes_ok() throws Exception {
        EmployeCompletDto dto = new EmployeCompletDto();
        dto.setPrenom("Mamadou");
        dto.setNom("Diop");

        Mockito.when(rhEmployeService.list(0, 10, "", ""))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/employes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].prenom").value("Mamadou"));
    }

    @Test
    void get_employe_by_id_ok() throws Exception {
        EmployeCompletDto dto = new EmployeCompletDto();
        dto.setId("abc123");
        dto.setNom("Diop");

        Mockito.when(rhEmployeService.getById("abc123")).thenReturn(dto);

        mockMvc.perform(get("/api/employes/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Diop"));
    }

    @Test
    void get_employe_by_id_not_found() throws Exception {
        Mockito.when(rhEmployeService.getById("inconnu"))
                .thenThrow(new ResourceNotFoundException("Employé introuvable : inconnu"));

        mockMvc.perform(get("/api/employes/inconnu"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_employe_ok() throws Exception {
        EmployeCompletDto dto = new EmployeCompletDto();
        dto.setPrenom("Fatou");
        dto.setNom("Sall");

        EmployeCompletDto created = new EmployeCompletDto();
        created.setId("new1");

        Mockito.when(rhEmployeService.create(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(created);

        MockMultipartFile employeJson = new MockMultipartFile(
                "employe", "employe.json", "application/json",
                objectMapper.writeValueAsBytes(dto));

        mockMvc.perform(multipart("/api/employes").file(employeJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("new1"));
    }

    @Test
    void update_employe_ok() throws Exception {
        EmployeCompletDto dto = new EmployeCompletDto();
        dto.setNom("Diallo");

        EmployeCompletDto updated = new EmployeCompletDto();
        updated.setId("id1");
        updated.setNom("Diallo");

        Mockito.when(rhEmployeService.update(Mockito.eq("id1"), Mockito.any(), Mockito.any()))
                .thenReturn(updated);

        MockMultipartFile employeJson = new MockMultipartFile(
                "employe", "employe.json", "application/json",
                objectMapper.writeValueAsBytes(dto));

        mockMvc.perform(multipart("/api/employes/id1")
                        .file(employeJson)
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Diallo"));
    }

    @Test
    void delete_employe_ok() throws Exception {
        mockMvc.perform(delete("/api/employes/id1"))
                .andExpect(status().isNoContent());

        Mockito.verify(rhEmployeService).delete("id1");
    }

    @Test
    void update_statut_ok() throws Exception {
        RhEmployeStatutRequest request = new RhEmployeStatutRequest();
        request.setStatut(EmployeComplet.StatutEmploye.SORTIE);
        request.setMotifSortie("Démission");

        EmployeCompletDto dto = new EmployeCompletDto();
        dto.setStatut(EmployeComplet.StatutEmploye.SORTIE);

        Mockito.when(rhEmployeService.updateStatut(Mockito.eq("id1"), Mockito.any()))
                .thenReturn(dto);

        mockMvc.perform(put("/api/employes/id1/statut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SORTIE"));
    }
}
