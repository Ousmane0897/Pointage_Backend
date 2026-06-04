package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.AlerteRecidiveDto;
import com.example.Pointage_Cleanic.Enum.rh.TypeSanction;
import com.example.Pointage_Cleanic.Mapper.rh.SanctionMapper;
import com.example.Pointage_Cleanic.entities.rh.Sanction;
import com.example.Pointage_Cleanic.repositories.rh.SanctionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SanctionServiceTest {

    private SanctionRepository repository;
    private SanctionMapper mapper;
    private SanctionService service;

    @BeforeEach
    void setUp() {
        repository = mock(SanctionRepository.class);
        mapper = mock(SanctionMapper.class);
        service = new SanctionService(repository, mapper);
    }

    @Test
    void estRecidiviste_vrai_si_2_sanctions_meme_type_12_mois() {
        when(repository.countByEmployeIdAndTypeAndDateSanctionBetween(
                eq("emp-1"), eq(TypeSanction.AVERTISSEMENT), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(2L);

        assertThat(service.estRecidiviste("emp-1", TypeSanction.AVERTISSEMENT, LocalDate.of(2026, 4, 21)))
                .isTrue();
    }

    @Test
    void estRecidiviste_faux_si_une_seule_sanction() {
        when(repository.countByEmployeIdAndTypeAndDateSanctionBetween(
                eq("emp-2"), any(), any(), any()))
                .thenReturn(1L);

        assertThat(service.estRecidiviste("emp-2", TypeSanction.BLAME, LocalDate.of(2026, 4, 21)))
                .isFalse();
    }

    @Test
    void alertesRecidive_groupe_par_employe_et_seuil_2() {
        Sanction s1 = Sanction.builder()
                .employeId("emp-1").nom("Diallo").prenom("A")
                .type(TypeSanction.AVERTISSEMENT)
                .dateSanction(LocalDate.of(2026, 1, 15))
                .build();
        Sanction s2 = Sanction.builder()
                .employeId("emp-1").nom("Diallo").prenom("A")
                .type(TypeSanction.BLAME)
                .dateSanction(LocalDate.of(2026, 3, 10))
                .build();
        Sanction s3 = Sanction.builder()
                .employeId("emp-2").nom("Niang").prenom("B")
                .type(TypeSanction.AVERTISSEMENT)
                .dateSanction(LocalDate.of(2026, 2, 5))
                .build();

        when(repository.findByDateSanctionBetween(any(), any()))
                .thenReturn(List.of(s1, s2, s3));

        List<AlerteRecidiveDto> alertes = service.alertesRecidive();

        assertThat(alertes).hasSize(1);
        assertThat(alertes.get(0).getEmployeId()).isEqualTo("emp-1");
        assertThat(alertes.get(0).getNombreSanctions()).isEqualTo(2);
        assertThat(alertes.get(0).getDerniereDate()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(alertes.get(0).getDerniereType()).isEqualTo(TypeSanction.BLAME);
    }
}
