package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.entities.GestionModules.ModulesAutorises;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Règles de {@link Droits} (unitaire, sans Spring). */
class DroitsTest {

    private static final String EMAIL = "a@cleanic.sn";

    private final CurrentUserProvider current = mock(CurrentUserProvider.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final LoginRepository logins = mock(LoginRepository.class);
    private final Droits droits = new Droits(current, utilisateurs, logins);

    @BeforeEach
    void connecte() {
        when(current.currentEmail()).thenReturn(EMAIL);
        when(utilisateurs.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(logins.findByEmail(EMAIL)).thenReturn(Optional.empty());
    }

    private void utilisateur(RoleAdmin role, String modulesJson, boolean actif) throws Exception {
        Utilisateur u = new Utilisateur();
        u.setEmail(EMAIL);
        u.setRole(role);
        u.setActive(actif);
        u.setModulesAutorises(modulesJson == null ? null
                : new ObjectMapper().readValue(modulesJson, ModulesAutorises.class));
        when(utilisateurs.findByEmail(EMAIL)).thenReturn(Optional.of(u));
    }

    @Test
    void anonyme_refuse() {
        when(current.currentEmail()).thenReturn(null);
        assertThat(droits.peut("dashboard")).isFalse();
        assertThat(droits.estSuperAdmin()).isFalse();
    }

    @Test
    void superadmin_de_la_collection_login_passe_partout() {
        User login = new User();
        login.setRole("SUPERADMIN");
        when(logins.findByEmail(EMAIL)).thenReturn(Optional.of(login));
        assertThat(droits.estSuperAdmin()).isTrue();
        assertThat(droits.peut("rh.calculBulletin")).isTrue();
        assertThat(droits.peut("stock.marges")).isTrue();
    }

    @Test
    void drapeau_precis() throws Exception {
        utilisateur(RoleAdmin.SUPERVISEUR, "{\"stock\":{\"catalogue\":true}}", true);
        assertThat(droits.peut("stock.catalogue")).isTrue();
        assertThat(droits.peut("stock.marges")).isFalse();
        assertThat(droits.peut("rh.calculBulletin")).isFalse();
        assertThat(droits.estSuperAdmin()).isFalse();
    }

    @Test
    void joker_module() throws Exception {
        utilisateur(RoleAdmin.SUPERVISEUR, "{\"terrain\":{\"planning\":true}}", true);
        assertThat(droits.peut("terrain.*")).isTrue();
        assertThat(droits.peut("stock.*")).isFalse();
        assertThat(droits.peutUnParmi("stock.*", "terrain.*")).isTrue();
    }

    @Test
    void premier_niveau() throws Exception {
        utilisateur(RoleAdmin.SUPERVISEUR, "{\"dashboard\":true}", true);
        assertThat(droits.peut("dashboard")).isTrue();
        assertThat(droits.peut("admin")).isFalse();
    }

    @Test
    void role_rh_ouvre_tout_le_rh_mais_rien_d_autre() throws Exception {
        utilisateur(RoleAdmin.RH, null, true);
        assertThat(droits.peut("rh.calculBulletin")).isTrue();
        assertThat(droits.peut("rh.*")).isTrue();
        assertThat(droits.peut("stock.catalogue")).isFalse();
    }

    @Test
    void ancien_booleen_rh_vaut_tous_les_drapeaux_rh() throws Exception {
        utilisateur(RoleAdmin.SUPERVISEUR, "{\"rh\":true}", true);
        assertThat(droits.peut("rh.declarations")).isTrue();
    }

    @Test
    void compte_desactive_refuse_meme_avec_le_drapeau() throws Exception {
        utilisateur(RoleAdmin.RH, "{\"stock\":{\"catalogue\":true}}", false);
        assertThat(droits.peut("stock.catalogue")).isFalse();
        assertThat(droits.peut("rh.calculBulletin")).isFalse();
    }

    @Test
    void aucun_module_refuse() throws Exception {
        utilisateur(RoleAdmin.SUPERVISEUR, null, true);
        assertThat(droits.peut("dashboard")).isFalse();
        assertThat(droits.peut("stock.*")).isFalse();
    }
}
