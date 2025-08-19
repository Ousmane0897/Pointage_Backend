package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {


    private final EmployeRepository employeRepository;
    private final PointageRepository pointageRepository;

    public Map<String, Long> getDashboardStats() {
        long total = employeRepository.count();

        LocalDate today = LocalDate.now();
        DateTimeFormatter frenchFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String todayStr = today.format(frenchFormatter);
        long present = pointageRepository.countByDate(todayStr);

        long absent = total - present;

        Map<String, Long> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("present", present);
        stats.put("absent", absent);

        return stats;
    }
}
