package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.PlanificationDto;
import com.example.Pointage_Cleanic.entities.Planification;
import com.example.Pointage_Cleanic.repositories.PlanificationRepository;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.*;

import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PlanificationServiceUpdateTest {

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
    // CASE 1 — ID introuvable → Optional.empty()
    // ---------------------------------------------------------
    @Test
    void testUpdatePlanification_NotFound() {

        when(repository.findById("XYZ")).thenReturn(Optional.empty());

        Optional<PlanificationDto> result = service.updatePlanification("XYZ", new Planification());

        assertTrue(result.isEmpty());
        verify(repository).findById("XYZ");
        verify(service, never()).cancelScheduled(anyString());
        verify(repository, never()).save(any());
    }


    // ---------------------------------------------------------
    // CASE 2 — Mise à jour complète
    // ---------------------------------------------------------
    @Test
    void testUpdatePlanification_Success() {

        // ----- Existing planification -----
        Planification existing = new Planification();
        existing.setId("PLAN123");
        existing.setNomSite("AncienSite");
        existing.setPrenomNom("Ancien Nom");
        existing.setCommentaires("Avant");
        existing.setHeureDebut("08:00");
        existing.setHeureFin("16:00");
        existing.setStatut(Planification.Statut.EN_COURS);

        // ----- Updated planification -----
        Planification updated = new Planification();
        updated.setPrenomNom("Nouveau Nom");
        updated.setNomSite("NouveauSite");
        updated.setDateDebut(new Date());
        updated.setHeureDebut("09:00");
        updated.setDateFin(new Date());
        updated.setHeureFin("18:00");
        updated.setCommentaires("Mis à jour");

        when(repository.findById("PLAN123")).thenReturn(Optional.of(existing));
        when(repository.save(any(Planification.class))).thenAnswer(inv -> inv.getArgument(0));

        // empêcher le scheduler réel
        doNothing().when(service).cancelScheduled("PLAN123");
        doNothing().when(service).scheduleStartAndEnd(any(Planification.class));

        // ----- When -----
        Optional<PlanificationDto> result = service.updatePlanification("PLAN123", updated);

        // ----- Then -----
        assertTrue(result.isPresent());
        PlanificationDto dto = result.get();

        // Vérifier les mises à jour
        assertEquals("Nouveau Nom", dto.getPrenomNom());
        assertEquals("NouveauSite", dto.getNomSite());
        assertEquals("EN_ATTENTE", dto.getStatut());

        // Vérifier appels
        verify(service).cancelScheduled("PLAN123");
        verify(repository).save(existing);
        verify(service).scheduleStartAndEnd(existing);
    }
}
