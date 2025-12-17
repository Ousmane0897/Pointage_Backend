package com.example.Pointage_Cleanic.services;

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

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PlanificationServiceCancelTest {

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
    // CASE 1 : planification introuvable → false
    // ---------------------------------------------------------
    @Test
    void testCancelPlanification_NotFound() {

        when(repository.findById("ABC")).thenReturn(Optional.empty());

        boolean result = service.cancelPlanification("ABC", "TEST MOTIF");

        assertFalse(result);
        verify(repository).findById("ABC");
        verify(repository, never()).save(any());
        verify(service, never()).cancelScheduled(anyString());
    }


    // ---------------------------------------------------------
    // CASE 2 : planification trouvée → statut ANNULEE + save + cancelScheduled
    // ---------------------------------------------------------
    @Test
    void testCancelPlanification_Success() {

        Planification plan = new Planification();
        plan.setId("PLAN123");
        plan.setStatut(Planification.Statut.EN_ATTENTE);

        when(repository.findById("PLAN123")).thenReturn(Optional.of(plan));
        when(repository.save(any(Planification.class))).thenReturn(plan);

        // Prevent real scheduler logic
        doNothing().when(service).cancelScheduled("PLAN123");

        boolean result = service.cancelPlanification("PLAN123", "Absence imprévue");


        // --------------------------
        // 🔹 Assertions
        // --------------------------

        assertTrue(result);
        assertEquals(Planification.Statut.ANNULEE, plan.getStatut());
        assertEquals("Absence imprévue", plan.getMotifAnnulation());


        // --------------------------
        // 🔹 Verify calls
        // --------------------------

        verify(repository).findById("PLAN123");
        verify(repository).save(plan);
        verify(service).cancelScheduled("PLAN123");
    }
}
