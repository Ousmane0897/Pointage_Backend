package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.repositories.PointageRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private EmployeRepository employeRepository;

    @Mock
    private PointageRepository pointageRepository;

    @InjectMocks
    private DashboardService dashboardService;


    // -------------------------------------------------------
    // TEST PRINCIPAL : getDashboardStats()
    // -------------------------------------------------------
    @Test
    void testGetDashboardStats() {

        // Total employés
        when(employeRepository.count()).thenReturn(10L);

        // Présents today
        when(pointageRepository.countByDate(LocalDate.parse(ArgumentMatchers.anyString()))).thenReturn(7L);

        Map<String, Long> stats = dashboardService.getDashboardStats();

        assertEquals(10L, stats.get("total"));
        assertEquals(7L, stats.get("present"));
        assertEquals(3L, stats.get("absent"));

        verify(employeRepository).count();
        verify(pointageRepository).countByDate(LocalDate.parse(ArgumentMatchers.anyString()));
    }


    // -------------------------------------------------------
    // CAS : aucun employé
    // -------------------------------------------------------
    @Test
    void testGetDashboardStats_zeroEmployees() {

        when(employeRepository.count()).thenReturn(0L);
        when(pointageRepository.countByDate(LocalDate.parse(ArgumentMatchers.anyString()))).thenReturn(0L);

        Map<String, Long> stats = dashboardService.getDashboardStats();

        assertEquals(0L, stats.get("total"));
        assertEquals(0L, stats.get("present"));
        assertEquals(0L, stats.get("absent"));
    }


    // -------------------------------------------------------
    // CAS : tout le monde présent
    // -------------------------------------------------------
    @Test
    void testGetDashboardStats_allPresent() {

        when(employeRepository.count()).thenReturn(5L);
        when(pointageRepository.countByDate(LocalDate.parse(ArgumentMatchers.anyString()))).thenReturn(5L);

        Map<String, Long> stats = dashboardService.getDashboardStats();

        assertEquals(5L, stats.get("total"));
        assertEquals(5L, stats.get("present"));
        assertEquals(0L, stats.get("absent"));
    }


    // -------------------------------------------------------
    // CAS : aucun présent
    // -------------------------------------------------------
    @Test
    void testGetDashboardStats_noPresent() {

        when(employeRepository.count()).thenReturn(8L);
        when(pointageRepository.countByDate(LocalDate.parse(ArgumentMatchers.anyString()))).thenReturn(0L);

        Map<String, Long> stats = dashboardService.getDashboardStats();

        assertEquals(8L, stats.get("total"));
        assertEquals(0L, stats.get("present"));
        assertEquals(8L, stats.get("absent"));
    }

}
