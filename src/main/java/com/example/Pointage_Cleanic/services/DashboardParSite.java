package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.projections.EmployeIdProjection;
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

        String todayStr = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        List<String> allSites = employeRepository.findAllDistinctSites();
        Map<String, Map<String, Long>> siteStats = new HashMap<>();

        for (String site : allSites) {

            // ✅ Projection conservée
            List<String> employeIds = employeRepository
                    .findEmployeIdsBySite(site)
                    .stream()
                    .map(EmployeIdProjection::getId)
                    .toList();

            long total = employeIds.size();

            long present = employeIds.isEmpty()
                    ? 0
                    : pointageRepository.countByDateAndIdIn(todayStr, employeIds);

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
