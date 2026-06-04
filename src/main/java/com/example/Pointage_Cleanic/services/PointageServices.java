package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
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
import java.util.*;
import java.util.regex.Pattern;

import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.aggregation.*;



@Slf4j
@Service
@RequiredArgsConstructor
public class PointageServices {

    private final MongoTemplate mongoTemplate;
    private final PointageRepository pointageRepository;
    private final GeocodingService geocodingService;
    // Référentiel autonome : le pointage résout l'agent par agentId (= codeSecret
    // 4 chiffres envoyé par le mobile) dans dossiers_employes. Les modèles legacy
    // Employe / EmployeComplet ne sont plus consultés par le flux de pointage.
    private final DossierEmployeRepository dossierEmployeRepository;

    Pointage save(Pointage pointage) {

        return mongoTemplate.save(pointage);
    }

    List<Pointage> getAll() {

        return mongoTemplate.findAll(Pointage.class);
    }

    public Pointage getPointageBycodeSecret(String codeSecret) {
        // Un agent peut avoir plusieurs pointages (multi-site) : renvoyer le plus récent.
        Query query = new Query();
        query.addCriteria(Criteria.where("codeSecret").is(codeSecret));
        query.with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(1);
        return mongoTemplate.findOne(query, Pointage.class);
    }

    public Page<Pointage> searchHistorique(
            String search,
            String dateDebut,
            String dateFin,
            int page,
            int size
    ) {
        Query query = new Query();

        LocalDate today = LocalDate.now();
        Criteria criteria = Criteria.where("date").ne(today); // exclure aujourd'hui

        // ✅ Filtre période si fourni
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        if (dateDebut != null && !dateDebut.isBlank() && dateFin != null && !dateFin.isBlank()) {
            try {
                LocalDate start = LocalDate.parse(dateDebut, formatter);
                LocalDate end = LocalDate.parse(dateFin, formatter);
                criteria = new Criteria().andOperator(
                        Criteria.where("date").ne(today),
                        Criteria.where("date").gte(start).lte(end)
                );
            } catch (Exception e) {
                System.out.println("Erreur parsing dates : " + e.getMessage());
            }
        }

        // ✅ Recherche texte partielle sur plusieurs champs (ignore la casse)
        if (search != null && !search.isBlank()) {
            String regexPattern = ".*" + Pattern.quote(search) + ".*";
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("nom").regex(regexPattern, "i"),
                    Criteria.where("prenom").regex(regexPattern, "i"),
                    Criteria.where("codeSecret").regex(regexPattern, "i"),
                    Criteria.where("site").regex(regexPattern, "i")
            );
            criteria = new Criteria().andOperator(criteria, searchCriteria);
        }

        query.addCriteria(criteria);

        // ✅ Pagination + tri par timestamp
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        query.with(pageable);

        // ✅ Récupération des données
        List<Pointage> data = mongoTemplate.find(query, Pointage.class);

        // ✅ Total pour pagination
        long total = mongoTemplate.count(query, Pointage.class);

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

    public boolean canPoint(String deviceId, int lockDurationInMinutes) {
        Instant cutoff = Instant.now().minus(lockDurationInMinutes, ChronoUnit.MINUTES);
        return !pointageRepository.existsByDeviceIdAndTimestampAfter(deviceId, cutoff);
    }



        /**
         * Découpe le champ {@code siteAffecte} du dossier-employé (chaîne
         * « / »-séparée) en tableau de sites pour {@code Pointage.site[]}.
         * Ex. "Keur gorgui / yoff / bgfi tann" → ["Keur gorgui","yoff","bgfi tann"].
         */
        static String[] decouperSites(String siteAffecte) {
            if (siteAffecte == null || siteAffecte.isBlank()) {
                return new String[0];
            }
            return Arrays.stream(siteAffecte.split("/"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }

        public Pointage enregistrerPointage(
                String codeSecret,
                String deviceId,
                Double latitude,
                Double longitude
        ) {

            // 1️⃣ Vérifier l’employé (référentiel dossier-employé, clé = agentId)
            DossierEmploye agent = dossierEmployeRepository.findByAgentId(codeSecret)
                    .orElseThrow(() -> new IllegalArgumentException("Employé introuvable"));

            LocalDate today = LocalDate.now();
            LocalTime now = LocalTime.now();
            String currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm"));

            // 2️⃣ Adresse (safe)
            String adresse = "Adresse non disponible";
            try {
                if (latitude != null && longitude != null) {
                    adresse = geocodingService
                            .getReadableAddress(latitude, longitude)
                            .block();
                }
            } catch (Exception e) {
                log.warn("Reverse geocoding échoué", e);
            }

            // 3️⃣ Chercher le pointage encore ouvert du jour (heureDepart == null).
            // Un agent peut enchaîner plusieurs pointages/jour (un par site) : tant
            // qu'un pointage est ouvert, le POST le clôture ; sinon il en crée un nouveau.
            Optional<Pointage> optional = pointageRepository
                    .findFirstByCodeSecretAndDateAndHeureDepartIsNullOrderByTimestampDesc(codeSecret, today);

            // =====================
            // 🟢 ARRIVÉE (aucun pointage ouvert → nouveau pointage)
            // =====================
            if (optional.isEmpty()) {

                Pointage pointage = Pointage.builder()
                        .codeSecret(agent.getAgentId())
                        .prenom(agent.getPrenom())
                        .nom(agent.getNom())
                        .site(decouperSites(agent.getSiteAffecte()))
                        .date(today)
                        .heureArrive(currentTime)
                        .status("EN COURS...")
                        .deviceId(deviceId)
                        .timestamp(Instant.now())
                        .adresse(adresse)
                        .build();

                return pointageRepository.save(pointage);
            }

            // =====================
            // 🔵 DÉPART (clôture du pointage ouvert)
            // =====================
            Pointage pointage = optional.get();

            LocalTime startTime = LocalTime.parse(pointage.getHeureArrive());
            Duration duration = Duration.between(startTime, now);

            long hours = duration.toHours();
            long minutes = duration.toMinutes() % 60;

            String formattedDuration =
                    hours > 0
                            ? hours + "h" + (minutes > 0 ? minutes + "mn" : "")
                            : minutes + "mn";

            pointage.setHeureDepart(currentTime);
            pointage.setDuree(formattedDuration);
            pointage.setStatus("TERMINÉ");

            return pointageRepository.save(pointage);
        }


}
