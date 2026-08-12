package com.example.Pointage_Cleanic.controllers.rh.gestionpersonnel;

import com.example.Pointage_Cleanic.Dto.rh.OrganigrammeArbreDto;
import com.example.Pointage_Cleanic.Dto.rh.OrganigrammeNodeDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.rh.OrganigrammeService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrganigrammeController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrganigrammeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrganigrammeService organigrammeService;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void get_organigramme_all_ok() throws Exception {
        OrganigrammeNodeDto node = OrganigrammeNodeDto.builder()
                .id("id1")
                .nomComplet("DIOP Mamadou")
                .poste("Superviseur")
                .build();

        Mockito.when(organigrammeService.getOrganigramme("")).thenReturn(List.of(node));

        // La liste plate est sur /departements ; la base sert l'arbre (cf. get_arbre_ok).
        mockMvc.perform(get("/api/gestion-personnel/organigramme/departements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nomComplet").value("DIOP Mamadou"));
    }

    @Test
    void get_organigramme_with_departement_ok() throws Exception {
        OrganigrammeNodeDto node = OrganigrammeNodeDto.builder()
                .id("id2")
                .nomComplet("FALL Aissatou")
                .agence("Dakar")
                .build();

        Mockito.when(organigrammeService.getOrganigramme("Dakar")).thenReturn(List.of(node));

        mockMvc.perform(get("/api/gestion-personnel/organigramme/departements?departement=Dakar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agence").value("Dakar"));
    }

    @Test
    void get_arbre_ok() throws Exception {
        OrganigrammeArbreDto enfant = OrganigrammeArbreDto.builder()
                .id("id2")
                .nomComplet("FALL Aissatou")
                .sousOrdres(List.of())
                .build();

        OrganigrammeArbreDto racine = OrganigrammeArbreDto.builder()
                .id("id1")
                .nomComplet("DIOP Mamadou")
                .sousOrdres(List.of(enfant))
                .build();

        Mockito.when(organigrammeService.getArbre()).thenReturn(List.of(racine));

        mockMvc.perform(get("/api/gestion-personnel/organigramme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sousOrdres.length()").value(1));
    }
}