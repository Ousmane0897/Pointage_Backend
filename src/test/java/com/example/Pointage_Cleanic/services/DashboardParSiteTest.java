package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.repositories.PointageRepository;

import com.example.Pointage_Cleanic.repositories.projections.EmployeIdProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class DashboardParSiteTest {

    @Mock
    private EmployeRepository employeRepository;

    @Mock
    private PointageRepository pointageRepository;

    @InjectMocks
    private DashboardParSite dashboardParSite;

    private EmployeIdProjection e1;
    private EmployeIdProjection e2;
    private EmployeIdProjection e3;
    private EmployeIdProjection e4;
    private EmployeIdProjection e5;

    @BeforeEach
    void setUp() {
        e1 = mockEmployeId("E1");
        e2 = mockEmployeId("E2");
        e3 = mockEmployeId("E3");
        e4 = mockEmployeId("E4");
        e5 = mockEmployeId("E5");
    }

    private EmployeIdProjection mockEmployeId(String id) {
        EmployeIdProjection projection = mock(EmployeIdProjection.class);
        when(projection.getId()).thenReturn(id);
        return projection;
    }

    @Test
    void testGetDashboardStatsBySite() {

        when(employeRepository.findAllDistinctSites())
                .thenReturn(List.of("Dakar", "Thies"));

        when(employeRepository.findEmployeIdsBySite("Dakar"))
                .thenReturn(List.of(e1, e2, e3));

        when(employeRepository.findEmployeIdsBySite("Thies"))
                .thenReturn(List.of(e4, e5));

        when(pointageRepository.countByDateAndIdIn(
                anyString(),
                ArgumentMatchers.<List<String>>argThat(
                        ids -> ids != null
                                && ids.size() == 3
                                && ids.containsAll(List.of("E1", "E2", "E3"))
                )
        )).thenReturn(2L);



        when(pointageRepository.countByDateAndIdIn(
                anyString(),
                ArgumentMatchers.<List<String>>argThat(
                        ids -> ids != null
                                && ids.size() == 2
                                && ids.containsAll(List.of("E4", "E5"))
                )
        )).thenReturn(1L);




        Map<String, Map<String, Long>> result =
                dashboardParSite.getDashboardStatsBySite();

        assertNotNull(result);
        assertEquals(2, result.size());

        Map<String, Long> dakarStats = result.get("Dakar");
        assertEquals(3, dakarStats.get("total"));
        assertEquals(2, dakarStats.get("present"));
        assertEquals(1, dakarStats.get("absent"));

        Map<String, Long> thiesStats = result.get("Thies");
        assertEquals(2, thiesStats.get("total"));
        assertEquals(1, thiesStats.get("present"));
        assertEquals(1, thiesStats.get("absent"));
    }
}
