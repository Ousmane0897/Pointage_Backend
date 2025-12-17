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

import java.lang.reflect.Method;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PlanificationServiceEndTaskTest {

    @Mock private PlanificationRepository repository;
    @Mock private EmployeServices employeServices;
    @Mock private AgencesServices agencesServices;
    @Mock private EmployeRepository employeRepository;
    @Mock private AgencesRepository agencesRepository;
    @Mock private TaskScheduler taskScheduler;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PlanificationService service;


    // ---------------------------------------------------------
    // 1️⃣ Planification inexistante → aucune action
    // ---------------------------------------------------------
    @Test
    void testEndTask_NotFound() throws Exception {

        Planification p = new Planification();
        p.setId("X1000");

        when(repository.findById("X1000")).thenReturn(Optional.empty());

        Method method = service.getClass().getDeclaredMethod("endTask", Planification.class);
        method.setAccessible(true);
        method.invoke(service, p);

        verify(repository).findById("X1000");
        verifyNoMoreInteractions(repository, employeServices, agencesServices);
    }


    // ---------------------------------------------------------
    // 2️⃣ Statut EXÉCUTÉE / ANNULEE → rien ne se passe
    // ---------------------------------------------------------
    @Test
    void testEndTask_AlreadyExecutedOrCancelled() throws Exception {

        Planification p = new Planification();
        p.setId("X2000");
        p.setStatut(Planification.Statut.EXECUTEE);

        when(repository.findById("X2000")).thenReturn(Optional.of(p));

        Method method = service.getClass().getDeclaredMethod("endTask", Planification.class);
        method.setAccessible(true);
        method.invoke(service, p);

        verify(repository, never()).save(any());
        verifyNoInteractions(employeServices);
    }


    // ---------------------------------------------------------
    // 3️⃣ Cas NORMAL → fin de planification réussie
    // ---------------------------------------------------------
    @Test
    void testEndTask_NormalFlow() throws Exception {

        // -----------------------
        // FAKE PLANIFICATION
        // -----------------------
        Planification plan = new Planification();
        plan.setId("X3000");
        plan.setCodeSecret("EMP001");
        plan.setNomSite("SiteA");
        plan.setSiteDestination(new String[]{"SiteB"});
        plan.setPersonneRemplacee("John Doe");
        plan.setStatut(Planification.Statut.EN_COURS);

        // -----------------------
        // FAKE EMPLOYÉS
        // -----------------------
        Employe employe = new Employe();
        employe.setCodeSecret("EMP001");
        employe.setSiteAvantDeplacement("SiteA");

        Employe employeRemplace = new Employe();
        employeRemplace.setPrenom("John");
        employeRemplace.setNom("Doe");

        // -----------------------
        // FAKE AGENCES
        // -----------------------
        Agence siteA = new Agence();
        siteA.setNom("SiteA");

        Agence siteB = new Agence();
        siteB.setNom("SiteB");

        // -----------------------
        // MOCK DES APPELS
        // -----------------------
        when(repository.findById("X3000")).thenReturn(Optional.of(plan));
        when(employeServices.getBycodeSecret("EMP001")).thenReturn(employe);
        when(agencesServices.getByNom("SiteA")).thenReturn(siteA);
        when(agencesServices.getByNom("SiteB")).thenReturn(siteB);
        when(employeServices.employeeRemplacee("John", "Doe")).thenReturn(employeRemplace);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // -----------------------
        // EXECUTION VIA REFLEXION
        // -----------------------
        Method method = service.getClass().getDeclaredMethod("endTask", Planification.class);
        method.setAccessible(true);
        method.invoke(service, plan);

        // -----------------------
        // ASSERTIONS FINALES
        // -----------------------
        assertEquals(Planification.Statut.EXECUTEE, plan.getStatut());
        assertFalse(siteA.isDeplacementEmploye());
        assertFalse(siteB.isReceptionEmploye());

        assertFalse(employeRemplace.isRemplacement());
        assertFalse(employe.isDeplacement());
        assertNull(employe.getHorairesDeRemplacement());
        assertNull(employe.getPersonneRemplacee());

        assertEquals("SiteA", employe.getSite()[0]); // retour au site d'origine

        // Vérification des sauvegardes
        verify(repository).save(plan);
        verify(employeServices).save(employe);
        verify(employeRepository).save(employeRemplace);
        verify(agencesRepository, times(2)).save(any());
    }
}
