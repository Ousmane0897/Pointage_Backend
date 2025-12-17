package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Absent;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Ferie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class AbsentServiceTest {

    @Mock
    MongoTemplate mongoTemplate;

    @InjectMocks
    AbsentService absentService;

    // ------------------- SIMPLE TESTS -------------------

    @Test
    void testGetAll() {
        when(mongoTemplate.findAll(Absent.class)).thenReturn(List.of(new Absent(), new Absent()));

        List<Absent> result = absentService.getAll();

        assertEquals(2, result.size());
        verify(mongoTemplate).findAll(Absent.class);
    }

    @Test
    void testGetByCodeSecret() {
        Absent absent = new Absent();
        when(mongoTemplate.findOne(any(Query.class), eq(Absent.class))).thenReturn(absent);

        Absent result = absentService.getBycodeSecret("XYZ123");

        assertNotNull(result);
        verify(mongoTemplate).findOne(any(Query.class), eq(Absent.class));
    }


    // ------------------- TEST findAbsencesDynamiques -------------------

    @Test
    void testFindAbsencesDynamiques_returnsEmptyList_whenFerie() {

        when(mongoTemplate.exists(any(Query.class), eq(Ferie.class))).thenReturn(true);

        List<Absent> result = absentService.findAbsencesDynamiques();

        assertTrue(result.isEmpty());
        verify(mongoTemplate).exists(any(Query.class), eq(Ferie.class));
    }

    @Test
    void testFindAbsencesDynamiques_returnsAbsentList() {

        // 1️⃣ Not a ferie
        when(mongoTemplate.exists(any(Query.class), eq(Ferie.class)))
                .thenReturn(false);

        // 2️⃣ Employé absent simulé
        Employe e = new Employe();
        e.setCodeSecret("A123");
        e.setPrenom("Ousmane");
        e.setNom("Diouf");

        AggregationResults<Employe> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(List.of(e));

        when(mongoTemplate.aggregate(
                any(org.springframework.data.mongodb.core.aggregation.Aggregation.class),
                eq("employes"),
                eq(Employe.class)
        )).thenReturn(mockResults);

        // 3️⃣ Execution
        List<Absent> result = absentService.findAbsencesDynamiques();

        // 4️⃣ Assertions
        assertEquals(1, result.size());
        assertEquals("A123", result.get(0).getCodeSecret());
        assertEquals("Pas encore pointé", result.get(0).getMotif());

        verify(mongoTemplate).aggregate(
                any(org.springframework.data.mongodb.core.aggregation.Aggregation.class),
                eq("employes"),
                eq(Employe.class)
        );
    }



    // ------------------- TEST Scheduled METHOD -------------------

    @Test
    void testFindAndStoreAbsentEmployees_noAction_whenWeekend() {

        AbsentService spyService = spy(absentService);
        doReturn(Collections.emptyList()).when(spyService).findAbsencesDynamiques();

        spyService.findAndStoreAbsentEmployees();

        verify(mongoTemplate, never()).insertAll(any());
    }

    @Test
    void testFindAndStoreAbsentEmployees_noAction_whenFerie() {

        when(mongoTemplate.exists(any(Query.class), eq(Ferie.class))).thenReturn(true);

        absentService.findAndStoreAbsentEmployees();

        verify(mongoTemplate, never()).insertAll(any());
    }

    @Test
    void testFindAndStoreAbsentEmployees_noAction_whenNoAbsences() {

        when(mongoTemplate.exists(any(Query.class), eq(Ferie.class))).thenReturn(false);

        AbsentService spyService = spy(absentService);
        doReturn(Collections.emptyList()).when(spyService).findAbsencesDynamiques();

        spyService.findAndStoreAbsentEmployees();

        verify(mongoTemplate, never()).insertAll(any());
    }

    @Test
    void testFindAndStoreAbsentEmployees_insertsAbsence_whenValid() {

        when(mongoTemplate.exists(any(Query.class), eq(Ferie.class))).thenReturn(false);

        Absent a = new Absent();
        List<Absent> absents = List.of(a);

        AbsentService spyService = spy(absentService);
        doReturn(absents).when(spyService).findAbsencesDynamiques();

        when(mongoTemplate.exists(any(Query.class), eq(Absent.class))).thenReturn(false);

        spyService.findAndStoreAbsentEmployees();

        verify(mongoTemplate).insertAll(absents);
    }

}
