package com.example.Pointage_Cleanic.services.pointage;

import com.example.Pointage_Cleanic.Dto.pointage.ParametresPointageDto;
import com.example.Pointage_Cleanic.Enum.pointage.ModeHorsAffectation;
import com.example.Pointage_Cleanic.entities.pointage.ParametresPointage;
import com.example.Pointage_Cleanic.exception.PointageAccesRefuseException;
import com.example.Pointage_Cleanic.repositories.pointage.ParametresPointageRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ParametresPointageServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Africa/Dakar"));

    private final ParametresPointageRepository repository = mock(ParametresPointageRepository.class);
    private final CurrentUserProvider user = mock(CurrentUserProvider.class);
    private final ParametresPointageService service = new ParametresPointageService(repository, user, CLOCK);

    @Test
    void base_neuve_seme_avertir_et_qr_facultatif() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ParametresPointageDto dto = service.getParametres();

        assertEquals(ModeHorsAffectation.AVERTIR, dto.getHorsAffectation());
        assertFalse(dto.getQrObligatoire());
        verify(repository).save(any());
    }

    @Test
    void champs_absents_du_document_retombent_sur_les_defauts() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(new ParametresPointage()));

        ParametresPointageDto dto = service.getParametres();

        assertEquals(ModeHorsAffectation.AVERTIR, dto.getHorsAffectation());
        assertFalse(dto.getQrObligatoire());
    }

    @Test
    void patch_rh_ne_modifie_que_les_champs_fournis() {
        ParametresPointage doc = ParametresPointage.builder()
                .horsAffectation(ModeHorsAffectation.AVERTIR).qrObligatoire(false).build();
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(doc));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(user.currentRole()).thenReturn("RH");
        when(user.currentUserNom()).thenReturn("Awa RH");

        ParametresPointageDto dto = service.updateParametres(
                ParametresPointageDto.builder().qrObligatoire(true).build());

        assertTrue(dto.getQrObligatoire());
        assertEquals(ModeHorsAffectation.AVERTIR, dto.getHorsAffectation(), "null = inchangé");
        assertEquals("Awa RH", dto.getModifieParNom());
    }

    @Test
    void patch_hors_rh_et_superadmin_est_refuse() {
        when(user.currentRole()).thenReturn("EXPLOITATION");

        assertThrows(PointageAccesRefuseException.class, () -> service.updateParametres(
                ParametresPointageDto.builder().horsAffectation(ModeHorsAffectation.BLOQUER).build()));
        verify(repository, never()).save(any());
    }

    @Test
    void superadmin_est_administrateur() {
        when(user.currentRole()).thenReturn("SUPERADMIN");
        assertTrue(service.estAdministrateur());
        when(user.currentRole()).thenReturn(null);
        assertFalse(service.estAdministrateur());
    }
}
