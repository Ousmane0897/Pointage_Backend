package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PointageServices {

    private final MongoTemplate mongoTemplate;
    private final PointageRepository pointageRepository;

    Pointage save(Pointage pointage) {

        return mongoTemplate.save(pointage);
    }

    List<Pointage> getAll() {

        return mongoTemplate.findAll(Pointage.class);
    }

    public Pointage getPointageBycodeSecret(String codeSecret) {
        Query query = new Query();
        query.addCriteria(Criteria.where("codeSecret").is(codeSecret));
        return mongoTemplate.findOne(query, Pointage.class);
    }

    public Employe getEmployeBycodeSecret(String codeSecret) {
        Query query = new Query();

        // 1. Essai en tant que String
        query.addCriteria(Criteria.where("codeSecret").is(codeSecret));
        Employe employe = mongoTemplate.findOne(query, Employe.class);
        if (employe != null) {
            return employe;
        }

        // 2. Si échec, essai en tant qu'entier
        try {
            int codeAsInt = Integer.parseInt(codeSecret);
            query = new Query(); // Nouvelle instance
            query.addCriteria(Criteria.where("codeSecret").is(codeAsInt));
            employe = mongoTemplate.findOne(query, Employe.class);
        } catch (NumberFormatException e) {
            // codeSecret n'est pas un entier, on ignore
        }

        return employe;
    }



    public Pointage getBycodeSecretAndDate(String codeSecret) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter frenchFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String date = today.format(frenchFormatter);
        Query query = new Query();
        query.addCriteria(Criteria.where("codeSecret").is(codeSecret).and("date").is(date));
        return mongoTemplate.findOne(query, Pointage.class);
    }

    public String getHeureArriveByCurrentDateAndcodeSecret(String codeSecret) {

        LocalDate today = LocalDate.now();
        DateTimeFormatter frenchFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);
        String formattedDate = today.format(frenchFormatter);

        Query query = new Query(Criteria.where("codeSecret").is(codeSecret).and("date").is(formattedDate));
        query.fields().include("HeureArrive");

        Pointage result = mongoTemplate.findOne(query, Pointage.class);

        String HeureArrive = result != null ? result.getHeureArrive() : null;

        return HeureArrive;

    }

    public void updatePointage(String codeSecret, String date, String heureDepart, String duree, String status) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter frenchFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String formattedDate = today.format(frenchFormatter);

        Query query = new Query(Criteria.where("codeSecret").is(codeSecret).and("date").is(formattedDate));
        Update update = new Update().set("heureDepart", heureDepart).set("duree", duree).set("status", status);

        mongoTemplate.updateFirst(query, update, Pointage.class);
    }

    public boolean canPoint(String deviceId, int lockDurationInHours) {
        Instant cutoff = Instant.now().minus(lockDurationInHours, ChronoUnit.HOURS);
        return !pointageRepository.existsByDeviceIdAndTimestampAfter(deviceId, cutoff);
    }

    public Pointage enregistrerPointage(String codeSecret, String deviceID) {
        try {
            Employe employe = getEmployeBycodeSecret(codeSecret);
            if (employe == null) {
                throw new IllegalArgumentException("Employé introuvable pour le code: " + codeSecret);
            }

            LocalDate today = LocalDate.now();
            DateTimeFormatter frenchDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String formattedDate = today.format(frenchDateFormatter);

            LocalTime now = LocalTime.now();
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH);
            String currentTime = now.format(timeFormatter);

            Pointage pointageExist = getBycodeSecretAndDate(codeSecret);
            if (pointageExist == null) {
                Pointage pointage = new Pointage();
                pointage.setCodeSecret(employe.getCodeSecret());
                pointage.setPrenom(employe.getPrenom());
                pointage.setNom(employe.getNom());
                pointage.setDate(formattedDate);
                pointage.setHeureArrive(currentTime);
                pointage.setStatus("En cours...");
                pointage.setSite(employe.getSite());
                pointage.setDeviceId(deviceID);
                pointage.setTimestamp(Instant.now());
                pointageRepository.save(pointage);
                return pointage;
            } else {
                String heureEntree = getHeureArriveByCurrentDateAndcodeSecret(codeSecret);
                if (heureEntree == null) {
                    throw new IllegalStateException("Heure d'arrivée introuvable pour le code: " + codeSecret);
                }

                DateTimeFormatter parseFormatter = DateTimeFormatter.ofPattern("HH:mm");
                LocalTime startTime = LocalTime.parse(heureEntree, parseFormatter);
                LocalTime endTime = LocalTime.parse(currentTime, parseFormatter);

                Duration duration = Duration.between(startTime, endTime);

                long hours = duration.toHours();
                long minutes = duration.toMinutes() % 60;

                String formattedDuration;
                if (hours > 0) {
                    formattedDuration = hours + "h" + (minutes > 0 ? minutes + "mn" : "");
                } else {
                    formattedDuration = minutes + "mn";
                }



                String status = "terminé";

                updatePointage(codeSecret, formattedDate, currentTime, formattedDuration, status);

                return getBycodeSecretAndDate(codeSecret); // récupération après update
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur lors de l'enregistrement du pointage: " + e.getMessage(), e);
        }
    }


}
