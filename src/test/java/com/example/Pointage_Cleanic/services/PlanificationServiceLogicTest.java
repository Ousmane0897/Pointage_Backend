package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Planification;
import com.example.Pointage_Cleanic.repositories.PlanificationRepository;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanificationServiceLogicTest {

    @Mock private PlanificationRepository repository;
    @Mock private TaskScheduler taskScheduler;
    @Mock private EmployeServices employeServices;
    @Mock private AgencesServices agencesServices;
    @Mock private EmployeRepository employeRepository;
    @Mock private AgencesRepository agencesRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PlanificationService service;

    // -------------------------------------------------------
    // difference() : durée entre deux heures
    // -------------------------------------------------------
    @Test
    void testDifference_NormalCase() {
        String diff = service.difference("08:00", "12:30");
        assertEquals("04:30", diff);
    }

    @Test
    void testDifference_CrossMidnight() {
        String diff = service.difference("23:00", "01:00");
        assertEquals("02:00", diff);
    }

    // -------------------------------------------------------
    // differenceInDays()
    // -------------------------------------------------------
    @Test
    void testDifferenceInDays_Positive() {
        Date start = Date.from(LocalDate.of(2024, 1, 1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(LocalDate.of(2024, 1, 10)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());

        long days = service.differenceInDays(start, end);
        assertEquals(9, days);
    }

    @Test
    void testDifferenceInDays_Negative() {
        Date start = Date.from(LocalDate.of(2024, 1, 10)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(LocalDate.of(2024, 1, 1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());

        long days = service.differenceInDays(start, end);
        assertEquals(-9, days);
    }

    // -------------------------------------------------------
    // comparerHeures()
    // -------------------------------------------------------
    @Test
    void testComparerHeures() {
        assertTrue(service.comparerHeures("08:00", "09:00") < 0);
        assertTrue(service.comparerHeures("10:00", "09:00") > 0);
        assertEquals(0, service.comparerHeures("12:00", "12:00"));
    }

    // -------------------------------------------------------
    // multiplierHeure()
    // -------------------------------------------------------
    @Test
    void testMultiplierHeure() {
        String result = service.multiplierHeure("02:30", 3);
        assertEquals("07:30", result);
    }

    // -------------------------------------------------------
    // parseToDateTime()
    // -------------------------------------------------------
    @Test
    void testParseToDateTime() {
        Date date = Date.from(LocalDate.of(2024, 1, 15)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());

        LocalDateTime dt =
                invokeParseToDateTime(service, date, "14:45");

        assertEquals(2024, dt.getYear());
        assertEquals(1, dt.getMonthValue());
        assertEquals(15, dt.getDayOfMonth());
        assertEquals(14, dt.getHour());
        assertEquals(45, dt.getMinute());
    }

    // Utilitaire pour appeler une méthode private via réflexion
    private LocalDateTime invokeParseToDateTime(PlanificationService svc, Date d, String h) {
        try {
            var m = PlanificationService.class
                    .getDeclaredMethod("parseToDateTime", Date.class, String.class);
            m.setAccessible(true);
            return (LocalDateTime) m.invoke(svc, d, h);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
