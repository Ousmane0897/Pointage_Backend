package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Absent;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Ferie;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.*;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class AbsentServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private EmployeRepository employeRepository;

    @Mock
    private PointageRepository pointageRepository;

    private Clock fixedClock;

    @InjectMocks
    private AbsentService absentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fixedClock = Clock.fixed(LocalDate.of(2025, 12, 26)
                .atStartOfDay(ZoneId.of("Africa/Dakar")).toInstant(), ZoneId.of("Africa/Dakar"));
        absentService = new AbsentService(mongoTemplate, employeRepository, pointageRepository, fixedClock);
    }

    @Test
    void testGetAll() {
        List<Absent> absents = List.of(new Absent(), new Absent());
        when(mongoTemplate.findAll(Absent.class)).thenReturn(absents);

        List<Absent> result = absentService.getAll();

        assertEquals(2, result.size());
        verify(mongoTemplate, times(1)).findAll(Absent.class);
    }

    @Test
    void testGetBycodeSecret() {
        Absent a = new Absent();
        a.setCodeSecret("123");
        when(mongoTemplate.findOne(any(Query.class), eq(Absent.class))).thenReturn(a);

        Absent result = absentService.getBycodeSecret("123");

        assertNotNull(result);
        assertEquals("123", result.getCodeSecret());
        verify(mongoTemplate, times(1)).findOne(any(Query.class), eq(Absent.class));
    }

    @Test
    void testFindAbsencesDynamiques_WeekEnd() {
        Clock saturdayClock = Clock.fixed(LocalDate.of(2025, 12, 27)
                .atStartOfDay(ZoneId.of("Africa/Dakar")).toInstant(), ZoneId.of("Africa/Dakar"));
        absentService = new AbsentService(mongoTemplate, employeRepository, pointageRepository, saturdayClock);

        List<Absent> absents = absentService.findAbsencesDynamiques();
        assertTrue(absents.isEmpty());
    }

    @Test
    void testFindAbsencesDynamiques_JourFerie() {
        when(mongoTemplate.exists(any(Query.class), eq(Ferie.class))).thenReturn(true);

        List<Absent> absents = absentService.findAbsencesDynamiques();
        assertTrue(absents.isEmpty());
    }

    @Test
    void testFindAbsencesDynamiques_JourOuvrable_Complet() {
        when(mongoTemplate.exists(any(Query.class), eq(Ferie.class))).thenReturn(false);

        Employe e1 = Employe.builder().codeSecret("A").prenom("John").nom("Doe").numero("001")
                .intervention("Vitre").site(new String[]{"Site1"}).build();
        Employe e2 = Employe.builder().codeSecret("B").prenom("Jane").nom("Smith").numero("002")
                .intervention("Bureau").site(new String[]{"Site2"}).build();

        when(employeRepository.findAll()).thenReturn(List.of(e1, e2));

        Pointage p = new Pointage();
        p.setCodeSecret("A");
        when(pointageRepository.findAllByDate(anyString())).thenReturn(List.of(p));

        List<Absent> absents = absentService.findAbsencesDynamiques();

        assertEquals(1, absents.size());
        Absent absent = absents.get(0);
        assertEquals("B", absent.getCodeSecret());
    }

    @Test
    void testFindAndStoreAbsentEmployees_noAction_whenWeekend() {
        Clock saturdayClock = Clock.fixed(LocalDate.of(2025, 12, 27)
                .atStartOfDay(ZoneId.of("Africa/Dakar")).toInstant(), ZoneId.of("Africa/Dakar"));
        absentService = new AbsentService(mongoTemplate, employeRepository, pointageRepository, saturdayClock);

        absentService.findAndStoreAbsentEmployees();
        verify(mongoTemplate, never()).insertAll(any());
    }

    @Test
    void testFindAndStoreAbsentEmployees_noAction_whenFerie() {
        when(mongoTemplate.exists(any(Query.class), eq(Ferie.class))).thenReturn(true);

        absentService.findAndStoreAbsentEmployees();
        verify(mongoTemplate, never()).insertAll(any());
    }

    @Test
    void testFindAndStoreAbsentEmployees_insertsAbsence_whenValid() {
        when(mongoTemplate.exists(any(Query.class), eq(Ferie.class))).thenReturn(false);
        when(mongoTemplate.exists(any(Query.class), eq(Absent.class))).thenReturn(false);

        Absent a = new Absent();
        List<Absent> absents = List.of(a);

        AbsentService spyService = spy(absentService);
        doReturn(absents).when(spyService).findAbsencesDynamiques();

        spyService.findAndStoreAbsentEmployees();
        verify(mongoTemplate).insertAll(absents);
    }
}
