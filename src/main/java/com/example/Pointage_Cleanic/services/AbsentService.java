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
        return mongoTemplate.findOne(query, Absent.class);
    }

    /**
     * 🔹 Méthode dynamique :
     * retourne les absents "du moment" (non pointés aujourd’hui)
     * sous forme d’objets Absent non persistés.
     * Lister les absents en temps réel (non pointés aujourd’hui)
     * ⚙️ Concrètement :
     * Le service regarde tous les employés.
     * Il fait une agrégation :
     * jointure avec la collection pointages (via le codeSecret),
     * filtre pour ne garder que les pointages du jour courant,
     * sélectionne ceux qui n’ont aucun pointage aujourd’hui.
     * Il renvoie ces employés sous forme d’objets Absent temporaires.
     */
    public List<Absent> findAbsencesDynamiques() {
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.of("Africa/Dakar");

        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();

        // Format pour l'affichage dans dateAbsence
        DateTimeFormatter frenchDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String formattedDate = today.format(frenchDateFormatter);

        // Vérifie jour férié
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String formatDate = today.format(formatter);
        Query ferieQuery = new Query(Criteria.where("date").is(formatDate));
        boolean isFerie = mongoTemplate.exists(ferieQuery, Ferie.class);
        if (isFerie) return Collections.emptyList();

        // Ignore weekEnd
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return Collections.emptyList();
        }

        // Agrégation pour trouver les employés sans pointage aujourd’hui
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

        // 🔄 Transformer les employés absents en objets Absent (non stockés)
        return absentEmployes.stream().map(e -> {
            Absent a = new Absent();
            a.setId(null); // pas encore stocké
            a.setCodeSecret(e.getCodeSecret());
            a.setPrenom(e.getPrenom());
            a.setNom(e.getNom());
            a.setNumero(e.getNumero());
            a.setMotif("Pas encore pointé");
            a.setJustification("Aucune justification");
            a.setIntervention(e.getIntervention());
            a.setSite(e.getSite());
            a.setDateAbsence(formattedDate);
            return a;
        }).collect(Collectors.toList());
    }

    /**
     * 🔹 Méthode planifiée quotidienne (historique des absences)
     */
    @Scheduled(cron = "0 30 21 * * *", zone = "Africa/Dakar")
    public void findAndStoreAbsentEmployees() {

        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.of("Africa/Dakar");

        // Format pour affichage et requêtes
        DateTimeFormatter frenchDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String formattedDate = today.format(frenchDateFormatter);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String formatDate = today.format(formatter);

        System.out.println("🕒 Heure du serveur : " + LocalDateTime.now());
        System.out.println("📅 Vérification des absences du " + formattedDate);

        // 🔹 1️⃣ Vérification du week-end
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            System.out.println("⛱️ Week-end détecté (" + dayOfWeek + ") - pas de traitement.");
            return;
        }

        // 🔹 2️⃣ Vérification jour férié
        Query ferieQuery = new Query(Criteria.where("date").is(formatDate));
        boolean isFerie = mongoTemplate.exists(ferieQuery, Ferie.class);
        if (isFerie) {
            System.out.println("🎉 Jour férié détecté (" + today + ") - pas de traitement.");
            return;
        }

        // 🔹 3️⃣ Récupération des absents dynamiques (employés non pointés aujourd’hui)
        List<Absent> absencesDynamiques = findAbsencesDynamiques();

        if (absencesDynamiques.isEmpty()) {
            System.out.println("✅ Aucune absence détectée aujourd’hui.");
            return;
        }

        // 🔹 4️⃣ Vérifie si absences déjà enregistrées pour ce jour
        Query check = new Query(Criteria.where("dateAbsence").is(formattedDate));
        boolean alreadyExists = mongoTemplate.exists(check, Absent.class);
        if (alreadyExists) {
            System.out.println("⚠️ Absences déjà enregistrées pour le " + formattedDate);
            return;
        }

        // 🔹 5️⃣ Enregistre les absences en base MongoDB
        mongoTemplate.insertAll(absencesDynamiques);

        System.out.println("💾 Absents enregistrés pour le " + formattedDate + " : "
                + absencesDynamiques.size() + " employés.");
    }

}
