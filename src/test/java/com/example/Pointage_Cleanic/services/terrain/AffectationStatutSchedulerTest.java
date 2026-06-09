package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import com.example.Pointage_Cleanic.repositories.terrain.AffectationAgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre la règle de transition de statut aux bornes exactes (cœur du livrable)
 * puis le job complet avec une horloge figée.
 */
@ExtendWith(MockitoExtension.class)
class AffectationStatutSchedulerTest {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    /** Dakar = UTC+0, donc l'instant UTC correspond directement au LocalDateTime attendu. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 8, 10, 0, 0);

    @Mock
    private AffectationAgentRepository repository;

    @Captor
    private ArgumentCaptor<List<AffectationAgent>> savedCaptor;

    private AffectationStatutScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(DAKAR).toInstant(), DAKAR);
        scheduler = new AffectationStatutScheduler(repository, fixedClock);
    }

    // --- A. Règle pure : bornes exactes et exclusions -------------------------

    @Test
    void regle_now_egal_dateDebut_passe_en_cours() {
        StatutAffectation cible = AffectationStatutScheduler.appliquerRegle(
                StatutAffectation.PLANIFIEE, NOW, NOW.plusHours(4), NOW);
        assertThat(cible).isEqualTo(StatutAffectation.EN_COURS);
    }

    @Test
    void regle_now_egal_dateFin_passe_effectuee() {
        StatutAffectation cible = AffectationStatutScheduler.appliquerRegle(
                StatutAffectation.PLANIFIEE, NOW.minusHours(4), NOW, NOW);
        assertThat(cible).isEqualTo(StatutAffectation.EFFECTUEE);
    }

    @Test
    void regle_avant_dateDebut_reste_planifiee() {
        StatutAffectation cible = AffectationStatutScheduler.appliquerRegle(
                StatutAffectation.PLANIFIEE, NOW.plusMinutes(1), NOW.plusHours(5), NOW);
        assertThat(cible).isEqualTo(StatutAffectation.PLANIFIEE);
    }

    @Test
    void regle_dans_le_creneau_passe_en_cours() {
        StatutAffectation cible = AffectationStatutScheduler.appliquerRegle(
                StatutAffectation.PLANIFIEE, NOW.minusHours(1), NOW.plusHours(1), NOW);
        assertThat(cible).isEqualTo(StatutAffectation.EN_COURS);
    }

    @Test
    void regle_rattrapage_deux_dates_passees_passe_directement_effectuee() {
        StatutAffectation cible = AffectationStatutScheduler.appliquerRegle(
                StatutAffectation.PLANIFIEE, NOW.minusHours(5), NOW.minusHours(1), NOW);
        assertThat(cible).isEqualTo(StatutAffectation.EFFECTUEE);
    }

    @Test
    void regle_en_cours_apres_dateFin_passe_effectuee() {
        StatutAffectation cible = AffectationStatutScheduler.appliquerRegle(
                StatutAffectation.EN_COURS, NOW.minusHours(5), NOW.minusHours(1), NOW);
        assertThat(cible).isEqualTo(StatutAffectation.EFFECTUEE);
    }

    @Test
    void regle_annulee_remplacee_effectuee_jamais_modifiees() {
        for (StatutAffectation fige : List.of(
                StatutAffectation.ANNULEE, StatutAffectation.REMPLACEE, StatutAffectation.EFFECTUEE)) {
            StatutAffectation cible = AffectationStatutScheduler.appliquerRegle(
                    fige, NOW.minusHours(5), NOW.minusHours(1), NOW);
            assertThat(cible).as("statut figé %s", fige).isEqualTo(fige);
        }
    }

    // --- B. Job complet : sélection, idempotence, updatedAt -------------------

    @Test
    void rafraichirStatuts_ne_sauve_que_les_affectations_modifiees() {
        AffectationAgent versEnCours = affectation("a-encours",
                StatutAffectation.PLANIFIEE, NOW.minusMinutes(1), NOW.plusHours(4));
        AffectationAgent versEffectuee = affectation("a-effectuee",
                StatutAffectation.EN_COURS, NOW.minusHours(5), NOW.minusMinutes(1));
        AffectationAgent inchangee = affectation("a-future",
                StatutAffectation.PLANIFIEE, NOW.plusHours(1), NOW.plusHours(3));

        when(repository.findByStatutIn(List.of(StatutAffectation.PLANIFIEE, StatutAffectation.EN_COURS)))
                .thenReturn(List.of(versEnCours, versEffectuee, inchangee));

        scheduler.rafraichirStatuts();

        verify(repository).saveAll(savedCaptor.capture());
        List<AffectationAgent> saved = savedCaptor.getValue();

        assertThat(saved).extracting(AffectationAgent::getId)
                .containsExactlyInAnyOrder("a-encours", "a-effectuee");
        assertThat(saved).allSatisfy(a -> assertThat(a.getUpdatedAt()).isEqualTo(NOW));
        assertThat(versEnCours.getStatut()).isEqualTo(StatutAffectation.EN_COURS);
        assertThat(versEffectuee.getStatut()).isEqualTo(StatutAffectation.EFFECTUEE);
        assertThat(inchangee.getStatut()).isEqualTo(StatutAffectation.PLANIFIEE);
    }

    @Test
    void rafraichirStatuts_aucune_bascule_ne_sauve_rien() {
        AffectationAgent future = affectation("a-future",
                StatutAffectation.PLANIFIEE, NOW.plusHours(1), NOW.plusHours(3));

        when(repository.findByStatutIn(List.of(StatutAffectation.PLANIFIEE, StatutAffectation.EN_COURS)))
                .thenReturn(List.of(future));

        scheduler.rafraichirStatuts();

        verify(repository, never()).saveAll(any());
    }

    private static AffectationAgent affectation(String id, StatutAffectation statut,
                                                LocalDateTime debut, LocalDateTime fin) {
        return AffectationAgent.builder()
                .id(id)
                .statut(statut)
                .dateDebut(debut)
                .dateFin(fin)
                .build();
    }
}
