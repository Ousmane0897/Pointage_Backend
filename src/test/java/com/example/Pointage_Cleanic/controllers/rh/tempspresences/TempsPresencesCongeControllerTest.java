package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.rh.CompteursAValiderDto;
import com.example.Pointage_Cleanic.Dto.rh.DemandeCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.MonProfilCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.SoldeCongeDto;
import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.exception.CongeAccesRefuseException;
import com.example.Pointage_Cleanic.exception.CongeInvalideException;
import com.example.Pointage_Cleanic.exception.CongeTransitionInterditeException;
import com.example.Pointage_Cleanic.exception.GlobalExceptionHandler;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.rh.CongeIdentiteService;
import com.example.Pointage_Cleanic.services.rh.CongeWorkflowService;
import com.example.Pointage_Cleanic.services.rh.DemandeCongeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
@Import(GlobalExceptionHandler.class)
class TempsPresencesCongeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private DemandeCongeService demandeCongeService;
    @MockBean private CongeWorkflowService congeWorkflowService;
    @MockBean private CongeIdentiteService congeIdentiteService;
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private DemandeCongeDto dto(String id) {
        return DemandeCongeDto.builder().id(id).employeId("emp-1").motif("Congé annuel").build();
    }

    // ─── Soldes ───────────────────────────────────────────────────────────────

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
    void mon_solde_utilise_employe_du_jwt() throws Exception {
        when(congeIdentiteService.employeIdCourant()).thenReturn("emp-1");
        when(demandeCongeService.getSolde("emp-1"))
                .thenReturn(SoldeCongeDto.builder().employeId("emp-1").solde(22).build());

        mockMvc.perform(get("/api/temps-presences/conges/soldes/moi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solde").value(22));
    }

    @Test
    void mon_solde_sans_dossier_employe_renvoie_204() throws Exception {
        when(congeIdentiteService.employeIdCourant()).thenReturn(null);

        mockMvc.perform(get("/api/temps-presences/conges/soldes/moi"))
                .andExpect(status().isNoContent());
    }

    // ─── Listes ───────────────────────────────────────────────────────────────

    @Test
    void demandes_renvoie_pageresponse() throws Exception {
        when(demandeCongeService.searchDemandes(any(), any(), any(), any(), any(), any(), any(), any(),
                eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(dto("cg-1")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/temps-presences/conges/demandes?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("cg-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void mes_demandes_delegue_au_workflow() throws Exception {
        when(congeWorkflowService.mesDemandes(0, 10))
                .thenReturn(new PageImpl<>(List.of(dto("cg-1")), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/temps-presences/conges/demandes/mes-demandes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("cg-1"));
    }

    @Test
    void a_valider_transmet_le_niveau_demande() throws Exception {
        when(congeWorkflowService.demandesAValider(eq(NiveauValidationConge.RH), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of(dto("cg-2")), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/temps-presences/conges/demandes/a-valider?niveau=RH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("cg-2"));

        verify(congeWorkflowService).demandesAValider(NiveauValidationConge.RH, 0, 10);
    }

    @Test
    void compteurs_a_valider_renvoie_le_detail_par_niveau() throws Exception {
        when(congeWorkflowService.compteurs()).thenReturn(CompteursAValiderDto.builder()
                .total(3)
                .parNiveau(Map.of(
                        NiveauValidationConge.SUPERIEUR, 2L,
                        NiveauValidationConge.RH, 1L,
                        NiveauValidationConge.DIRECTION_GENERALE, 0L))
                .build());

        mockMvc.perform(get("/api/temps-presences/conges/demandes/a-valider/compteurs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.parNiveau.SUPERIEUR").value(2));
    }

    @Test
    void moi_renvoie_le_profil_metier() throws Exception {
        when(congeWorkflowService.monProfil()).thenReturn(MonProfilCongeDto.builder()
                .employeId("emp-1")
                .email("agent@cleanic.sn")
                .niveauxValidables(List.of(NiveauValidationConge.SUPERIEUR))
                .build());

        mockMvc.perform(get("/api/temps-presences/conges/moi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeId").value("emp-1"))
                .andExpect(jsonPath("$.niveauxValidables[0]").value("SUPERIEUR"));
    }

    @Test
    void detail_demande_passe_par_le_workflow() throws Exception {
        when(congeWorkflowService.getPourAppelant("cg-1")).thenReturn(dto("cg-1"));

        mockMvc.perform(get("/api/temps-presences/conges/demandes/cg-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cg-1"));
    }

    @Test
    void demandes_par_employe_renvoie_liste() throws Exception {
        when(demandeCongeService.getByEmployeId("emp-1")).thenReturn(List.of(dto("cg-1")));

        mockMvc.perform(get("/api/temps-presences/conges/demandes/employe/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cg-1"));
    }

    // ─── Écritures et transitions ─────────────────────────────────────────────

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
    void valider_transmet_le_commentaire_sans_identifiant_de_decideur() throws Exception {
        when(demandeCongeService.valider("cg-1", "OK")).thenReturn(dto("cg-1"));

        mockMvc.perform(post("/api/temps-presences/conges/demandes/cg-1/valider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("commentaire", "OK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cg-1"));

        // Le décideur vient du JWT côté service : aucun identifiant n'est accepté du client.
        verify(demandeCongeService).valider("cg-1", "OK");
    }

    @Test
    void approuver_reste_un_alias_de_valider() throws Exception {
        when(demandeCongeService.valider("cg-1", "OK")).thenReturn(dto("cg-1"));

        mockMvc.perform(post("/api/temps-presences/conges/demandes/cg-1/approuver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("commentaire", "OK"))))
                .andExpect(status().isOk());

        verify(demandeCongeService).valider("cg-1", "OK");
    }

    @Test
    void refuser_accepte_motif_ou_commentaire() throws Exception {
        when(demandeCongeService.refuser("cg-1", "Solde insuffisant")).thenReturn(dto("cg-1"));

        mockMvc.perform(post("/api/temps-presences/conges/demandes/cg-1/refuser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("motif", "Solde insuffisant"))))
                .andExpect(status().isOk());

        verify(demandeCongeService).refuser("cg-1", "Solde insuffisant");
    }

    @Test
    void delete_demande_ok() throws Exception {
        mockMvc.perform(delete("/api/temps-presences/conges/demandes/cg-1"))
                .andExpect(status().isNoContent());
    }

    // ─── Codes d'erreur du circuit ────────────────────────────────────────────

    @Test
    void valider_sans_habilitation_renvoie_403() throws Exception {
        when(demandeCongeService.valider(eq("cg-1"), any()))
                .thenThrow(new CongeAccesRefuseException("Non habilité"));

        mockMvc.perform(post("/api/temps-presences/conges/demandes/cg-1/valider")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CONGE_ACCES_REFUSE"));
    }

    @Test
    void valider_une_demande_deja_traitee_renvoie_409() throws Exception {
        when(demandeCongeService.valider(eq("cg-1"), any()))
                .thenThrow(new CongeTransitionInterditeException("Déjà traitée"));

        mockMvc.perform(post("/api/temps-presences/conges/demandes/cg-1/valider")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONGE_TRANSITION_INTERDITE"));
    }

    @Test
    void refuser_sans_motif_valable_renvoie_422() throws Exception {
        when(demandeCongeService.refuser(eq("cg-1"), any()))
                .thenThrow(new CongeInvalideException("Motif trop court"));

        mockMvc.perform(post("/api/temps-presences/conges/demandes/cg-1/refuser")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("CONGE_INVALIDE"))
                .andExpect(jsonPath("$.message").value("Motif trop court"));
    }
}
