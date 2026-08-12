package com.example.Pointage_Cleanic.controllers.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.AffectationAgentDto;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.TerrainConflitException;
import com.example.Pointage_Cleanic.exception.TerrainExceptionHandler;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.terrain.PlanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP figé côté frontend pour l'annulation motivée d'une affectation
 * et les compteurs par statut.
 */
@WebMvcTest(PlanningController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TerrainExceptionHandler.class) // advice scopé controllers.terrain : pas auto-détecté par le slice
class PlanningControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlanningService service;

    // === Sécurité (OBLIGATOIRE en WebMvcTest même avec addFilters=false)
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private static final String URL = "/api/terrain/planning/affectations/{id}/annuler";
    private static final String BODY = """
            { "motif": "Client a reporté l'intervention" }
            """;

    private AffectationAgentDto annulee() {
        return AffectationAgentDto.builder()
                .id("a1")
                .statut(StatutAffectation.ANNULEE)
                .motifAnnulation("Client a reporté l'intervention")
                .dateAnnulation(LocalDateTime.of(2026, 7, 21, 10, 30))
                .annuleParNom("Awa Diop")
                .build();
    }

    @Test
    void annuler_ok_renvoie_l_affectation_avec_les_trois_champs() throws Exception {
        when(service.annuler(eq("a1"), any())).thenReturn(annulee());

        mockMvc.perform(post(URL, "a1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ANNULEE"))
                .andExpect(jsonPath("$.motifAnnulation").value("Client a reporté l'intervention"))
                .andExpect(jsonPath("$.dateAnnulation").exists())
                .andExpect(jsonPath("$.annuleParNom").value("Awa Diop"));

        // Le motif transmis au service est bien celui du corps.
        verify(service).annuler("a1", "Client a reporté l'intervention");
    }

    @Test
    void annuler_transmet_null_quand_le_corps_est_absent() throws Exception {
        when(service.annuler(eq("a1"), any())).thenReturn(annulee());

        mockMvc.perform(post(URL, "a1")).andExpect(status().isOk());

        verify(service).annuler("a1", null);
    }

    @Test
    void statut_non_annulable_renvoie_409() throws Exception {
        when(service.annuler(eq("a1"), any()))
                .thenThrow(new TerrainConflitException("Annulation impossible : l'affectation est au statut EFFECTUEE"));

        mockMvc.perform(post(URL, "a1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void motif_invalide_renvoie_400() throws Exception {
        when(service.annuler(eq("a1"), any()))
                .thenThrow(new IllegalArgumentException("Le motif d'annulation est obligatoire (5 caractères minimum)"));

        mockMvc.perform(post(URL, "a1").contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void affectation_inexistante_renvoie_404() throws Exception {
        when(service.annuler(eq("zzz"), any()))
                .thenThrow(new ResourceNotFoundException("Affectation introuvable : zzz"));

        mockMvc.perform(post(URL, "zzz").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    /**
     * Le hard delete a été retiré : une affectation s'annule, elle ne se supprime pas.
     * Sans mapping DELETE, Spring répond 405 Method Not Allowed.
     */
    @Test
    void delete_affectation_n_existe_plus() throws Exception {
        mockMvc.perform(delete("/api/terrain/planning/affectations/{id}", "a1"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void stats_renvoie_les_cinq_statuts() throws Exception {
        Map<StatutAffectation, Long> stats = new LinkedHashMap<>();
        stats.put(StatutAffectation.PLANIFIEE, 18L);
        stats.put(StatutAffectation.EN_COURS, 5L);
        stats.put(StatutAffectation.EFFECTUEE, 15L);
        stats.put(StatutAffectation.ANNULEE, 4L);
        stats.put(StatutAffectation.REMPLACEE, 0L);
        when(service.stats(any(), any())).thenReturn(stats);

        mockMvc.perform(get("/api/terrain/planning/affectations/stats")
                        .param("dateDebut", "2026-07-01")
                        .param("dateFin", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PLANIFIEE").value(18))
                .andExpect(jsonPath("$.EN_COURS").value(5))
                .andExpect(jsonPath("$.EFFECTUEE").value(15))
                .andExpect(jsonPath("$.ANNULEE").value(4))
                .andExpect(jsonPath("$.REMPLACEE").value(0));
    }
}
