package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardParSite {

    private final EmployeRepository employeRepository;
    private final PointageRepository pointageRepository;

    public Map<String, Map<String, Long>> getDashboardStatsBySite() {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String todayStr = today.format(formatter);

        // Récupère tous les sites distincts
        List<String> allSites = employeRepository.findAllDistinctSites();

        Map<String, Map<String, Long>> siteStats = new HashMap<>();

        for (String site : allSites) {
            // Récupère tous les employés assignés à ce site
            List<String> employeIds = employeRepository.findEmployeIdsBySite(site);

            long total = employeIds.size();
            long present = pointageRepository.countByDateAndIdIn(todayStr, employeIds);
            long absent = total - present;

            Map<String, Long> stats = new HashMap<>();
            stats.put("total", total);
            stats.put("present", present);
            stats.put("absent", absent);

            siteStats.put(site, stats);
        }

        return siteStats;
    }


}
