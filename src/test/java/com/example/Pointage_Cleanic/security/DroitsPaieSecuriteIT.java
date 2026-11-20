package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C4 — paie : bulletins, paramètres, grille salariale, déclarations sociales. */
class DroitsPaieSecuriteIT extends SecuriteITBase {

    private static final String AUTRE_RH = "{\"rh\":{\"formations\":true}}";
    private static final String AUTRE_MODULE = "{\"stock\":{\"catalogue\":true},\"dashboard\":true}";

    @Test
    void grille_salariale() throws Exception {
        verifierAcces(() -> get("/api/paie/grille-salariale"),
                "{\"rh\":{\"grilleSalariale\":true}}", AUTRE_RH, AUTRE_MODULE);
        verifierAcces(() -> post("/api/paie/grille-salariale").contentType(MediaType.APPLICATION_JSON).content("{}"),
                "{\"rh\":{\"grilleSalariale\":true}}", AUTRE_RH, "{\"rh\":{\"calculBulletin\":true}}");
    }

    @Test
    void bulletins() throws Exception {
        verifierAcces(() -> get("/api/paie/bulletins"),
                "{\"rh\":{\"calculBulletin\":true}}", AUTRE_RH, AUTRE_MODULE);
        verifierAcces(() -> get("/api/paie/bulletins/historique").param("employeId", "e1").param("annee", "2026"),
                "{\"rh\":{\"historiquePaies\":true}}", AUTRE_RH);
        verifierAcces(() -> post("/api/paie/bulletins/calculer").contentType(MediaType.APPLICATION_JSON).content("{}"),
                "{\"rh\":{\"calculBulletin\":true}}", "{\"rh\":{\"historiquePaies\":true}}", AUTRE_MODULE);
    }

    @Test
    void declarations_sociales() throws Exception {
        verifierAcces(() -> get("/api/paie/declarations-sociales"),
                "{\"rh\":{\"declarations\":true}}", AUTRE_RH, AUTRE_MODULE);
    }

    @Test
    void parametres_paie() throws Exception {
        verifierAcces(() -> get("/api/paie/parametres"),
                "{\"rh\":{\"declarations\":true}}", AUTRE_RH, AUTRE_MODULE);
        verifierAcces(() -> put("/api/paie/parametres").contentType(MediaType.APPLICATION_JSON).content("{}"),
                "{\"rh\":{\"calculBulletin\":true}}", "{\"rh\":{\"declarations\":true}}", AUTRE_MODULE);
    }

    /** Rôle RH : accès à tout le RH sans drapeau explicite (comme LoginService.accesRh du front). */
    @Test
    void role_rh_sans_drapeau_accede_a_la_paie() throws Exception {
        String jeton = jetonUtilisateur("role.rh@cleanic.sn", RoleAdmin.RH, null, true);
        mockMvc.perform(avec(get("/api/paie/bulletins"), jeton)).andExpect(status().isOk());
    }

    @Test
    void superadmin_accede_a_la_paie() throws Exception {
        mockMvc.perform(avec(get("/api/paie/grille-salariale"), jetonSuperAdmin())).andExpect(status().isOk());
    }
}
