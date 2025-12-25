package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class PointageServicesTest {

    @InjectMocks
    private PointageServices service;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PointageRepository pointageRepository;

    // ======================================================
    // 🧪 canPoint()
    // ======================================================

    @Test
    void should_allow_point_when_no_recent_device_usage() {

        when(pointageRepository.existsByDeviceIdAndTimestampAfter(anyString(), any()))
                .thenReturn(false);

        boolean result = service.canPoint("DEV-1", 4);

        assertTrue(result);
    }

    // ======================================================
    // 🧪 enregistrerPointage() — NO EXISTING POINTAGE
    // ======================================================

    @Test
    void should_create_new_pointage_when_none_exists() {

        Employe employe = new Employe();
        employe.setCodeSecret("1234");
        employe.setPrenom("Mamadou");
        employe.setNom("Diop");
        employe.setSite(new String[]{"Dakar"});

        Pointage savedPointage = new Pointage();

        when(mongoTemplate.findOne(any(), eq(Employe.class))).thenReturn(employe);
        when(mongoTemplate.findOne(any(), eq(Pointage.class))).thenReturn(null);
        when(pointageRepository.save(any())).thenReturn(savedPointage);

        Pointage result = service.enregistrerPointage("1234", "DEV-1");

        assertNotNull(result);
        verify(pointageRepository).save(any());
    }

    // ======================================================
    // 🧪 enregistrerPointage() — EXISTING POINTAGE
    // ======================================================

    @Test
    void should_update_existing_pointage() {

        PointageServices spyService = spy(service);

        Employe employe = new Employe();
        employe.setCodeSecret("1234");
        employe.setPrenom("Mamadou");
        employe.setNom("Diop");
        employe.setSite(new String[]{"Dakar"});

        Pointage existing = new Pointage();
        existing.setHeureArrive("08:00");

        doReturn(employe).when(spyService).getEmployeBycodeSecret("1234");
        doReturn(existing).when(spyService).getBycodeSecretAndDate("1234");
        doReturn("08:00").when(spyService).getHeureArriveByCurrentDateAndcodeSecret("1234");

        doNothing().when(spyService)
                .updatePointage(any(), any(), any(), any(), any());

        Pointage result = spyService.enregistrerPointage("1234", "DEV-1");

        assertNotNull(result);

        verify(spyService)
                .updatePointage(any(), any(), any(), any(), any());
    }







    // ======================================================
    // 🧪 enregistrerPointage() — EMPLOYE NOT FOUND
    // ======================================================

    @Test
    void should_throw_exception_when_employe_not_found() {

        when(mongoTemplate.findOne(any(), eq(Employe.class))).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.enregistrerPointage("9999", "DEV-1"));

        assertTrue(ex.getMessage().contains("Employé introuvable"));
    }
}
