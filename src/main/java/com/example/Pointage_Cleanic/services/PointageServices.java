package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointageServices {

    private final MongoTemplate mongoTemplate;
    private final PointageRepository pointageRepository;
    private final GeocodingService geocodingService;

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


    public Pointage enregistrerPointage(
            String codeSecret,
            String deviceID,
            Double latitude,
            Double longitude
    ) {

        // 1️⃣ Vérification employé
        Employe employe = getEmployeBycodeSecret(codeSecret);
        if (employe == null) {
            throw new IllegalArgumentException(
                    "Employé introuvable pour le code : " + codeSecret
            );
        }

        // 2️⃣ Date & heure courantes
        LocalDate today = LocalDate.now();
        String formattedDate = today.format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy")
        );

        LocalTime now = LocalTime.now();
        String currentTime = now.format(
                DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
        );

        // 3️⃣ Reverse geocoding sécurisé
        String adresse = "Adresse non disponible";
        if (latitude != null && longitude != null) {
            try {
                log.info("🌍 Reverse geocoding demandé pour lat={}, lng={}", latitude, longitude);
                // Appel du GeocodingService réactif
                adresse = geocodingService.getReadableAddress(latitude, longitude)
                        .block(); // ⚠️ .block() transforme le Mono en String synchrone
                log.info("📍 Adresse retournée = [{}]", adresse);

            } catch (Exception e) {
                // ⚠️ On log seulement, le pointage ne doit jamais échouer
                log.warn("Reverse geocoding échoué pour {} / {}", latitude, longitude, e);
            }
        }


        // 4️⃣ Recherche pointage du jour
        Pointage pointageExist = getBycodeSecretAndDate(codeSecret);

        // =======================
        // 🟢 CAS ARRIVÉE
        // =======================
        if (pointageExist == null) {

            Pointage pointage = Pointage.builder()
                    .codeSecret(employe.getCodeSecret())
                    .prenom(employe.getPrenom())
                    .nom(employe.getNom())
                    .date(formattedDate)
                    .heureArrive(currentTime)
                    .status("En cours...")
                    .site(employe.getSite())
                    .deviceId(deviceID)
                    .timestamp(Instant.now())
                    .adresse(adresse)
                    .build();

            return pointageRepository.save(pointage);
        }

        // =======================
        // 🔵 CAS DÉPART
        // =======================

        String heureEntree = pointageExist.getHeureArrive();
        if (heureEntree == null) {
            throw new IllegalStateException(
                    "Heure d'arrivée introuvable pour le code : " + codeSecret
            );
        }

        LocalTime startTime = LocalTime.parse(
                heureEntree,
                DateTimeFormatter.ofPattern("HH:mm")
        );
        LocalTime endTime = LocalTime.parse(
                currentTime,
                DateTimeFormatter.ofPattern("HH:mm")
        );

        Duration duration = Duration.between(startTime, endTime);

        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;

        String formattedDuration =
                hours > 0
                        ? hours + "h" + (minutes > 0 ? minutes + "mn" : "")
                        : minutes + "mn";

        updatePointage(
                codeSecret,
                formattedDate,
                currentTime,
                formattedDuration,
                "terminé"
        );

        return getBycodeSecretAndDate(codeSecret);
    }


}
