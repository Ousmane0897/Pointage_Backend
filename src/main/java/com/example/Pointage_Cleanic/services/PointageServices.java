package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.lowagie.text.Document;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;


import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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


    public Page<Pointage> searchHistorique(
            String search,
            String dateDebut,
            String dateFin,
            int page,
            int size
    ) {
        Query query = new Query();

        // 📅 Date format front-end : dd/MM/yyyy
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate today = LocalDate.now();

        // 1️⃣ Filtre date (exclure aujourd'hui)
        Criteria dateCriteria = Criteria.where("date").ne(today);

        // 2️⃣ Filtre période si fourni
        if (dateDebut != null && !dateDebut.isBlank() && dateFin != null && !dateFin.isBlank()) {
            try {
                LocalDate start = LocalDate.parse(dateDebut, formatter);
                LocalDate end = LocalDate.parse(dateFin, formatter);

                dateCriteria = new Criteria().andOperator(
                        Criteria.where("date").ne(today),
                        Criteria.where("date").gte(start).lte(end)
                );
            } catch (Exception e) {
                System.out.println("Erreur parsing dates : " + e.getMessage());
            }
        }
        query.addCriteria(dateCriteria);

        // 3️⃣ Recherche texte optimisée (MongoDB utilise l’index textuel)
        if (search != null && !search.isBlank()) {
            TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(search);
            query.addCriteria(textCriteria);
        }

        // 4️⃣ Pagination + tri par timestamp (utilise l’index composé)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        query.with(pageable);

        // 5️⃣ Récupération données + total
        long total = mongoTemplate.count(query, Pointage.class);
        List<Pointage> data = mongoTemplate.find(query, Pointage.class);

        return new PageImpl<>(data, pageable, total);
    }


    public byte[] exportExcel(String search, String dateDebut, String dateFin) throws Exception {

        // 1️⃣ Récupérer les données
        List<Pointage> data = searchHistorique(search, dateDebut, dateFin, 0, Integer.MAX_VALUE).getContent();

        // 2️⃣ Créer le workbook et la feuille
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Historique Pointages");

        // 3️⃣ Header
        Row header = sheet.createRow(0);
        String[] cols = {"Date", "Nom", "Prénom", "Arrivée", "Départ", "Durée", "Site", "Adresse"};
        for (int i = 0; i < cols.length; i++) {
            header.createCell(i).setCellValue(cols[i]);
        }

        // 4️⃣ Fonctions utilitaires
        DateTimeFormatter formatterExcel = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.util.function.Function<String, String> safe = s -> s == null ? "" : s;

        // Optionnel : méthode pour formater LocalDate
        java.util.function.Function<LocalDate, String> formatDate = d -> d == null ? "" : d.format(formatterExcel);

        // 5️⃣ Remplir la feuille
        int rowIdx = 1;
        for (Pointage p : data) {
            Row row = sheet.createRow(rowIdx++);

            row.createCell(0).setCellValue(formatDate.apply(p.getDate()));
            row.createCell(1).setCellValue(safe.apply(p.getNom()));
            row.createCell(2).setCellValue(safe.apply(p.getPrenom()));
            row.createCell(3).setCellValue(safe.apply(p.getHeureArrive()));
            row.createCell(4).setCellValue(safe.apply(p.getHeureDepart()));
            row.createCell(5).setCellValue(safe.apply(p.getDuree()));
            row.createCell(6).setCellValue(p.getSite() != null ? Arrays.toString(p.getSite()) : "");
            row.createCell(7).setCellValue(safe.apply(p.getAdresse()));
        }

        // 6️⃣ Ajuster automatiquement la largeur des colonnes
        for (int i = 0; i < cols.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // 7️⃣ Export en byte[]
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        return out.toByteArray();
    }

    public byte[] exportPdf(String search, String dateDebut, String dateFin) throws Exception {

        // 1️⃣ Récupérer les données
        List<Pointage> data = searchHistorique(search, dateDebut, dateFin, 0, Integer.MAX_VALUE).getContent();

        // 2️⃣ Créer le document PDF A4 paysage
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(doc, out);
        doc.open();

        // 3️⃣ Table à 8 colonnes
        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        String[] headers = {"Date", "Nom", "Prénom", "Arrivée", "Départ", "Durée", "Site", "Adresse"};
        for (String h : headers) {
            table.addCell(new PdfPCell(new Phrase(h)));
        }

        // 4️⃣ Fonctions utilitaires
        java.util.function.Function<String, String> safe = s -> s == null ? "" : s;
        DateTimeFormatter formatterPdf = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // 5️⃣ Remplir la table
        for (Pointage p : data) {

            // Date formatée
            String dateStr = p.getDate() != null ? p.getDate().format(formatterPdf) : "";

            table.addCell(new PdfPCell(new Phrase(dateStr)));
            table.addCell(new PdfPCell(new Phrase(safe.apply(p.getNom()))));
            table.addCell(new PdfPCell(new Phrase(safe.apply(p.getPrenom()))));
            table.addCell(new PdfPCell(new Phrase(safe.apply(p.getHeureArrive()))));
            table.addCell(new PdfPCell(new Phrase(safe.apply(p.getHeureDepart()))));
            table.addCell(new PdfPCell(new Phrase(safe.apply(p.getDuree()))));
            table.addCell(new PdfPCell(new Phrase(safe.apply(Arrays.toString(p.getSite())))));
            table.addCell(new PdfPCell(new Phrase(safe.apply(p.getAdresse()))));
        }

        // 6️⃣ Ajouter la table et fermer le document
        doc.add(table);
        doc.close();

        return out.toByteArray();
    }


    public Pointage getBycodeSecretAndDate(String codeSecret) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter frenchFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String date = today.format(frenchFormatter);
        Query query = new Query();
        query.addCriteria(Criteria.where("codeSecret").is(codeSecret).and("date").is(date));
        return mongoTemplate.findOne(query, Pointage.class);
    }

    public void updatePointage(String codeSecret, LocalDate date, String heureDepart, String duree, String status) {
        LocalDate today = LocalDate.now();


        Query query = new Query(Criteria.where("codeSecret").is(codeSecret).and("date").is(today));
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
                    .date(today)
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
                today,
                currentTime,
                formattedDuration,
                "terminé"
        );

        return getBycodeSecretAndDate(codeSecret);
    }


}
