package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Agence;
import com.example.Pointage_Cleanic.entities.Employe;
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
class PlanificationServiceStartTaskTest {

    @Mock private PlanificationRepository repository;
    @Mock private EmployeServices employeServices;
    @Mock private AgencesServices agencesServices;
    @Mock private EmployeRepository employeRepository;
    @Mock private AgencesRepository agencesRepository;
    @Mock private TaskScheduler scheduler;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PlanificationService service;


    // ---------------------------------------------------------
    // 1️⃣ La planification n'existe pas → aucune action
    // ---------------------------------------------------------
    @Test
    void testStartTask_WhenPlanificationNotFound() throws Exception {

        Planification p = new Planification();
        p.setId("P1000");

        when(repository.findById("P1000")).thenReturn(Optional.empty());

        // EXECUTION
        var privateMethod =
                service.getClass().getDeclaredMethod("startTask", Planification.class);
        privateMethod.setAccessible(true);
        privateMethod.invoke(service, p);

        // VÉRIFICATIONS
        verify(repository).findById("P1000");
        verifyNoMoreInteractions(repository, employeServices, agencesServices);
    }



    // ---------------------------------------------------------
    // 2️⃣ Statut déjà EN_COURS / ANNULEE / EXECUTEE → rien ne se passe
    // ---------------------------------------------------------
    @Test
    void testStartTask_WhenAlreadyProcessed() throws Exception {

        Planification p = new Planification();
        p.setId("P2000");
        p.setStatut(Planification.Statut.ANNULEE);

        when(repository.findById("P2000")).thenReturn(Optional.of(p));

        var method = service.getClass().getDeclaredMethod("startTask", Planification.class);
        method.setAccessible(true);
        method.invoke(service, p);

        // aucune mise à jour du statut
        verify(repository, never()).save(any());
        verifyNoInteractions(employeServices);
    }


    // ---------------------------------------------------------
    // 3️⃣ Cas NORMAL → Démarrage réussi
    // ---------------------------------------------------------
    @Test
    void testStartTask_NormalFlow() throws Exception {

        // ------- FAKE PLANIFICATION -------
        Planification p = new Planification();
        p.setId("P3000");
        p.setCodeSecret("EMP001");
        p.setNomSite("SiteA");
        p.setSiteDestination(new String[]{"SiteB"});
        p.setPersonneRemplacee("John Doe");

        p.setStatut(Planification.Statut.EN_ATTENTE);

        // ------- FAKE EMPLOYES -------
        Employe employe = new Employe();
        employe.setCodeSecret("EMP001");

        Employe employeRemplace = new Employe();
        employeRemplace.setPrenom("John");
        employeRemplace.setNom("Doe");

        // ------- FAKE AGENCES -------
        Agence siteA = new Agence();
        siteA.setNom("SiteA");

        Agence siteB = new Agence();
        siteB.setNom("SiteB");

        // ------- MOCK CALLS -------
        when(repository.findById("P3000")).thenReturn(Optional.of(p));
        when(employeServices.getBycodeSecret("EMP001")).thenReturn(employe);
        when(agencesServices.getByNom("SiteA")).thenReturn(siteA);
        when(agencesServices.getByNom("SiteB")).thenReturn(siteB);
        when(employeServices.employeeRemplacee("John", "Doe")).thenReturn(employeRemplace);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // ------- EXECUTION -------
        var method = service.getClass().getDeclaredMethod("startTask", Planification.class);
        method.setAccessible(true);
        method.invoke(service, p);

        // ------- ASSERTIONS -------
        assertEquals(Planification.Statut.EN_COURS, p.getStatut());
        assertTrue(siteA.isDeplacementEmploye());
        assertTrue(siteB.isReceptionEmploye());
        assertTrue(employeRemplace.isRemplacement());

        verify(repository).save(p);
        verify(employeServices).save(employe);
        verify(agencesRepository, times(2)).save(any());
        verify(employeRepository).save(employeRemplace);
    }
}
