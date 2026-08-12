package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Périmètre de lecture des congés : qui voit quoi.
 *
 * <p>Règle : la RH et le super-admin voient tout ; un employé se voit lui-même et ses
 * subordonnés directs ; un compte non rattaché à un dossier employé ne voit rien.
 */
@ExtendWith(MockitoExtension.class)
class CongeIdentiteServicePerimetreTest {

    private static final String EMAIL = "agent@cleanic.sn";
    private static final String MOI = "emp-moi";

    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private DossierEmployeRepository dossierEmployeRepository;

    @InjectMocks private CongeIdentiteService service;

    private void connecte(String role) {
        when(currentUserProvider.currentRole()).thenReturn(role);
    }

    private void rattacheA(String employeId) {
        when(currentUserProvider.currentEmail()).thenReturn(EMAIL);
        when(dossierEmployeRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(List.of(DossierEmploye.builder().id(employeId).build()));
    }

    @Test
    void la_rh_voit_tout() {
        connecte("RH");

        PerimetreConges perimetre = service.perimetreLecture();

        assertThat(perimetre.voitTout()).isTrue();
        assertThat(perimetre.voitEmploye("n-importe-qui")).isTrue();
        assertThat(perimetre.estVide()).isFalse();
    }

    @Test
    void le_super_admin_voit_tout() {
        connecte("SUPERADMIN");

        assertThat(service.perimetreLecture().voitTout()).isTrue();
    }

    @Test
    void un_agent_sans_subordonne_ne_voit_que_lui_meme() {
        connecte("AGENT");
        rattacheA(MOI);
        when(dossierEmployeRepository.findBySuperieurHierarchiqueId(MOI)).thenReturn(List.of());

        PerimetreConges perimetre = service.perimetreLecture();

        assertThat(perimetre.voitTout()).isFalse();
        assertThat(perimetre.employesVisibles()).containsExactly(MOI);
        assertThat(perimetre.voitEmploye("emp-collegue")).isFalse();
    }

    @Test
    void un_superieur_voit_ses_subordonnes_directs() {
        connecte("AGENT");
        rattacheA(MOI);
        when(dossierEmployeRepository.findBySuperieurHierarchiqueId(MOI)).thenReturn(List.of(
                DossierEmploye.builder().id("emp-sub-1").build(),
                DossierEmploye.builder().id("emp-sub-2").build()));

        PerimetreConges perimetre = service.perimetreLecture();

        assertThat(perimetre.employesVisibles())
                .containsExactlyInAnyOrder(MOI, "emp-sub-1", "emp-sub-2");
        assertThat(perimetre.voitEmploye("emp-sub-2")).isTrue();
        assertThat(perimetre.voitEmploye("emp-autre-equipe")).isFalse();
    }

    @Test
    void un_compte_non_rattache_a_un_dossier_ne_voit_rien() {
        connecte("AGENT");
        when(currentUserProvider.currentEmail()).thenReturn(EMAIL);
        when(dossierEmployeRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(List.of());

        PerimetreConges perimetre = service.perimetreLecture();

        assertThat(perimetre.estVide()).isTrue();
        assertThat(perimetre.voitEmploye(MOI)).isFalse();
    }

    @Test
    void un_role_absent_ne_donne_jamais_un_perimetre_total() {
        connecte(null);
        when(currentUserProvider.currentEmail()).thenReturn(null);

        assertThat(service.perimetreLecture().estVide()).isTrue();
    }

    @Test
    void le_role_n_est_lu_qu_une_fois_par_resolution() {
        // currentRole() interroge Mongo : le périmètre doit être résolu en une passe et
        // circuler ensuite en variable locale, jamais recalculé dans un stream de filtrage.
        connecte("RH");

        service.perimetreLecture();

        verify(currentUserProvider, times(1)).currentRole();
    }

    @Test
    void le_validateur_fige_reste_habilite_meme_hors_perimetre() {
        // Après une réorg, le supérieur figé sur la demande n'est plus le manager courant :
        // il doit malgré tout pouvoir ouvrir la demande qu'il a reçue par e-mail.
        PerimetreConges perimetre = new PerimetreConges(false, MOI, java.util.Set.of(MOI));

        assertThat(perimetre.voitEmploye("emp-ancien-subordonne")).isFalse();
        assertThat(perimetre.voitDemande("emp-ancien-subordonne", MOI)).isTrue();
        assertThat(perimetre.voitDemande("emp-inconnu", "emp-autre-manager")).isFalse();
    }
}
