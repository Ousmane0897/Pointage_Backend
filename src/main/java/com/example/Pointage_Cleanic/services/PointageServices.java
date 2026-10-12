package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.Dto.pointage.PointageResponse;
import com.example.Pointage_Cleanic.Enum.pointage.AnomaliePointage;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.Dto.pointage.PointageLectureDto;
import com.example.Pointage_Cleanic.services.pointage.AgenceNomResolver;
import com.example.Pointage_Cleanic.services.pointage.PointageScanService;
import com.example.Pointage_Cleanic.services.pointage.PointageScanService.ScanAgence;
import com.example.Pointage_Cleanic.util.SiteAffecteUtils;
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
import org.springframework.data.mongodb.core.FindAndModifyOptions;
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
import java.time.LocalDateTime;
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
    // Contrôle du QR code d'agence : purement en lecture, appelé AVANT toute écriture.
    private final PointageScanService pointageScanService;
    // Noms d'agence résolus à la lecture (le pointage ne stocke que l'id).
    private final AgenceNomResolver agenceNomResolver;

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

    /** Valeur du filtre agence désignant les pointages sans agence (avant QR, ou sans QR). */
    public static final String AGENCE_AUCUNE = "AUCUNE";

    /**
     * Pointages enrichis des noms d'agence d'arrivée et de départ, résolus en une seule
     * requête pour toute la page.
     */
    public Page<PointageLectureDto> lecture(Page<Pointage> page) {
        List<String> ids = new ArrayList<>();
        for (Pointage p : page.getContent()) {
            ids.add(p.getAgenceId());
            ids.add(p.getAgenceIdSortie());
        }
        Map<String, String> noms = agenceNomResolver.noms(ids);
        return page.map(p -> PointageLectureDto.builder()
                .pointage(p)
                .agenceArriveeNom(p.getAgenceId() == null ? null : noms.get(p.getAgenceId()))
                .agenceDepartNom(p.getAgenceIdSortie() == null ? null : noms.get(p.getAgenceIdSortie()))
                .build());
    }

    public Page<Pointage> searchHistorique(
            String search,
            String dateDebut,
            String dateFin,
            int page,
            int size
    ) {
        return searchHistorique(search, dateDebut, dateFin, null, page, size);
    }

    /**
     * @param agenceId agence d'arrivée <b>ou</b> de départ ({@link #AGENCE_AUCUNE} : ni
     *                 l'une ni l'autre renseignée).
     */
    public Page<Pointage> searchHistorique(
            String search,
            String dateDebut,
            String dateFin,
            String agenceId,
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

        // Filtre agence : un pointage « appartient » à une agence par son arrivée OU son
        // départ — un agent peut arriver dans une agence et repartir d'une autre.
        if (agenceId != null && !agenceId.isBlank()) {
            Criteria agenceCriteria = AGENCE_AUCUNE.equals(agenceId)
                    ? new Criteria().andOperator(
                            Criteria.where("agenceId").is(null),
                            Criteria.where("agenceIdSortie").is(null))
                    : new Criteria().orOperator(
                            Criteria.where("agenceId").is(agenceId),
                            Criteria.where("agenceIdSortie").is(agenceId));
            criteria = new Criteria().andOperator(criteria, agenceCriteria);
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
         * multi-sites) en tableau de sites pour {@code Pointage.site[]}.
         * Délègue à {@link SiteAffecteUtils#decouper} (séparateurs tolérants
         * {@code /}, {@code ,} ou {@code " - "} — le tiret collé est préservé,
         * ex. « Sacré-Coeur »).
         * Ex. "Keur gorgui / yoff / bgfi tann" → ["Keur gorgui","yoff","bgfi tann"].
         */
        static String[] decouperSites(String siteAffecte) {
            return SiteAffecteUtils.decouper(siteAffecte).toArray(new String[0]);
        }

        /**
         * Signature historique (sans QR d'agence), conservée pour les appelants existants.
         * Suit les mêmes règles que {@link #pointer} : si le QR est rendu obligatoire, elle
         * est refusée comme un pointage sans QR.
         */
        public Pointage enregistrerPointage(
                String codeSecret,
                String deviceId,
                Double latitude,
                Double longitude
        ) {
            return pointer(codeSecret, deviceId, latitude, longitude, null, null).getPointage();
        }

        /**
         * Pointage kiosque : arrivée si aucun pointage n'est ouvert aujourd'hui, départ sinon.
         * <p>
         * Avec un QR d'agence, le scan est <b>entièrement validé avant toute écriture</b>
         * (QR, géofence de l'agence scannée, affectation) : une sortie refusée laisse le
         * pointage ouvert. Entrée et sortie sont jugées chacune contre l'agence de son
         * propre QR — un agent peut arriver dans une agence et repartir d'une autre.
         */
        public PointageResponse pointer(
                String codeSecret,
                String deviceId,
                Double latitude,
                Double longitude,
                Double precisionMetres,
                String qrPayload
        ) {

            // 1️⃣ Vérifier l’employé (référentiel dossier-employé, clé = agentId).
            // Code vide / inconnu → 404 NOT_FOUND JSON via GlobalExceptionHandler.
            DossierEmploye agent = dossierEmployeRepository.findByAgentId(codeSecret)
                    .orElseThrow(() -> new ResourceNotFoundException("Employé introuvable"));

            // 1️⃣bis QR d'agence — lève PointageRefuseException (422) sans rien écrire.
            ScanAgence scan = pointageScanService
                    .evaluer(agent, qrPayload, latitude, longitude, precisionMetres)
                    .orElse(null);

            LocalDate today = LocalDate.now();
            LocalTime now = LocalTime.now();
            String currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm"));

            // 2️⃣ DÉPART en priorité — clôture ATOMIQUE du pointage ouvert du jour.
            // Un agent peut enchaîner plusieurs pointages/jour (un par site). Le
            // findAndModify pose heureDepart de façon atomique : un seul appel
            // concurrent matche (ensuite heureDepart n'est plus null), ce qui
            // supprime la fenêtre TOCTOU du read-then-save précédent.
            Query openQuery = new Query(Criteria.where("codeSecret").is(codeSecret)
                    .and("date").is(today)
                    .and("heureDepart").is(null))
                    .with(Sort.by(Sort.Direction.DESC, "timestamp"));
            Update closeUpdate = new Update()
                    .set("heureDepart", currentTime)
                    .set("status", "TERMINÉ");
            List<String> anomaliesSortie = anomalies(scan, true);
            if (scan != null) {
                // Agence de SORTIE : celle du QR scanné maintenant, jamais celle d'entrée.
                closeUpdate
                        .set("agenceIdSortie", scan.agence().getId())
                        .set("latitudeSortie", scan.latitude())
                        .set("longitudeSortie", scan.longitude())
                        .set("precisionMetresSortie", scan.precisionMetres())
                        .set("distanceMetresSortie", scan.distanceMetres());
                if (!anomaliesSortie.isEmpty()) {
                    closeUpdate.push("anomalies").each(anomaliesSortie.toArray());
                }
            }
            Pointage ouvert = mongoTemplate.findAndModify(
                    openQuery, closeUpdate,
                    FindAndModifyOptions.options().returnNew(false),
                    Pointage.class);

            if (ouvert != null) {
                // La durée dépend de heureArrive (état pré-update renvoyé par
                // returnNew(false)) : second update ciblé par _id pour la renseigner.
                LocalTime startTime = LocalTime.parse(ouvert.getHeureArrive());
                Duration duration = Duration.between(startTime, now);
                long hours = duration.toHours();
                long minutes = duration.toMinutes() % 60;
                String formattedDuration =
                        hours > 0
                                ? hours + "h" + (minutes > 0 ? minutes + "mn" : "")
                                : minutes + "mn";

                mongoTemplate.updateFirst(
                        new Query(Criteria.where("_id").is(ouvert.getId())),
                        new Update().set("duree", formattedDuration),
                        Pointage.class);

                ouvert.setHeureDepart(currentTime);
                ouvert.setStatus("TERMINÉ");
                ouvert.setDuree(formattedDuration);
                if (scan != null) {
                    // returnNew(false) renvoie l'état pré-update : on reporte ici ce qui
                    // vient d'être écrit, pour que la réponse reflète la base.
                    ouvert.setAgenceIdSortie(scan.agence().getId());
                    ouvert.setLatitudeSortie(scan.latitude());
                    ouvert.setLongitudeSortie(scan.longitude());
                    ouvert.setPrecisionMetresSortie(scan.precisionMetres());
                    ouvert.setDistanceMetresSortie(scan.distanceMetres());
                    if (!anomaliesSortie.isEmpty()) {
                        List<String> toutes = new ArrayList<>(
                                ouvert.getAnomalies() == null ? List.of() : ouvert.getAnomalies());
                        toutes.addAll(anomaliesSortie);
                        ouvert.setAnomalies(toutes);
                    }
                }
                return reponse(ouvert, "SORTIE", today, now, scan);
            }

            // 3️⃣ ARRIVÉE (aucun pointage ouvert → nouveau pointage).
            // Adresse calculée ici seulement (évite un appel géocodage bloquant
            // à chaque clôture).
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

            List<String> anomaliesEntree = anomalies(scan, false);
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
                    .agenceId(scan == null ? null : scan.agence().getId())
                    .latitude(scan == null ? null : scan.latitude())
                    .longitude(scan == null ? null : scan.longitude())
                    .precisionMetres(scan == null ? null : scan.precisionMetres())
                    .distanceMetres(scan == null ? null : scan.distanceMetres())
                    .anomalies(anomaliesEntree.isEmpty() ? null : anomaliesEntree)
                    .build();

            return reponse(pointageRepository.save(pointage), "ENTREE", today, now, scan);
        }

        /** Nom d'agence d'un payload QR (écran d'accueil public), vide si invalide. */
        public Optional<String> nomAgenceQr(String qrPayload) {
            return pointageScanService.nomAgence(qrPayload);
        }

        /** Anomalies du scan, suffixées par le moment désormais connu. */
        private static List<String> anomalies(ScanAgence scan, boolean sortie) {
            List<String> liste = new ArrayList<>();
            if (scan == null) {
                return liste;
            }
            if (scan.horsAffectation()) {
                liste.add(AnomaliePointage.horsAffectation(sortie).name());
            }
            if (scan.precisionFaible()) {
                liste.add(AnomaliePointage.precisionFaible(sortie).name());
            }
            return liste;
        }

        private static PointageResponse reponse(Pointage pointage, String type,
                                                LocalDate date, LocalTime heure, ScanAgence scan) {
            return PointageResponse.builder()
                    .pointage(pointage)
                    .type(type)
                    .horodatage(LocalDateTime.of(date, heure.withSecond(0).withNano(0)))
                    .agenceId(scan == null ? null : scan.agence().getId())
                    .agenceNom(scan == null ? null : scan.agence().getNom())
                    .distanceMetres(scan == null ? null : scan.distanceMetres())
                    .avertissement(scan == null ? null : scan.avertissement())
                    .build();
        }


}
