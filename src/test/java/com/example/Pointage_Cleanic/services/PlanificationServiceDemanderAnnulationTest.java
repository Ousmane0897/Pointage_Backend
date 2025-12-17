package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.CancelRequestDto;
import com.example.Pointage_Cleanic.entities.Planification;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.repositories.PlanificationRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PlanificationServiceDemanderAnnulationTest {

    @Mock private PlanificationRepository repository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @Mock private TaskScheduler taskScheduler;
    @Mock private EmployeServices employeServices;
    @Mock private AgencesServices agencesServices;
    @Mock private EmployeRepository employeRepository;
    @Mock private AgencesRepository agencesRepository;

    @InjectMocks
    private PlanificationService service;


    // ---------------------------------------------------------
    // CASE 1 : Planification introuvable → exception
    // ---------------------------------------------------------
    @Test
    void testDemanderAnnulation_NotFound() {
        when(repository.findById("BAD_ID")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.demanderAnnulation("BAD_ID", "Motif", "Admin")
        );

        assertEquals("Planification non trouvée", ex.getMessage());
        verify(repository).findById("BAD_ID");
        verify(repository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(Optional.ofNullable(any()), any());
    }


    // ---------------------------------------------------------
    // CASE 2 : Succès → mise à jour + websocket + DTO
    // ---------------------------------------------------------
    @Test
    void testDemanderAnnulation_Success() {

        // ---- Fake planification ----
        Planification plan = new Planification();
        plan.setId("PLAN123");

        when(repository.findById("PLAN123")).thenReturn(Optional.of(plan));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0)); // renvoie l'objet sauvegardé

        // ---- Inputs ----
        String motif = "Maladie";
        String requestedBy = "adminTest";

        // ---- Execution ----
        CancelRequestDto dto = service.demanderAnnulation("PLAN123", motif, requestedBy);

        // ---- Vérifications ----
        // BDD mise à jour
        assertEquals(motif, plan.getMotifAnnulation());
        assertEquals("EN_ATTENTE_VALIDATION", plan.getStatut().name());
        assertEquals(requestedBy, plan.getRequestedBy());
        assertNotNull(plan.getDateDemandeAnnulation());

        verify(repository).save(plan);

        // WebSocket envoyé
        verify(messagingTemplate).convertAndSend(eq("/topic/annulationRequests"), any(CancelRequestDto.class));

        // Vérification du DTO retourné
        assertEquals("PLAN123", dto.getPlanificationId());
        assertEquals("Maladie", dto.getMotif());
        assertEquals("adminTest", dto.getRequestedBy());
    }
}
