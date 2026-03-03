package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Absent;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Ferie;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AbsentService {

    private final MongoTemplate mongoTemplate;
    private final EmployeRepository employeRepository;
    private final PointageRepository pointageRepository;
    private final Clock clock; // Injecté pour tests et production

    // Méthode pour récupérer la date du jour
    protected LocalDate getTodayDate() {
        return LocalDate.now(clock);
    }

    public List<Absent> getAll() {
        return mongoTemplate.findAll(Absent.class);
    }

    public Absent getBycodeSecret(String codeSecret) {
        Query query = new Query();
        query.addCriteria(Criteria.where("codeSecret").is(codeSecret));
        return mongoTemplate.findOne(query, Absent.class);
    }

    public List<Absent> findAbsencesDynamiques() {

        LocalDate today = LocalDate.now(ZoneId.of("Africa/Dakar"));

        // Tous les employés
        List<Employe> allEmployes = employeRepository.findAll();
        if (allEmployes.isEmpty()) return Collections.emptyList();

        // Tous les pointages du jour
        List<Pointage> pointagesToday = pointageRepository.findAllByDate(today);
        Set<String> codesPresent = pointagesToday.stream()
                .map(Pointage::getCodeSecret)
                .collect(Collectors.toSet());

        // Filtrer absents
        return allEmployes.stream()
                .filter(e -> !codesPresent.contains(e.getCodeSecret()))
                .map(e -> Absent.builder()
                        .codeSecret(e.getCodeSecret())
                        .prenom(e.getPrenom())
                        .nom(e.getNom())
                        .numero(e.getNumero())
                        .dateAbsence(today)
                        .motif("Pas encore pointé")
                        .justification("Aucune justification")
                        .intervention(e.getIntervention())
                        .site(e.getSite())
                        .build())
                .toList();
    }

    @Scheduled(cron = "0 52 11 * * *", zone = "Africa/Dakar")
    public void findAndStoreAbsentEmployees() {

        LocalDate today = LocalDate.now(ZoneId.of("Africa/Dakar"));

        // 1️⃣ Week-end
        if (today.getDayOfWeek() == DayOfWeek.SATURDAY ||
                today.getDayOfWeek() == DayOfWeek.SUNDAY) return;

        // 2️⃣ Jour férié
        if (mongoTemplate.exists(
                new Query(Criteria.where("date").is(today)),
                Ferie.class)) return;

        // 3️⃣ Absences dynamiques
        List<Absent> absents = findAbsencesDynamiques();
        if (absents.isEmpty()) return;

        // 4️⃣ Absences déjà enregistrées
        Set<String> codesDejaAbsents = mongoTemplate.find(
                        new Query(Criteria.where("dateAbsence").is(today)),
                        Absent.class
                ).stream()
                .map(Absent::getCodeSecret)
                .collect(Collectors.toSet());

        // 5️⃣ Nouveaux absents
        List<Absent> absentsAInserer = absents.stream()
                .filter(a -> !codesDejaAbsents.contains(a.getCodeSecret()))
                .toList();

        // 6️⃣ Insertion finale
        if (!absentsAInserer.isEmpty()) {
            mongoTemplate.insertAll(absentsAInserer);
        }
    }

}
