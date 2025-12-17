package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Planification;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.repositories.PlanificationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.*;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PlanificationServiceScheduleTest {

    @Mock private PlanificationRepository repository;
    @Mock private TaskScheduler scheduler;
    @Mock private EmployeServices employeServices;
    @Mock private AgencesServices agencesServices;
    @Mock private EmployeRepository employeRepository;
    @Mock private AgencesRepository agencesRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private Clock clock;


    @InjectMocks
    private PlanificationService service;

    private Method scheduleMethod;
    private Map<String, ScheduledFuture<?>> startMap;
    private Map<String, ScheduledFuture<?>> endMap;

    @BeforeEach
    void setup() throws Exception {

        // ⏱️ TEMPS CONTRÔLÉ POUR LES TESTS
        lenient().when(clock.instant())
                .thenReturn(Instant.parse("2025-01-01T00:00:00Z"));

        lenient().when(clock.getZone())
                .thenReturn(ZoneId.systemDefault());

        scheduleMethod =
                service.getClass().getDeclaredMethod("scheduleStartAndEnd", Planification.class);
        scheduleMethod.setAccessible(true);

        startMap = new ConcurrentHashMap<>();
        endMap = new ConcurrentHashMap<>();

        Field f1 = service.getClass().getDeclaredField("startFutures");
        f1.setAccessible(true);
        f1.set(service, startMap);

        Field f2 = service.getClass().getDeclaredField("endFutures");
        f2.setAccessible(true);
        f2.set(service, endMap);
    }



    // ---------------------------
    // Helper pour créer Date + Time
    // ---------------------------
    private Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant());
    }

    // ============================================================================
    // 1️⃣ CASE : END BEFORE START → statut = EXECUTEE + AUCUNE PLANIFICATION
    // ============================================================================
    @Test
    void testSchedule_WhenEndBeforeStart() throws Exception {

        Planification p = new Planification();
        p.setId("T1");
        p.setDateDebut(date(2025, 1, 10));
        p.setHeureDebut("10:00");
        p.setDateFin(date(2025, 1, 9)); // PLUS TÔT
        p.setHeureFin("09:00");

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleMethod.invoke(service, p);

        assertEquals(Planification.Statut.EXECUTEE, p.getStatut());
        verify(repository).save(p);
        assertTrue(startMap.isEmpty());
        assertTrue(endMap.isEmpty());
    }


    // ============================================================================
    // 2️⃣ CASE : NOW < START → EN_ATTENTE + 2 TÂCHES PLANIFIÉES
    // ============================================================================
    @Test
    void testSchedule_BeforeStart_TwoTasksPlanned() throws Exception {

        // Now is earlier than start time
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        Planification p = new Planification();
        p.setId("T2");
        p.setDateDebut(date(2025, 2, 1));
        p.setHeureDebut("10:00");
        p.setDateFin(date(2025, 2, 1));
        p.setHeureFin("18:00");

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Mock TaskScheduler.schedule → renvoyer ScheduledFuture fake
        ScheduledFuture<?> fakeFuture = mock(ScheduledFuture.class);

        doReturn(fakeFuture)
                .when(scheduler)
                .schedule(any(Runnable.class), any(Instant.class));




        scheduleMethod.invoke(service, p);

        assertEquals(Planification.Statut.EN_ATTENTE, p.getStatut());
        verify(repository).save(p);

        // 2 tâches doivent être stockées
        assertEquals(1, startMap.size());
        assertEquals(1, endMap.size());
    }


    // ============================================================================
    // 3️⃣ CASE : START <= NOW < END → EN_COURS + 1 TASK PLANIFIED (end)
    // ============================================================================
    @Test
    void testSchedule_DuringTask_OnlyEndPlanned() throws Exception {

        // Now is during task
        Instant now = Instant.now(); // implicit

        Planification p = new Planification();
        p.setId("T3");
        p.setDateDebut(date(2025, 1, 1));
        p.setHeureDebut("00:00");
        p.setDateFin(date(2030, 1, 1));
        p.setHeureFin("00:00"); // far in the future

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScheduledFuture<?> fakeF = mock(ScheduledFuture.class);
        doReturn(fakeF)
                .when(scheduler)
                .schedule(any(Runnable.class), any(Instant.class));


        scheduleMethod.invoke(service, p);

        assertEquals(Planification.Statut.EN_COURS, p.getStatut());
        verify(repository).save(p);

        // Seule la fin doit être planifiée
        assertEquals(0, startMap.size());
        assertEquals(1, endMap.size());
    }


    // ============================================================================
    // 4️⃣ CASE : NOW >= END → EXECUTEE + AUCUNE PLANIFICATION
    // ============================================================================
    @Test
    void testSchedule_AfterEnd_NoPlan() throws Exception {

        Planification p = new Planification();
        p.setId("T4");
        p.setDateDebut(date(2020, 1, 1));
        p.setHeureDebut("00:00");
        p.setDateFin(date(2020, 1, 1));
        p.setHeureFin("01:00");

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduleMethod.invoke(service, p);

        assertEquals(Planification.Statut.EXECUTEE, p.getStatut());
        verify(repository).save(p);

        assertTrue(startMap.isEmpty());
        assertTrue(endMap.isEmpty());
    }
}
