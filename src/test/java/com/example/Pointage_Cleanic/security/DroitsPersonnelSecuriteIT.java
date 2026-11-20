package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C4 — dossiers employés, contrats, ancien module EmployeComplet. */
class DroitsPersonnelSecuriteIT extends SecuriteITBase {

    private static final String DOSSIER = "{\"rh\":{\"dossierEmploye\":true}}";
    private static final String STOCK = "{\"stock\":{\"bonsSortie\":true}}";
    private static final String AUTRE_RH = "{\"rh\":{\"formations\":true}}";

    /** Valeurs factices, reconnaissables dans les réponses. */
    private static final String RIB = "RIB-FACTICE-0001";
    private static final String TEL = "770000001";

    private String idDossier;

    @BeforeEach
    void dossier() {
        mongoTemplate.dropCollection(DossierEmploye.class);
        DossierEmploye d = new DossierEmploye();
        d.setMatricule("MAT-TEST-1");
        d.setAgentId("4321");
        d.setNom("Faye");
        d.setPrenom("Moussa");
        d.setPoste("Agent");
        d.setDepartement("Exploitation");
        d.setTelephone(TEL);
        d.setEmail("moussa.perso@exemple.sn");
        d.setAdresse("Adresse factice");
        d.setNumeroIpres("IPRES-FACTICE");
        d.setNumeroCss("CSS-FACTICE");
        d.setRib(RIB);
        d.setBanque("Banque factice");
        idDossier = mongoTemplate.save(d).getId();
    }

    @Test
    void ecriture_dossier_reservee_au_drapeau() throws Exception {
        verifierAcces(() -> delete("/api/gestion-personnel/employes/{id}", "inexistant"),
                DOSSIER, STOCK, AUTRE_RH);
        verifierAcces(() -> multipart("/api/gestion-personnel/employes").file(
                        new org.springframework.mock.web.MockMultipartFile("dossier", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes())),
                DOSSIER, STOCK, AUTRE_RH);
    }

    @Test
    void lecture_referentiel_sans_droit_403() throws Exception {
        mockMvc.perform(get("/api/gestion-personnel/employes")).andExpect(status().isUnauthorized());
        mockMvc.perform(avec(get("/api/gestion-personnel/employes"), jetonSansDroit())).andExpect(status().isForbidden());
    }

    /** Un lecteur venu d'un autre module reçoit la vue réduite : ni paie, ni données personnelles. */
    @Test
    void lecture_depuis_stock_vue_reduite() throws Exception {
        String jeton = jetonAvec(STOCK);
        mockMvc.perform(avec(get("/api/gestion-personnel/employes"), jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nom").value("Faye"))
                .andExpect(jsonPath("$.content[0].matricule").value("MAT-TEST-1"))
                .andExpect(content().string(not(containsString(RIB))))
                .andExpect(content().string(not(containsString(TEL))))
                .andExpect(content().string(not(containsString("IPRES-FACTICE"))))
                .andExpect(content().string(not(containsString("CSS-FACTICE"))))
                .andExpect(content().string(not(containsString("moussa.perso"))))
                .andExpect(content().string(not(containsString("Adresse factice"))))
                .andExpect(content().string(not(containsString("4321"))))
                .andExpect(jsonPath("$.content[0].rib").doesNotExist())
                .andExpect(jsonPath("$.content[0].banque").doesNotExist());

        mockMvc.perform(avec(get("/api/gestion-personnel/employes/{id}", idDossier), jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prenom").value("Moussa"))
                .andExpect(jsonPath("$.rib").doesNotExist())
                .andExpect(jsonPath("$.numeroIpres").doesNotExist())
                .andExpect(jsonPath("$.numeroCss").doesNotExist())
                .andExpect(jsonPath("$.telephone").doesNotExist())
                .andExpect(jsonPath("$.dateNaissance").doesNotExist())
                .andExpect(jsonPath("$.adresse").doesNotExist());
    }

    @Test
    void lecture_avec_drapeau_dossier_vue_complete() throws Exception {
        mockMvc.perform(avec(get("/api/gestion-personnel/employes/{id}", idDossier), jetonAvec(DOSSIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rib").value(RIB))
                .andExpect(jsonPath("$.telephone").value(TEL));
    }

    @Test
    void contrats() throws Exception {
        verifierAcces(() -> get("/api/gestion-personnel/contrats"),
                "{\"rh\":{\"contrats\":true}}", STOCK, AUTRE_RH);
        verifierAcces(() -> delete("/api/gestion-personnel/contrats/{id}", "inexistant"),
                "{\"rh\":{\"contrats\":true}}", STOCK, DOSSIER);
    }

    @Test
    void employe_complet_legacy() throws Exception {
        verifierAcces(() -> get("/api/employe-complet/all"), DOSSIER, STOCK, AUTRE_RH);
    }
}
