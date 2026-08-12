package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.RhAbsenceDto;
import com.example.Pointage_Cleanic.Enum.rh.TypeAbsence;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.RhAbsence;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.rh.RhAbsenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RhAbsenceServiceTest {

    private RhAbsenceRepository rhAbsenceRepository;
    private DossierEmployeRepository dossierEmployeRepository;
    private CongeIdentiteService identite;
    private RhAbsenceService service;

    @BeforeEach
    void setup() {
        rhAbsenceRepository = mock(RhAbsenceRepository.class);
        dossierEmployeRepository = mock(DossierEmployeRepository.class);
        identite = mock(CongeIdentiteService.class);
        service = new RhAbsenceService(rhAbsenceRepository, dossierEmployeRepository, identite);
        // Le périmètre de visibilité est couvert par RhAbsenceServiceScopeTest : ici on se
        // place en RH pour ne mesurer que les règles métier de la déclaration.
        when(identite.perimetreLecture()).thenReturn(PerimetreConges.tout());

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
}