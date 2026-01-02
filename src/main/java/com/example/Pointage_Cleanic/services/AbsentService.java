package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Absent;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Ferie;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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
        LocalDate today = getTodayDate();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String todayStr = today.format(dateFormatter);

        // Vérification week-end
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return Collections.emptyList();
        }

        // Vérification jour férié
        Query ferieQuery = new Query(Criteria.where("date").is(todayStr));
        if (mongoTemplate.exists(ferieQuery, Ferie.class)) {
            return Collections.emptyList();
        }

        // Tous les employés
        List<Employe> allEmployes = employeRepository.findAll();

        // Tous les pointages du jour
        List<Pointage> pointagesToday = pointageRepository.findAllByDate(todayStr);
        List<String> codesPresent = pointagesToday.stream()
                .map(Pointage::getCodeSecret)
                .collect(Collectors.toList());

        // Filtrer absents
        List<Employe> absentEmployes = allEmployes.stream()
                .filter(e -> !codesPresent.contains(e.getCodeSecret()))
                .collect(Collectors.toList());

        // Transformer en objets Absent (non persistés)
        return absentEmployes.stream().map(e -> {
            Absent a = new Absent();
            a.setId(null);
            a.setCodeSecret(e.getCodeSecret());
            a.setPrenom(e.getPrenom());
            a.setNom(e.getNom());
            a.setNumero(e.getNumero());
            a.setMotif("Pas encore pointé");
            a.setJustification("Aucune justification");
            a.setIntervention(e.getIntervention());
            a.setSite(e.getSite());
            a.setDateAbsence(todayStr);
            return a;
        }).collect(Collectors.toList());
    }

    @Scheduled(cron = "0 30 21 * * *", zone = "Africa/Dakar")
    public void findAndStoreAbsentEmployees() {
        LocalDate today = getTodayDate();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String formattedDate = today.format(dateFormatter);

        // Week-end
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) return;

        // Jour férié
        Query ferieQuery = new Query(Criteria.where("date").is(formattedDate));
        if (mongoTemplate.exists(ferieQuery, Ferie.class)) return;

        // Absents dynamiques
        List<Absent> absencesDynamiques = findAbsencesDynamiques();
        if (absencesDynamiques.isEmpty()) return;

        // Vérifie si déjà enregistrés
        Query check = new Query(Criteria.where("dateAbsence").is(formattedDate));
        if (mongoTemplate.exists(check, Absent.class)) return;

        // Enregistre
        mongoTemplate.insertAll(absencesDynamiques);
    }
}
