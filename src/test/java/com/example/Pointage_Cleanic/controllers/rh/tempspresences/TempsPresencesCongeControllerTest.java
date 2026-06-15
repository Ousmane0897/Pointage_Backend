package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.rh.DemandeCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.SoldeCongeDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.rh.DemandeCongeService;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TempsPresencesCongeController.class)
@AutoConfigureMockMvc(addFilters = false)
class TempsPresencesCongeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private DemandeCongeService demandeCongeService;
    @MockBean private CurrentUserProvider currentUserProvider;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private DemandeCongeDto dto(String id) {
        return DemandeCongeDto.builder().id(id).employeId("emp-1").motif("Congé annuel").build();
    }

    @Test
    void soldes_sans_employeId_renvoie_tableau() throws Exception {
        when(demandeCongeService.getSoldes())
                .thenReturn(List.of(SoldeCongeDto.builder().employeId("emp-1").solde(18).build()));

        mockMvc.perform(get("/api/temps-presences/conges/soldes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeId").value("emp-1"))
                .andExpect(jsonPath("$[0].solde").value(18));
    }

    @Test
    void soldes_avec_employeId_renvoie_tableau_un_element() throws Exception {
        when(demandeCongeService.getSolde("emp-1"))
                .thenReturn(SoldeCongeDto.builder().employeId("emp-1").solde(12).build());

        mockMvc.perform(get("/api/temps-presences/conges/soldes?employeId=emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].solde").value(12));
    }

    @Test
    void solde_par_id_renvoie_objet() throws Exception {
        when(demandeCongeService.getSolde("emp-1"))
                .thenReturn(SoldeCongeDto.builder().employeId("emp-1").solde(12).build());

        mockMvc.perform(get("/api/temps-presences/conges/soldes/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solde").value(12));
    }

    @Test
    void demandes_renvoie_pageresponse() throws Exception {
        when(demandeCongeService.searchDemandes(any(), any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(dto("cg-1")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/temps-presences/conges/demandes?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("cg-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void create_demande_ok() throws Exception {
        when(demandeCongeService.create(any(DemandeCongeDto.class))).thenReturn(dto("cg-new"));

        mockMvc.perform(post("/api/temps-presences/conges/demandes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("cg-new"));
    }

    @Test
    void approuver_utilise_decideur_du_jwt() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn("usr-9");
        when(currentUserProvider.currentUserNom()).thenReturn("Ndiaye");
        when(demandeCongeService.approuver(eq("cg-1"), eq("usr-9"), eq("Ndiaye"), eq("OK")))
                .thenReturn(dto("cg-1"));

        mockMvc.perform(post("/api/temps-presences/conges/demandes/cg-1/approuver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("commentaire", "OK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cg-1"));

        verify(demandeCongeService).approuver("cg-1", "usr-9", "Ndiaye", "OK");
    }

    @Test
    void refuser_mappe_motif_sur_commentaire() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn("usr-9");
        when(currentUserProvider.currentUserNom()).thenReturn("Ndiaye");
        when(demandeCongeService.refuser(eq("cg-1"), eq("usr-9"), eq("Ndiaye"), eq("Solde insuffisant")))
                .thenReturn(dto("cg-1"));

        mockMvc.perform(post("/api/temps-presences/conges/demandes/cg-1/refuser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("motif", "Solde insuffisant"))))
                .andExpect(status().isOk());

        verify(demandeCongeService).refuser("cg-1", "usr-9", "Ndiaye", "Solde insuffisant");
    }

    @Test
    void delete_demande_ok() throws Exception {
        mockMvc.perform(delete("/api/temps-presences/conges/demandes/cg-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void demandes_par_employe_renvoie_liste() throws Exception {
        when(demandeCongeService.getByEmployeId("emp-1")).thenReturn(List.of(dto("cg-1")));

        mockMvc.perform(get("/api/temps-presences/conges/demandes/employe/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cg-1"));
    }
}
