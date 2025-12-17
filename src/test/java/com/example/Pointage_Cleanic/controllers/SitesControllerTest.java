package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Site;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.SiteRepository;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.SiteServices;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SitesController.class)
@AutoConfigureMockMvc(addFilters = false)
class SitesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SiteServices siteServices;

    @MockBean
    private SiteRepository siteRepository;

    // === Sécurité (OBLIGATOIRE en WebMvcTest)
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter; // 🔥 LE MANQUANT

    // =========================
    // POST /api/site
    // =========================
    @Test
    void create_site_ok() throws Exception {
        Site site = new Site();
        site.setNom("Site A");

        when(siteServices.save(any(Site.class))).thenReturn(site);

        mockMvc.perform(post("/api/site")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(site)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Site A"));
    }

    // =========================
    // GET /api/site
    // =========================
    @Test
    void get_all_sites_ok() throws Exception {
        when(siteServices.getAll()).thenReturn(List.of(new Site()));

        mockMvc.perform(get("/api/site"))
                .andExpect(status().isFound())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================
    // GET /api/site/{id}
    // =========================
    @Test
    void get_site_by_id_ok() throws Exception {
        Site site = new Site();
        site.setNom("Site B");

        when(siteServices.getById("1")).thenReturn(site);

        mockMvc.perform(get("/api/site/1"))
                .andExpect(status().isFound())
                .andExpect(jsonPath("$.nom").value("Site B"));
    }

    // =========================
    // DELETE /api/site/{id}
    // =========================
    @Test
    void delete_site_ok() throws Exception {
        Site site = new Site();

        when(siteRepository.findById("1")).thenReturn(Optional.of(site));
        doNothing().when(siteRepository).delete(site);

        mockMvc.perform(delete("/api/site/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Deleted").value(true));
    }

    @Test
    void delete_site_not_found() throws Exception {
        when(siteRepository.findById("404")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/site/404"))
                .andExpect(status().isNotFound());
    }

    // =========================
    // PUT /api/site/{id}
    // =========================
    @Test
    void update_site_ok() throws Exception {
        Site existing = new Site();
        existing.setNom("Old Site");

        Site updated = new Site();
        updated.setNom("New Site");

        when(siteRepository.findById("1")).thenReturn(Optional.of(existing));
        when(siteServices.save(any(Site.class))).thenReturn(updated);

        mockMvc.perform(put("/api/site/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("New Site"));
    }

    @Test
    void update_site_not_found() throws Exception {
        when(siteRepository.findById("404"))
                .thenThrow(new ResourceNotFoundException("Site not found"));

        mockMvc.perform(put("/api/site/404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Site())))
                .andExpect(status().isNotFound());
    }
}
