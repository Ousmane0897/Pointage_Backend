package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.RattachementDossierDto;
import com.example.Pointage_Cleanic.entities.GestionModules.ModulesAutorises;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.exception.CompteAccesRefuseException;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RattachementCompteServiceTest {

    @Mock DossierEmployeRepository dossierEmployeRepository;
    @Mock UtilisateurRepository utilisateurRepository;
    @Mock UtilisateursService utilisateursService;
    @Mock CurrentUserProvider currentUserProvider;

    @InjectMocks RattachementCompteService service;

    private void connecteEnSuperAdmin() {
        when(currentUserProvider.currentRole()).thenReturn("SUPERADMIN");
    }

    @Test
    void un_email_porte_par_un_dossier_est_rattache() {
        connecteEnSuperAdmin();
        when(dossierEmployeRepository.findByEmailIgnoreCase("awa@cleanic.sn"))
                .thenReturn(List.of(dossier("d1", "M001", "Awa", "Diop", "awa@cleanic.sn")));

        RattachementDossierDto r = service.rattachement("  Awa@Cleanic.SN ");

        assertThat(r.getEmail()).isEqualTo("awa@cleanic.sn");
        assertThat(r.getEmployeId()).isEqualTo("d1");
        assertThat(r.getMatricule()).isEqualTo("M001");
        assertThat(r.getNomComplet()).isEqualTo("Awa Diop");
    }

    @Test
    void un_email_absent_des_dossiers_n_est_pas_rattache() {
        connecteEnSuperAdmin();
        when(dossierEmployeRepository.findByEmailIgnoreCase("inconnu@cleanic.sn")).thenReturn(List.of());

        RattachementDossierDto r = service.rattachement("inconnu@cleanic.sn");

        assertThat(r.getEmail()).isEqualTo("inconnu@cleanic.sn");
        assertThat(r.getEmployeId()).isNull();
    }

    @Test
    void en_cas_de_doublon_le_dossier_retenu_est_celui_du_depot_de_conge() {
        connecteEnSuperAdmin();
        when(dossierEmployeRepository.findByEmailIgnoreCase("awa@cleanic.sn")).thenReturn(List.of(
                dossier("d9", "M009", "Awa", "Bis", "awa@cleanic.sn"),
                dossier("d2", "M002", "Awa", "Diop", "awa@cleanic.sn")));

        assertThat(service.rattachement("awa@cleanic.sn").getEmployeId()).isEqualTo("d2");
    }

    @Test
    void un_email_vide_ne_declenche_aucune_recherche() {
        connecteEnSuperAdmin();

        assertThat(service.rattachement("   ").getEmployeId()).isNull();
        verifyNoInteractions(dossierEmployeRepository);
    }

    @Test
    void rattachements_lit_les_dossiers_en_une_seule_requete() {
        connecteEnSuperAdmin();
        when(utilisateursService.getAll()).thenReturn(List.of(
                utilisateur("awa@cleanic.sn"), utilisateur("tech@cleanic.sn")));
        when(dossierEmployeRepository.findByEmailIn(anyCollection()))
                .thenReturn(List.of(dossier("d1", "M001", "Awa", "Diop", "awa@cleanic.sn")));

        List<RattachementDossierDto> r = service.rattachements();

        assertThat(r).extracting(RattachementDossierDto::getEmail, RattachementDossierDto::getEmployeId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("awa@cleanic.sn", "d1"),
                        org.assertj.core.groups.Tuple.tuple("tech@cleanic.sn", null));
        verify(dossierEmployeRepository).findByEmailIn(List.of("awa@cleanic.sn", "tech@cleanic.sn"));
    }

    @Test
    void un_compte_portant_le_module_admin_est_habilite() {
        when(currentUserProvider.currentRole()).thenReturn("RH");
        when(currentUserProvider.currentEmail()).thenReturn("rh@cleanic.sn");
        Utilisateur moi = utilisateur("rh@cleanic.sn");
        ModulesAutorises modules = new ModulesAutorises();
        modules.setAdmin(true);
        moi.setModulesAutorises(modules);
        when(utilisateurRepository.findByEmail("rh@cleanic.sn")).thenReturn(Optional.of(moi));
        when(dossierEmployeRepository.findByEmailIgnoreCase("x@cleanic.sn")).thenReturn(List.of());

        assertThat(service.rattachement("x@cleanic.sn").getEmployeId()).isNull();
    }

    @Test
    void un_compte_sans_droit_de_gestion_des_utilisateurs_recoit_403() {
        when(currentUserProvider.currentRole()).thenReturn("MAGASINIER");
        when(currentUserProvider.currentEmail()).thenReturn("mag@cleanic.sn");
        when(utilisateurRepository.findByEmail("mag@cleanic.sn"))
                .thenReturn(Optional.of(utilisateur("mag@cleanic.sn")));

        assertThatThrownBy(() -> service.rattachements())
                .isInstanceOf(CompteAccesRefuseException.class);
        verifyNoInteractions(dossierEmployeRepository);
    }

    private static DossierEmploye dossier(String id, String matricule, String prenom, String nom, String email) {
        return DossierEmploye.builder()
                .id(id).matricule(matricule).prenom(prenom).nom(nom).email(email)
                .build();
    }

    private static Utilisateur utilisateur(String email) {
        Utilisateur u = new Utilisateur();
        u.setEmail(email);
        return u;
    }
}
