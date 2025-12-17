package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Agence;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Planification;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.AgencesServices;
import com.example.Pointage_Cleanic.services.EmployeServices;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployesController.class)
@AutoConfigureMockMvc(addFilters = false) // 🔥 Sécurité désactivée
class EmployesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ===== MOCKS =====
    @MockBean private EmployeServices employeServices;
    @MockBean private EmployeRepository employeRepository;
    @MockBean private AgencesServices agencesServices;
    @MockBean private AgencesRepository agencesRepository;

    // ✅ Sécurité
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    // ===================== GET ALL =====================
    @Test
    void get_all_employes_ok() throws Exception {
        when(employeServices.getAll()).thenReturn(List.of(new Employe(), new Employe()));

        mockMvc.perform(get("/api/employe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ===================== GET BY CODE =====================
    @Test
    void get_employe_by_code_ok() throws Exception {
        Employe e = new Employe();
        e.setCodeSecret("ABC123");
        e.setNom("Diouf");

        when(employeServices.getBycodeSecret("ABC123")).thenReturn(e);

        mockMvc.perform(get("/api/employe/ABC123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Diouf"));
    }

    @Test
    void get_employe_by_code_not_found() throws Exception {
        when(employeServices.getBycodeSecret("XXX")).thenReturn(null);

        mockMvc.perform(get("/api/employe/XXX"))
                .andExpect(status().isNotFound());
    }

    // ===================== CREATE =====================
    @Test
    void create_employe_ok() throws Exception {
        Employe e = new Employe();
        e.setNom("Diouf");

        when(employeServices.save(any())).thenReturn(e);

        mockMvc.perform(post("/api/employe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(e)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Diouf"));
    }

    // ===================== DELETE =====================
    @Test
    void delete_employe_ok() throws Exception {
        Employe e = new Employe();
        when(employeServices.getBycodeSecret("ABC")).thenReturn(e);

        mockMvc.perform(delete("/api/employe/ABC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Deleted").value(true));

        verify(employeRepository).delete(e);
    }

    // ===================== UPDATE =====================
    @Test
    void update_employe_ok() throws Exception {
        Employe existing = new Employe();
        existing.setNom("Old");

        Employe updated = new Employe();
        updated.setNom("New");

        when(employeServices.getBycodeSecret("ABC")).thenReturn(existing);
        when(employeServices.save(any())).thenReturn(updated);

        mockMvc.perform(put("/api/employe/ABC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("New"));
    }

    // ===================== DEPLACEMENT =====================
    @Test
    void update_employe_deplacement_ok() throws Exception {
        Employe employe = new Employe();
        Agence depart = new Agence();
        Agence arrivee = new Agence();

        Planification plan = new Planification();
        plan.setNomSite("Dakar");
        plan.setSiteDestination(new String[]{"Thies"});
        plan.setPersonneRemplacee("Ali Fall");
        plan.setMatin(true);

        when(employeServices.getBycodeSecret("ABC")).thenReturn(employe);
        when(agencesServices.getByNom("Dakar")).thenReturn(depart);
        when(agencesServices.getByNom("Thies")).thenReturn(arrivee);
        when(employeServices.employeeRemplacee("Ali","Fall")).thenReturn(new Employe());
        when(employeServices.save(any())).thenReturn(employe);

        mockMvc.perform(put("/api/employe/deplacement/ABC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(plan)))
                .andExpect(status().isCreated());
    }

}
