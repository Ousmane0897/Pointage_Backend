package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.RhAbsenceDto;
import com.example.Pointage_Cleanic.Enum.rh.TypeAbsence;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.RhAbsence;
import com.example.Pointage_Cleanic.Enum.rh.StatutAbsence;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.rh.RhAbsenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RhAbsenceServiceTest {

    private RhAbsenceRepository rhAbsenceRepository;
    private DossierEmployeRepository dossierEmployeRepository;
    private RhAbsenceService service;

    @BeforeEach
    void setup() {
        rhAbsenceRepository = mock(RhAbsenceRepository.class);
        dossierEmployeRepository = mock(DossierEmployeRepository.class);
        service = new RhAbsenceService(rhAbsenceRepository, dossierEmployeRepository);

        when(dossierEmployeRepository.findById("emp1")).thenReturn(Optional.of(
                DossierEmploye.builder().id("emp1").matricule("M001")
                        .nom("Diop").prenom("Moussa").departement("Dakar")
                        .build()));
        when(rhAbsenceRepository.save(any(RhAbsence.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void create_avec_type_AUTRE_sans_precision_leve_exception() {
        RhAbsenceDto dto = RhAbsenceDto.builder()
                .employeId("emp1").type(TypeAbsence.AUTRE)
                .dateDebut(LocalDate.of(2026, 4, 1)).dateFin(LocalDate.of(2026, 4, 2))
                .build();

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("typeAutrePrecision");
    }

    @Test
    void create_avec_type_AUTRE_et_precision_ok() {
        RhAbsenceDto dto = RhAbsenceDto.builder()
                .employeId("emp1").type(TypeAbsence.AUTRE).typeAutrePrecision("Deuil familial")
                .dateDebut(LocalDate.of(2026, 4, 1)).dateFin(LocalDate.of(2026, 4, 2))
                .build();

        RhAbsenceDto result = service.create(dto);

        assertThat(result.getTypeAutrePrecision()).isEqualTo("Deuil familial");
        assertThat(result.getMatricule()).isEqualTo("M001");
        assertThat(result.getDepartement()).isEqualTo("Dakar");
    }

    @Test
    void create_avec_autre_type_nettoie_typeAutrePrecision() {
        RhAbsenceDto dto = RhAbsenceDto.builder()
                .employeId("emp1").type(TypeAbsence.MALADIE).typeAutrePrecision("résidu incohérent")
                .dateDebut(LocalDate.of(2026, 4, 1)).dateFin(LocalDate.of(2026, 4, 2))
                .build();

        service.create(dto);

        ArgumentCaptor<RhAbsence> captor = ArgumentCaptor.forClass(RhAbsence.class);
        verify(rhAbsenceRepository).save(captor.capture());
        assertThat(captor.getValue().getTypeAutrePrecision()).isNull();
    }

    @Test
    void create_avec_type_ANNUEL_ok() {
        RhAbsenceDto dto = RhAbsenceDto.builder()
                .employeId("emp1").type(TypeAbsence.ANNUEL).typeAutrePrecision("ignoré")
                .dateDebut(LocalDate.of(2026, 5, 4)).dateFin(LocalDate.of(2026, 5, 8))
                .build();

        RhAbsenceDto result = service.create(dto);

        assertThat(result.getType()).isEqualTo(TypeAbsence.ANNUEL);
        assertThat(result.getTypeAutrePrecision()).isNull();     // ignoré si type != AUTRE
        assertThat(result.getNombreJours()).isEqualTo(5);        // bornes incluses
        assertThat(result.getStatut()).isEqualTo(StatutAbsence.DECLAREE);
        assertThat(result.getMatricule()).isEqualTo("M001");
    }

    @Test
    void create_avec_type_SANS_SOLDE_ok() {
        RhAbsenceDto dto = RhAbsenceDto.builder()
                .employeId("emp1").type(TypeAbsence.SANS_SOLDE)
                .dateDebut(LocalDate.of(2026, 6, 1)).dateFin(LocalDate.of(2026, 6, 1))
                .build();

        RhAbsenceDto result = service.create(dto);

        assertThat(result.getType()).isEqualTo(TypeAbsence.SANS_SOLDE);
        assertThat(result.getTypeAutrePrecision()).isNull();
        assertThat(result.getNombreJours()).isEqualTo(1);
        assertThat(result.getStatut()).isEqualTo(StatutAbsence.DECLAREE);
    }

    @Test
    void search_filtre_par_type_ANNUEL() {
        RhAbsence annuel = RhAbsence.builder()
                .id("a1").employeId("emp1").type(TypeAbsence.ANNUEL)
                .dateDebut(LocalDate.of(2026, 5, 4)).dateFin(LocalDate.of(2026, 5, 8))
                .statut(StatutAbsence.DECLAREE).build();
        RhAbsence sansSolde = RhAbsence.builder()
                .id("a2").employeId("emp1").type(TypeAbsence.SANS_SOLDE)
                .dateDebut(LocalDate.of(2026, 6, 1)).dateFin(LocalDate.of(2026, 6, 1))
                .statut(StatutAbsence.DECLAREE).build();
        when(rhAbsenceRepository.findAll()).thenReturn(List.of(annuel, sansSolde));

        Page<RhAbsenceDto> result = service.search(
                null, "ANNUEL", null, null, null, null, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo("a1");
        assertThat(result.getContent().get(0).getType()).isEqualTo(TypeAbsence.ANNUEL);
    }
}