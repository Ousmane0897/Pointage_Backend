package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Agence;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.AgencesServices;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgencesController.class)
@AutoConfigureMockMvc(addFilters = false) // 🔥 Sécurité désactivée
class AgencesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgencesServices agencesServices;

    @MockBean
    private AgencesRepository agencesRepository;

    // ✅ Sécurité
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    // ---------------- CREATE ----------------

    @Test
    void create_agence_should_return_201() throws Exception {
        Agence agence = new Agence();
        agence.setNom("Dakar");

        when(agencesServices.save(any(Agence.class))).thenReturn(agence);

        mockMvc.perform(post("/api/agences")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(agence)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Dakar"));
    }

    // ---------------- GET ALL ----------------

    @Test
    void get_all_agences_should_return_200() throws Exception {
        when(agencesServices.getAll()).thenReturn(List.of(new Agence(), new Agence()));

        mockMvc.perform(get("/api/agences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------------- GET BY NOM ----------------

    @Test
    void get_by_nom_found() throws Exception {
        Agence agence = new Agence();
        agence.setNom("Dakar");

        when(agencesServices.getByNom("Dakar")).thenReturn(agence);

        mockMvc.perform(get("/api/agences/nom")
                        .param("nomAgence", "Dakar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Dakar"));
    }

    @Test
    void get_by_nom_not_found() throws Exception {
        when(agencesServices.getByNom("X")).thenReturn(null);

        mockMvc.perform(get("/api/agences/nom")
                        .param("nomAgence", "X"))
                .andExpect(status().isNotFound());
    }

    // ---------------- DELETE ----------------

    @Test
    void delete_agence_ok() throws Exception {
        Agence agence = new Agence();
        agence.setNom("Dakar");

        when(agencesServices.getByNom("Dakar")).thenReturn(agence);

        mockMvc.perform(delete("/api/agences/Dakar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Deleted").value(true));

        verify(agencesRepository).delete(agence);
    }

    // ---------------- EMPLOYES PAR SITE ----------------

    @Test
    void get_employees_by_site() throws Exception {
        when(agencesServices.EmployeeParAgence("Dakar"))
                .thenReturn(List.of(new Employe(), new Employe()));

        mockMvc.perform(get("/api/agences/site")
                        .param("nomAgence", "Dakar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------------- SITES ----------------

    @Test
    void get_all_sites() throws Exception {
        when(agencesServices.getAllSiteNames())
                .thenReturn(List.of("Dakar", "Thies"));

        mockMvc.perform(get("/api/agences/sites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------------- NOMBRE EMPLOYES ----------------

    @Test
    void get_number_of_employees() throws Exception {
        when(agencesServices.getNumberofEmployeesInOneAgence("Dakar"))
                .thenReturn(10);

        mockMvc.perform(get("/api/agences/getNumberofEmployeesInOneAgence")
                        .param("nomAgence", "Dakar"))
                .andExpect(status().isOk())
                .andExpect(content().string("10"));
    }

    // ---------------- JOURS OUVERTURE ----------------

    @Test
    void get_jours_ouverture_ok() throws Exception {

        when(agencesServices.getJoursOuvertureByNom("Dakar"))
                .thenReturn(Optional.of("Lundi-Vendredi"));

        mockMvc.perform(get("/api/agences/Dakar"))
                .andExpect(status().isOk())
                .andExpect(content().string("Lundi-Vendredi"));
    }


    @Test
    void get_jours_ouverture_not_found() throws Exception {

        when(agencesServices.getJoursOuvertureByNom("X"))
                .thenReturn(Optional.empty()); // ✅ JAMAIS null

        mockMvc.perform(get("/api/agences/X"))
                .andExpect(status().isNotFound());
    }


    // ---------------- UPDATE ----------------

    @Test
    void update_agence_ok() throws Exception {
        Agence existing = new Agence();
        existing.setNom("Dakar");

        Agence updated = new Agence();
        updated.setNom("Dakar Centre");

        when(agencesServices.getByNom("Dakar")).thenReturn(existing);
        when(agencesServices.save(any(Agence.class))).thenReturn(updated);

        mockMvc.perform(put("/api/agences/Dakar")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Dakar Centre"));
    }
}
