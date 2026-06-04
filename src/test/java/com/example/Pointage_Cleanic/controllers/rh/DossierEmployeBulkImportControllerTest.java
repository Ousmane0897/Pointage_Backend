package com.example.Pointage_Cleanic.controllers.rh;

import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeBulkImportResponse;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeImportError;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.rh.DossierEmployeService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests HTTP de l'endpoint POST /api/gestion-personnel/employes/bulk.
 * Vérifie le mapping des codes 200 / 207 / 400 / 422 selon le rapport renvoyé
 * par le service (lui-même mocké).
 */
@WebMvcTest(DossierEmployeController.class)
@AutoConfigureMockMvc(addFilters = false)
class DossierEmployeBulkImportControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean DossierEmployeService service;

    @MockBean MyUserDetailsService myUserDetailsService;
    @MockBean JwtUtil jwtUtil;
    @MockBean JwtRequestFilter jwtRequestFilter;

    private static final String PAYLOAD_DEUX_LIGNES = """
            {
              "strategieErreurs": "TOUT_OU_RIEN",
              "employes": [
                {"matricule":"M001","nom":"DIOP","prenom":"Awa","poste":"Agent",
                 "dateEntree":"2026-01-01","statut":"ACTIF","genre":"FEMME"},
                {"matricule":"M002","nom":"FALL","prenom":"Bob","poste":"Agent",
                 "dateEntree":"2026-01-01","statut":"ACTIF","genre":"HOMME"}
              ]
            }
            """;

    @Test
    void bulk_tout_valide_retourne_200() throws Exception {
        Mockito.when(service.importBulk(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new DossierEmployeBulkImportResponse(
                        2, 2, 0, List.of("id-1", "id-2"), List.of()));

        mockMvc.perform(post("/api/gestion-personnel/employes/bulk")
                        .contentType("application/json")
                        .content(PAYLOAD_DEUX_LIGNES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(2))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    void bulk_tout_ou_rien_avec_erreur_retourne_422() throws Exception {
        Mockito.when(service.importBulk(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new DossierEmployeBulkImportResponse(
                        2, 0, 1, List.of(),
                        List.of(new DossierEmployeImportError(
                                1, "M002", "nom", "CHAMP_OBLIGATOIRE",
                                "Le champ nom est obligatoire"))));

        mockMvc.perform(post("/api/gestion-personnel/employes/bulk")
                        .contentType("application/json")
                        .content(PAYLOAD_DEUX_LIGNES))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.inserted").value(0))
                .andExpect(jsonPath("$.errors[0].code").value("CHAMP_OBLIGATOIRE"));
    }

    @Test
    void bulk_importer_lignes_valides_avec_erreur_retourne_207() throws Exception {
        Mockito.when(service.importBulk(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new DossierEmployeBulkImportResponse(
                        2, 1, 1, List.of("id-1"),
                        List.of(new DossierEmployeImportError(
                                1, "M002", "nom", "CHAMP_OBLIGATOIRE",
                                "Le champ nom est obligatoire"))));

        String payload = PAYLOAD_DEUX_LIGNES.replace("TOUT_OU_RIEN", "IMPORTER_LIGNES_VALIDES");

        mockMvc.perform(post("/api/gestion-personnel/employes/bulk")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.inserted").value(1))
                .andExpect(jsonPath("$.insertedIds[0]").value("id-1"));
    }

    @Test
    void bulk_batch_vide_retourne_400() throws Exception {
        Mockito.when(service.importBulk(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("Le batch d'import ne peut pas être vide"));

        String payload = """
                {"strategieErreurs":"TOUT_OU_RIEN","employes":[]}
                """;

        mockMvc.perform(post("/api/gestion-personnel/employes/bulk")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void bulk_batch_trop_grand_retourne_400() throws Exception {
        Mockito.when(service.importBulk(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("Taille du batch supérieure à la limite autorisée : 1001 > 1000"));

        mockMvc.perform(post("/api/gestion-personnel/employes/bulk")
                        .contentType("application/json")
                        .content(PAYLOAD_DEUX_LIGNES))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
