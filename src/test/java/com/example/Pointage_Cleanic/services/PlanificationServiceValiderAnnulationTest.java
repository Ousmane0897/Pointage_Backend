package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Mapper.PlanificationMapper;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import org.mockito.MockedStatic;
import com.example.Pointage_Cleanic.Dto.AnnulationDecisionMessage;
import com.example.Pointage_Cleanic.Dto.PlanificationDto;
import com.example.Pointage_Cleanic.entities.Planification;
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
class PlanificationServiceValiderAnnulationTest {

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
    // TEST 1 : Planification introuvable
    // ---------------------------------------------------------
    @Test
    void testValiderAnnulation_NotFound() {

        when(repository.findById("BAD")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validerAnnulation("BAD", true, "superAdmin")
        );

        assertEquals("Planification non trouvée", ex.getMessage());
        verify(repository).findById("BAD");
    }


    // ---------------------------------------------------------
    // TEST 2 : Acceptation de l'annulation
    // ---------------------------------------------------------
    @Test
    void testValiderAnnulation_Accepte() {

        Planification plan = new Planification();
        plan.setId("PLAN123");
        plan.setRequestedBy("admin1");
        plan.setMotifAnnulation("Urgence");

        when(repository.findById("PLAN123")).thenReturn(Optional.of(plan));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlanificationDto result =
                service.validerAnnulation("PLAN123", true, "superAdmin");

        assertEquals(Planification.Statut.ANNULATION_ACCEPTEE, plan.getStatut());
        assertEquals("superAdmin", plan.getValidatedBy());

        verify(repository).save(plan);

        verify(messagingTemplate).convertAndSendToUser(
                eq("admin1"),
                eq("/queue/annulationResponses"),
                any(AnnulationDecisionMessage.class)
        );

        verify(messagingTemplate).convertAndSend(
                eq("/topic/annulationDecisions"),
                any(AnnulationDecisionMessage.class)
        );

        assertNotNull(result);
    }



    // ---------------------------------------------------------
    // TEST 3 : Refus de l'annulation
    // ---------------------------------------------------------
    @Test
    void testValiderAnnulation_Refuse() {

        // ========= MOCK PLANIFICATION =========
        Planification plan = new Planification();
        plan.setId("PLAN123");
        plan.setRequestedBy("admin2");
        plan.setMotifAnnulation("Pas clair");

        when(repository.findById("PLAN123")).thenReturn(Optional.of(plan));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));


        // ========= EXECUTION =========
        PlanificationDto dto =
                service.validerAnnulation("PLAN123", false, "superAdmin");

        // ========= ASSERTIONS =========
        assertEquals(Planification.Statut.ANNULATION_REFUSEE, plan.getStatut());
        assertEquals("superAdmin", plan.getValidatedBy());

        verify(repository).save(plan);

        // PRIVATE notification
        verify(messagingTemplate).convertAndSendToUser(
                eq("admin2"),
                eq("/queue/annulationResponses"),
                any(AnnulationDecisionMessage.class)
        );

        // BROADCAST notification
        verify(messagingTemplate).convertAndSend(
                eq("/topic/annulationDecisions"),
                any(AnnulationDecisionMessage.class)
        );

        assertNotNull(dto);
    }
}
