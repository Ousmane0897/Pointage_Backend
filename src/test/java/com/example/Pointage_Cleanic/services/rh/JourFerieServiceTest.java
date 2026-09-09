package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.JourFerieDto;
import com.example.Pointage_Cleanic.entities.rh.JourFerie;
import com.example.Pointage_Cleanic.exception.CongeAccesRefuseException;
import com.example.Pointage_Cleanic.exception.JourFerieConflitException;
import com.example.Pointage_Cleanic.repositories.rh.JourFerieRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Référentiel des jours fériés — habilitations, unicité de la date et duplication d'année.
 *
 * <p>Dates figées : 2026 n'est pas bissextile, 2028 l'est.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JourFerieServiceTest {

    @Mock private JourFerieRepository repository;
    @Mock private CongeIdentiteService identite;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks private JourFerieService service;

    private static final LocalDate PREMIER_MAI_2026 = LocalDate.of(2026, 5, 1);

    @BeforeEach
    void rhParDefaut() {
        when(identite.estRh()).thenReturn(true);
        when(identite.estSuperAdmin()).thenReturn(false);
        when(repository.save(any(JourFerie.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    }

    private static JourFerieDto dto(LocalDate date, String libelle) {
        return JourFerieDto.builder().date(date).libelle(libelle).build();
    }

    private static JourFerie entity(LocalDate date, String libelle, boolean recurrent) {
        return JourFerie.builder()
                .id(date.toString())
                .date(date)
                .libelle(libelle)
                .chome(true)
                .recurrent(recurrent)
                .build();
    }

    // --- Habilitations ------------------------------------------------------

    @Test
    void un_profil_sans_droit_ne_peut_pas_creer_de_ferie() {
        when(identite.estRh()).thenReturn(false);

        assertThatThrownBy(() -> service.creer(dto(PREMIER_MAI_2026, "Fête du Travail")))
                .isInstanceOf(CongeAccesRefuseException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void le_super_admin_peut_creer_un_ferie() {
        when(identite.estRh()).thenReturn(false);
        when(identite.estSuperAdmin()).thenReturn(true);

        assertThat(service.creer(dto(PREMIER_MAI_2026, "Fête du Travail"))).isNotNull();
    }

    @Test
    void la_lecture_n_exige_aucun_role() {
        when(identite.estRh()).thenReturn(false);
        when(repository.findByDateBetween(any(), any()))
                .thenReturn(List.of(entity(PREMIER_MAI_2026, "Fête du Travail", true)));

        // Le calcul des soldes et du récapitulatif en dépend, y compris pour un simple agent.
        assertThat(service.getParAnnee(2026)).hasSize(1);
        assertThat(service.datesFeriees(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .containsExactly(PREMIER_MAI_2026);
    }

    // --- Unicité de la date -------------------------------------------------

    @Test
    void deux_feries_ne_peuvent_pas_partager_la_meme_date() {
        when(repository.existsByDate(PREMIER_MAI_2026)).thenReturn(true);

        assertThatThrownBy(() -> service.creer(dto(PREMIER_MAI_2026, "Doublon")))
                .isInstanceOf(JourFerieConflitException.class);
    }

    @Test
    void reenregistrer_un_ferie_sans_changer_sa_date_ne_se_heurte_pas_a_lui_meme() {
        JourFerie existant = entity(PREMIER_MAI_2026, "Fête du Travail", true);
        when(repository.findById("x")).thenReturn(java.util.Optional.of(existant));
        when(repository.existsByDate(PREMIER_MAI_2026)).thenReturn(true);

        JourFerieDto modifie = service.modifier("x", dto(PREMIER_MAI_2026, "Fête du travail"));

        assertThat(modifie.getLibelle()).isEqualTo("Fête du travail");
    }

    // --- Défauts serveur ----------------------------------------------------

    @Test
    void un_ferie_cree_sans_drapeau_est_chome_et_non_recurrent() {
        service.creer(dto(PREMIER_MAI_2026, "Fête du Travail"));

        ArgumentCaptor<JourFerie> captor = ArgumentCaptor.forClass(JourFerie.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getChome()).isTrue();
        assertThat(captor.getValue().getRecurrent()).isFalse();
    }

    // --- Duplication d'année ------------------------------------------------

    @Test
    void la_duplication_ne_reporte_que_les_feries_a_date_fixe() {
        when(repository.findByDateBetween(any(), any())).thenReturn(List.of(
                entity(PREMIER_MAI_2026, "Fête du Travail", true),
                entity(LocalDate.of(2026, 3, 20), "Korité", false)));

        List<JourFerieDto> crees = service.dupliquerAnnee(2026, 2027);

        // La Korité recule d'environ onze jours chaque année : la reporter au 20/03/2027
        // produirait une fausse date que personne ne recouperait.
        assertThat(crees).extracting(JourFerieDto::getDate)
                .containsExactly(LocalDate.of(2027, 5, 1));
    }

    @Test
    void la_duplication_est_idempotente() {
        when(repository.findByDateBetween(any(), any()))
                .thenReturn(List.of(entity(PREMIER_MAI_2026, "Fête du Travail", true)));
        when(repository.existsByDate(LocalDate.of(2027, 5, 1))).thenReturn(true);

        // Relancer la duplication ne doit ni dupliquer, ni écraser une correction RH.
        assertThat(service.dupliquerAnnee(2026, 2027)).isEmpty();
    }

    @Test
    void le_29_fevrier_est_saute_plutot_que_reporte_au_1er_mars() {
        when(repository.findByDateBetween(any(), any()))
                .thenReturn(List.of(entity(LocalDate.of(2028, 2, 29), "Jour bissextile", true)));

        assertThat(service.dupliquerAnnee(2028, 2029)).isEmpty();
    }

    @Test
    void dupliquer_une_annee_sur_elle_meme_est_refuse() {
        assertThatThrownBy(() -> service.dupliquerAnnee(2026, 2026))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Lecture pour le calcul ---------------------------------------------

    @Test
    void une_periode_inversee_ou_incomplete_ne_declenche_aucune_lecture() {
        assertThat(service.datesFeriees(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(service.datesFeriees(null, PREMIER_MAI_2026)).isEmpty();
        verify(repository, never()).findByDateBetween(any(), any());
    }
}
