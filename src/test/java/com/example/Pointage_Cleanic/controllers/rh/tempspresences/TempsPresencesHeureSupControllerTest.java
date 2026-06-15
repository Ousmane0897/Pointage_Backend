package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.rh.HeureSupplementaireDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.rh.HeureSupplementaireService;
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

@WebMvcTest(TempsPresencesHeureSupController.class)
@AutoConfigureMockMvc(addFilters = false)
class TempsPresencesHeureSupControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private HeureSupplementaireService heureSupplementaireService;
    @MockBean private CurrentUserProvider currentUserProvider;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private HeureSupplementaireDto dto(String id) {
        return HeureSupplementaireDto.builder().id(id).employeId("emp-1").nombreHeures(2.0).build();
    }

    @Test
    void search_renvoie_pageresponse() throws Exception {
        when(heureSupplementaireService.search(any(), any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(dto("hs-1")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/temps-presences/heures-supplementaires?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("hs-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void create_ok() throws Exception {
        when(heureSupplementaireService.create(any(HeureSupplementaireDto.class))).thenReturn(dto("hs-new"));

        mockMvc.perform(post("/api/temps-presences/heures-supplementaires")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("hs-new"));
    }

    @Test
    void valider_utilise_decideur_du_jwt() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn("usr-9");
        when(currentUserProvider.currentUserNom()).thenReturn("Ndiaye");
        when(heureSupplementaireService.valider(eq("hs-1"), eq("usr-9"), eq("Ndiaye"), eq("OK")))
                .thenReturn(dto("hs-1"));

        mockMvc.perform(post("/api/temps-presences/heures-supplementaires/hs-1/valider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("commentaire", "OK"))))
                .andExpect(status().isOk());

        verify(heureSupplementaireService).valider("hs-1", "usr-9", "Ndiaye", "OK");
    }

    @Test
    void refuser_mappe_motif_sur_commentaire() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn("usr-9");
        when(currentUserProvider.currentUserNom()).thenReturn("Ndiaye");
        when(heureSupplementaireService.refuser(eq("hs-1"), eq("usr-9"), eq("Ndiaye"), eq("Non justifié")))
                .thenReturn(dto("hs-1"));

        mockMvc.perform(post("/api/temps-presences/heures-supplementaires/hs-1/refuser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("motif", "Non justifié"))))
                .andExpect(status().isOk());

        verify(heureSupplementaireService).refuser("hs-1", "usr-9", "Ndiaye", "Non justifié");
    }

    @Test
    void delete_ok() throws Exception {
        mockMvc.perform(delete("/api/temps-presences/heures-supplementaires/hs-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getByEmployeId_renvoie_liste() throws Exception {
        when(heureSupplementaireService.getByEmployeId("emp-1")).thenReturn(List.of(dto("hs-1")));

        mockMvc.perform(get("/api/temps-presences/heures-supplementaires/employe/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("hs-1"));
    }
}
