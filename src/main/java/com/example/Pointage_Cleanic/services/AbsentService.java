package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.Absent;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Ferie;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AbsentService {

    private final MongoTemplate mongoTemplate;


    public List<Absent> getAll() {

        return mongoTemplate.findAll(Absent.class);
    }

    public Absent getBycodeSecret(String codeSecret) {
        Query query = new Query();
        query.addCriteria(Criteria.where("codeSecret").is(codeSecret));
        return mongoTemplate.findOne(query,Absent.class);
    }

    @Scheduled(cron = "0 20 11 * * *", zone = "Africa/Dakar")
    public void findAndStoreAbsentEmployees() {

        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.of("Africa/Dakar");

        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();

        DateTimeFormatter frenchDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String formattedDate = today.format(frenchDateFormatter);


        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String formatDate = today.format(formatter);

        System.out.println("Heure du serveur : " + LocalDateTime.now());

        DayOfWeek dayOfWeek = today.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            System.out.println("Week-end détecté (" + dayOfWeek + ") - pas de traitement.");
            return;
        }

        // Vérifie jour férié
        Query ferieQuery = new Query(Criteria.where("date").is(formatDate));
        boolean isFerie = mongoTemplate.exists(ferieQuery, Ferie.class);
        if (isFerie) {
            System.out.println("Jour férié détecté (" + today + ") - pas de traitement.");
            return;
        }

        // Agrégation pour trouver les employés sans pointage aujourd'hui
        List<AggregationOperation> operations = Arrays.asList(
                Aggregation.lookup("pointages", "codeSecret", "codeSecret", "pointagesToday"),
                Aggregation.addFields()
                        .addFieldWithValue("pointagesToday",
                                ArrayOperators.Filter.filter("pointagesToday")
                                        .as("pt")
                                        .by(
                                                BooleanOperators.And.and(
                                                        ComparisonOperators.Gte.valueOf("$$pt.timestamp").greaterThanEqualToValue(start),
                                                        ComparisonOperators.Lt.valueOf("$$pt.timestamp").lessThanValue(end)
                                                )
                                        )
                        ).build(),
                Aggregation.match(Criteria.where("pointagesToday").size(0))

        );

        Aggregation aggregation = Aggregation.newAggregation(operations);
        AggregationResults<Employe> results = mongoTemplate.aggregate(aggregation, "employes", Employe.class);
        List<Employe> absentEmployes = results.getMappedResults();

        System.out.println("Nombre d'absents détectés : " + absentEmployes.size());
        absentEmployes.forEach(e -> System.out.println("Absent : " + e.getPrenom() + " " + e.getNom()));

        // Vérifie si absences déjà enregistrées
        Query check = new Query(Criteria.where("dateAbsence").is(today));
        boolean alreadyExists = mongoTemplate.exists(check, Absent.class);
        if (alreadyExists) {
            System.out.println("Absences déjà enregistrées pour le " + today);
            return;
        }

        // Sauvegarde
        List<Absent> absentsToSave = absentEmployes.stream()
                .map(e -> {
                    Absent a = new Absent();
                    a.setCodeSecret(e.getCodeSecret());
                    a.setPrenom(e.getPrenom());
                    a.setNom(e.getNom());
                    a.setNumero(e.getNumero());
                    a.setMotif("Pas de motif");
                    a.setJustification("Pas de justification");
                    a.setSite(e.getSite());
                    a.setIntervention(e.getIntervention());
                    a.setDateAbsence(formattedDate);
                    return a;
                })
                .collect(Collectors.toList());

        mongoTemplate.insertAll(absentsToSave);
        System.out.println("Absents enregistrés pour le " + today);
    }





}
