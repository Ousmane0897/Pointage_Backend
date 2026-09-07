package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.PalierAncienneteCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.ParametresCongesDto;
import com.example.Pointage_Cleanic.entities.rh.PalierAncienneteConge;
import com.example.Pointage_Cleanic.entities.rh.ParametresConges;
import com.example.Pointage_Cleanic.exception.CongeAccesRefuseException;
import com.example.Pointage_Cleanic.repositories.rh.ParametresCongesRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Barème des congés : semis du barème légal, patch partiel, habilitation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParametresCongesServiceTest {

    @Mock private ParametresCongesRepository repository;
    @Mock private CongeIdentiteService identite;
    @Mock private CurrentUserProvider currentUserProvider;

    private ParametresCongesService service;

    @BeforeEach
    void setUp() {
        service = new ParametresCongesService(repository, identite, currentUserProvider);
        ReflectionTestUtils.setField(service, "joursAcquisParMoisSemis", 2);
        when(repository.save(any(ParametresConges.class))).thenAnswer(i -> i.getArgument(0));
        when(identite.estRh()).thenReturn(true);
    }

    private void enBase(ParametresConges entity) {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(entity));
    }

    private void baseVide() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
    }

    @Test
    void une_base_neuve_est_semee_avec_le_bareme_legal() {
        // Get-or-create plutôt qu'un 404 dépendant d'un DataLoader : sans document, le calcul
        // du solde deviendrait impossible.
        baseVide();

        ParametresCongesDto dto = service.getParametres();

        assertThat(dto.getJoursAcquisParMois()).isEqualTo(2);
        assertThat(dto.getJoursParEnfant()).isEqualTo(1);
        assertThat(dto.getAgeMaxEnfant()).isEqualTo(14);
        assertThat(dto.getReserverAuxMeres()).isTrue();
        assertThat(dto.getSupplementAncienneteActif()).isTrue();
        assertThat(dto.getPaliersAnciennete())
                .extracting(PalierAncienneteCongeDto::getAnneesAnciennete,
                        PalierAncienneteCongeDto::getJoursSupplementaires)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10, 1),
                        org.assertj.core.groups.Tuple.tuple(15, 2),
                        org.assertj.core.groups.Tuple.tuple(20, 3),
                        org.assertj.core.groups.Tuple.tuple(25, 6));
    }

    @Test
    void un_patch_partiel_ne_perd_pas_les_paliers() {
        // Sans le patch champ par champ, un client qui n'envoie qu'un réglage effacerait
        // tout le barème d'ancienneté.
        baseVide();

        ParametresCongesDto patch = ParametresCongesDto.builder().joursParEnfant(2).build();
        ParametresCongesDto resultat = service.updateParametres(patch);

        assertThat(resultat.getJoursParEnfant()).isEqualTo(2);
        assertThat(resultat.getPaliersAnciennete()).hasSize(4);
        assertThat(resultat.getJoursAcquisParMois()).isEqualTo(2);
    }

    @Test
    void les_paliers_sont_tries_par_anciennete_croissante() {
        baseVide();

        ParametresCongesDto patch = ParametresCongesDto.builder()
                .paliersAnciennete(List.of(
                        PalierAncienneteCongeDto.builder().anneesAnciennete(25).joursSupplementaires(6).build(),
                        PalierAncienneteCongeDto.builder().anneesAnciennete(10).joursSupplementaires(1).build()))
                .build();

        assertThat(service.updateParametres(patch).getPaliersAnciennete())
                .extracting(PalierAncienneteCongeDto::getAnneesAnciennete)
                .containsExactly(10, 25);
    }

    @Test
    void deux_paliers_de_meme_anciennete_sont_refuses() {
        baseVide();

        ParametresCongesDto patch = ParametresCongesDto.builder()
                .paliersAnciennete(List.of(
                        PalierAncienneteCongeDto.builder().anneesAnciennete(10).joursSupplementaires(1).build(),
                        PalierAncienneteCongeDto.builder().anneesAnciennete(10).joursSupplementaires(3).build()))
                .build();

        assertThatThrownBy(() -> service.updateParametres(patch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 ans");
    }

    @Test
    void un_plafond_negatif_efface_le_plafond() {
        // « null = inchangé » interdirait de retirer un plafond une fois posé, et 0
        // signifierait « aucun jour » — d'où la sentinelle négative.
        enBase(ParametresConges.builder().plafondJoursEnfants(3).build());

        ParametresCongesDto resultat = service.updateParametres(
                ParametresCongesDto.builder().plafondJoursEnfants(-1).build());

        assertThat(resultat.getPlafondJoursEnfants()).isNull();
    }

    @Test
    void un_profil_non_rh_ne_peut_pas_modifier_le_bareme() {
        when(identite.estRh()).thenReturn(false);
        when(identite.estSuperAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.updateParametres(ParametresCongesDto.builder().build()))
                .isInstanceOf(CongeAccesRefuseException.class);
    }

    @Test
    void le_super_admin_peut_modifier_le_bareme() {
        when(identite.estRh()).thenReturn(false);
        when(identite.estSuperAdmin()).thenReturn(true);
        baseVide();

        assertThat(service.updateParametres(ParametresCongesDto.builder().joursParEnfant(2).build())
                .getJoursParEnfant()).isEqualTo(2);
    }

    @Test
    void un_document_seme_par_une_version_anterieure_retombe_sur_le_bareme_legal() {
        // Champs absents ⇒ valeurs légales, jamais un droit à zéro par simple absence de champ.
        enBase(ParametresConges.builder().joursAcquisParMois(2).build());

        BaremeConges bareme = service.baremeCourant();

        assertThat(bareme.joursParEnfant()).isEqualTo(1);
        assertThat(bareme.ageMaxEnfant()).isEqualTo(14);
        assertThat(bareme.reserverAuxMeres()).isTrue();
        assertThat(bareme.paliersAnciennete()).hasSize(4);
    }

    @Test
    void le_bareme_courant_reflete_le_document_en_base() {
        enBase(ParametresConges.builder()
                .joursAcquisParMois(2)
                .supplementEnfantsActif(true).joursParEnfant(2).ageMaxEnfant(18)
                .reserverAuxMeres(false).plafondJoursEnfants(5)
                .supplementAncienneteActif(true)
                .paliersAnciennete(List.of(PalierAncienneteConge.builder()
                        .anneesAnciennete(5).joursSupplementaires(4).build()))
                .proratiserSupplements(false)
                .build());

        BaremeConges bareme = service.baremeCourant();

        assertThat(bareme.joursParEnfant()).isEqualTo(2);
        assertThat(bareme.ageMaxEnfant()).isEqualTo(18);
        assertThat(bareme.reserverAuxMeres()).isFalse();
        assertThat(bareme.plafondJoursEnfants()).isEqualTo(5);
        assertThat(bareme.joursPourAnciennete(6)).isEqualTo(4);
    }
}
