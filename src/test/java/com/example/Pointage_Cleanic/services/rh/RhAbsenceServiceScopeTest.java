package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.RhAbsenceDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutAbsence;
import com.example.Pointage_Cleanic.Enum.rh.TypeAbsence;
import com.example.Pointage_Cleanic.entities.rh.RhAbsence;
import com.example.Pointage_Cleanic.exception.CongeAccesRefuseException;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.rh.RhAbsenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Périmètre de visibilité des <b>déclarations</b> (onglet « Déclarations » de la rubrique
 * Congés).
 *
 * <p>Ce module est servi par une autre collection que les demandes de congé : sans la même
 * garde, la restriction posée sur les congés serait contournable en un clic depuis l'onglet
 * voisin. Une déclaration ne porte pas de supérieur figé — seul {@code voitEmploye}
 * s'applique.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RhAbsenceServiceScopeTest {

    private static final String MOI = "emp-moi";
    private static final String SUBORDONNE = "emp-sub";
    private static final String TIERS = "emp-tiers";

    @Mock private RhAbsenceRepository rhAbsenceRepository;
    @Mock private DossierEmployeRepository dossierEmployeRepository;
    @Mock private CongeIdentiteService identite;

    private RhAbsenceService service;

    @BeforeEach
    void setUp() {
        service = new RhAbsenceService(rhAbsenceRepository, dossierEmployeRepository, identite);
        when(identite.perimetreLecture())
                .thenReturn(new PerimetreConges(false, MOI, Set.of(MOI, SUBORDONNE)));
    }

    private RhAbsence absence(String employeId) {
        return RhAbsence.builder()
                .id("abs-" + employeId)
                .employeId(employeId)
                .type(TypeAbsence.MALADIE)
                .statut(StatutAbsence.DECLAREE)
                .dateDebut(LocalDate.of(2026, 4, 1))
                .dateFin(LocalDate.of(2026, 4, 3))
                .build();
    }

    private Page<RhAbsenceDto> rechercher() {
        return service.search(null, null, null, null, null, null, null, 0, 10);
    }

    @Test
    void la_recherche_est_limitee_au_perimetre() {
        when(rhAbsenceRepository.findAll())
                .thenReturn(List.of(absence(MOI), absence(SUBORDONNE), absence(TIERS)));

        Page<RhAbsenceDto> page = rechercher();

        assertThat(page.getContent()).extracting(RhAbsenceDto::getEmployeId)
                .containsExactlyInAnyOrder(MOI, SUBORDONNE);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void la_rh_voit_toutes_les_declarations() {
        when(identite.perimetreLecture()).thenReturn(PerimetreConges.tout());
        when(rhAbsenceRepository.findAll()).thenReturn(List.of(absence(MOI), absence(TIERS)));

        assertThat(rechercher().getTotalElements()).isEqualTo(2);
    }

    @Test
    void un_compte_non_rattache_ne_voit_aucune_declaration() {
        when(identite.perimetreLecture()).thenReturn(PerimetreConges.vide());

        assertThat(rechercher().getContent()).isEmpty();
        verify(rhAbsenceRepository, never()).findAll();
    }

    @Test
    void ouvrir_la_declaration_d_un_tiers_est_refuse() {
        when(rhAbsenceRepository.findById("abs-tiers")).thenReturn(Optional.of(absence(TIERS)));

        assertThatThrownBy(() -> service.getById("abs-tiers"))
                .isInstanceOf(CongeAccesRefuseException.class);
    }

    @Test
    void ouvrir_la_declaration_d_un_subordonne_est_autorise() {
        when(rhAbsenceRepository.findById("abs-emp-sub")).thenReturn(Optional.of(absence(SUBORDONNE)));

        assertThat(service.getById("abs-emp-sub").getEmployeId()).isEqualTo(SUBORDONNE);
    }

    @Test
    void supprimer_la_declaration_d_un_tiers_est_refuse() {
        when(rhAbsenceRepository.findById("abs-tiers")).thenReturn(Optional.of(absence(TIERS)));

        assertThatThrownBy(() -> service.delete("abs-tiers"))
                .isInstanceOf(CongeAccesRefuseException.class);
        verify(rhAbsenceRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void declarer_au_nom_d_un_tiers_est_refuse() {
        RhAbsenceDto dto = RhAbsenceDto.builder().employeId(TIERS).type(TypeAbsence.MALADIE).build();

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CongeAccesRefuseException.class);
        // Le refus tombe avant toute lecture du dossier employé.
        verify(dossierEmployeRepository, never()).findById(TIERS);
    }

    @Test
    void lister_les_declarations_d_un_tiers_est_refuse() {
        assertThatThrownBy(() -> service.getByEmployeId(TIERS))
                .isInstanceOf(CongeAccesRefuseException.class);
    }
}
