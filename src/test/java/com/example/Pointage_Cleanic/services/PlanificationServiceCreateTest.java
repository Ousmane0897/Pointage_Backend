package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Planification;

import com.example.Pointage_Cleanic.repositories.PlanificationRepository;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PlanificationServiceCreateTest {

    @Mock private PlanificationRepository repository;
    @Mock private TaskScheduler taskScheduler;
    @Mock private EmployeServices employeServices;
    @Mock private AgencesServices agencesServices;
    @Mock private EmployeRepository employeRepository;
    @Mock private AgencesRepository agencesRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @Spy
    @InjectMocks
    private PlanificationService service;

    // ---------------------------------------------------------
    // TEST createPlanification()
    // ---------------------------------------------------------
    @Test
    void testCreatePlanification() {

        // ------------------------------
        // 🔹 GIVEN
        // ------------------------------
        Employe employe = new Employe();
        employe.setCodeSecret("ABC123");
        employe.setDeplacement(false);

        // Mock Planification d’entrée
        Planification input = new Planification();
        input.setId("PLAN123");
        input.setCodeSecret("ABC123");
        input.setDateDebut(new Date());
        input.setDateFin(new Date());
        input.setHeureDebut("08:00");
        input.setHeureFin("17:00");

        // Mock résultat repository
        Planification saved = new Planification();
        saved.setId("PLAN123");
        saved.setCodeSecret("ABC123");
        saved.setStatut(Planification.Statut.EN_ATTENTE);

        when(employeServices.getBycodeSecret("ABC123")).thenReturn(employe);
        when(repository.save(any(Planification.class))).thenReturn(saved);

        // ⚠️ IMPORTANT : empêcher la vraie méthode (scheduler) de s’exécuter
        doNothing().when(service).scheduleStartAndEnd(any(Planification.class));


        // ------------------------------
        // 🔹 WHEN
        // ------------------------------
        var result = service.createPlanification(input);


        // ------------------------------
        // 🔹 THEN
        // ------------------------------

        // Employé récupéré
        verify(employeServices).getBycodeSecret("ABC123");

        // L’employé doit avoir été marqué en déplacement
        assertTrue(employe.isDeplacement());

        // Employé sauvegardé
        verify(employeServices).save(employe);

        // Planification sauvegardée
        verify(repository).save(any(Planification.class));

        // Vérifier statut -> EN_ATTENTE
        assertEquals("EN_ATTENTE", result.getStatut());

        // La méthode scheduleStartAndEnd a bien été appelée
        verify(service).scheduleStartAndEnd(saved);

        assertNotNull(result);
        assertEquals("PLAN123", result.getId());
    }
}
