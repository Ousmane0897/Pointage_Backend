package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.DeclarationSocialeDto;
import com.example.Pointage_Cleanic.Enum.StatutBulletin;
import com.example.Pointage_Cleanic.Enum.StatutDeclaration;
import com.example.Pointage_Cleanic.Enum.TypeDeclaration;
import com.example.Pointage_Cleanic.Mapper.DeclarationSocialeMapper;
import com.example.Pointage_Cleanic.entities.BulletinPaie;
import com.example.Pointage_Cleanic.entities.DeclarationSociale;
import com.example.Pointage_Cleanic.entities.LigneBulletin;
import com.example.Pointage_Cleanic.entities.LigneDeclaration;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.BulletinPaieRepository;
import com.example.Pointage_Cleanic.repositories.DeclarationSocialeRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeclarationSocialeService {

    private final BulletinPaieRepository bulletinPaieRepository;
    private final DeclarationSocialeRepository declarationSocialeRepository;
    private final DeclarationSocialeMapper declarationSocialeMapper;
    private final MongoTemplate mongoTemplate;

    private static final List<StatutBulletin> STATUTS_ELIGIBLES =
            List.of(StatutBulletin.VALIDE, StatutBulletin.PAYE);

    public DeclarationSocialeDto genererIpresMensuelle(int mois, int annee) {
        return genererMensuelle(TypeDeclaration.IPRES_MENSUELLE, mois, annee, "IPRES");
    }

    public DeclarationSocialeDto genererCssMensuelle(int mois, int annee) {
        return genererMensuelle(TypeDeclaration.CSS_MENSUELLE, mois, annee, "CSS");
    }

    public DeclarationSocialeDto genererIpresAnnuelle(int annee) {
        return genererAnnuelle(TypeDeclaration.IPRES_ANNUELLE, annee, "IPRES");
    }

    public DeclarationSocialeDto genererCssAnnuelle(int annee) {
        return genererAnnuelle(TypeDeclaration.CSS_ANNUELLE, annee, "CSS");
    }

    private DeclarationSocialeDto genererMensuelle(TypeDeclaration type, int mois, int annee, String libellePrefix) {
        List<BulletinPaie> bulletins = bulletinPaieRepository
                .findByPeriodeMoisAndPeriodeAnneeAndStatutIn(mois, annee, STATUTS_ELIGIBLES);

        Map<String, Long> totaux = calculerTotauxAggregation(mois, annee);

        return construireEtSauvegarder(type, mois, annee, bulletins, totaux, libellePrefix);
    }

    private DeclarationSocialeDto genererAnnuelle(TypeDeclaration type, int annee, String libellePrefix) {
        List<BulletinPaie> bulletins = bulletinPaieRepository
                .findByPeriodeAnneeAndStatutIn(annee, STATUTS_ELIGIBLES);

        Map<String, Long> totaux = calculerTotauxAggregationAnnuelle(annee);

        return construireEtSauvegarder(type, null, annee, bulletins, totaux, libellePrefix);
    }

    /**
     * Agrégation MongoDB native sur bulletins_paie pour produire les totaux
     * d'une période donnée (mois + année, statut VALIDE/PAYE).
     */
    private Map<String, Long> calculerTotauxAggregation(int mois, int annee) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("periode.mois").is(mois)
                        .and("periode.annee").is(annee)
                        .and("statut").in(STATUTS_ELIGIBLES)),
                Aggregation.group()
                        .sum("salaireBrut").as("totalBrut")
                        .sum("totalCotisationsSalariales").as("totalCotisSal")
                        .sum("totalCotisationsPatronales").as("totalCotisPat")
                        .sum("impotRevenu").as("totalIr")
                        .sum("trimf").as("totalTrimf")
                        .count().as("effectif")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "bulletins_paie", Map.class);
        Map doc = results.getUniqueMappedResult();
        return doc != null ? mapToLongs(doc) : Map.of();
    }

    private Map<String, Long> calculerTotauxAggregationAnnuelle(int annee) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("periode.annee").is(annee)
                        .and("statut").in(STATUTS_ELIGIBLES)),
                Aggregation.group()
                        .sum("salaireBrut").as("totalBrut")
                        .sum("totalCotisationsSalariales").as("totalCotisSal")
                        .sum("totalCotisationsPatronales").as("totalCotisPat")
                        .sum("impotRevenu").as("totalIr")
                        .sum("trimf").as("totalTrimf")
                        .count().as("effectif")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "bulletins_paie", Map.class);
        Map doc = results.getUniqueMappedResult();
        return doc != null ? mapToLongs(doc) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> mapToLongs(Map doc) {
        return ((Map<String, Object>) doc).entrySet().stream()
                .filter(e -> e.getValue() instanceof Number)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> ((Number) e.getValue()).longValue()));
    }

    private DeclarationSocialeDto construireEtSauvegarder(TypeDeclaration type, Integer mois, int annee,
                                                          List<BulletinPaie> bulletins,
                                                          Map<String, Long> totauxAgg,
                                                          String libellePrefix) {
        List<LigneDeclaration> lignes = bulletins.stream().map(this::bulletinVersLigneDeclaration).toList();

        long totalIpresSalarie = lignes.stream().mapToLong(l -> nv(l.getCotisationIpresSalarie())).sum();
        long totalIpresEmployeur = lignes.stream().mapToLong(l -> nv(l.getCotisationIpresEmployeur())).sum();
        long totalCssSalarie = lignes.stream().mapToLong(l -> nv(l.getCotisationCssSalarie())).sum();
        long totalCssEmployeur = lignes.stream().mapToLong(l -> nv(l.getCotisationCssEmployeur())).sum();

        long totalPayable = type.name().startsWith("IPRES")
                ? totalIpresSalarie + totalIpresEmployeur
                : totalCssSalarie + totalCssEmployeur;

        String libelle = libellePrefix + " — "
                + (mois != null
                ? YearMonth.of(annee, mois).getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + annee
                : String.valueOf(annee));

        DeclarationSociale decl = DeclarationSociale.builder()
                .type(type).libelle(libelle).mois(mois).annee(annee)
                .lignes(new ArrayList<>(lignes))
                .totalBrut(totauxAgg.getOrDefault("totalBrut", 0L))
                .totalIpresSalarie(totalIpresSalarie)
                .totalIpresEmployeur(totalIpresEmployeur)
                .totalCssSalarie(totalCssSalarie)
                .totalCssEmployeur(totalCssEmployeur)
                .totalIr(totauxAgg.getOrDefault("totalIr", 0L))
                .totalTrimf(totauxAgg.getOrDefault("totalTrimf", 0L))
                .totalPayable(totalPayable)
                .effectif(lignes.size())
                .statut(StatutDeclaration.GENEREE)
                .dateGeneration(LocalDate.now())
                .build();

        return declarationSocialeMapper.toDto(declarationSocialeRepository.save(decl));
    }

    private LigneDeclaration bulletinVersLigneDeclaration(BulletinPaie b) {
        long ipresSal = sommerLignes(b, List.of(CalculPaieService.CODE_IPRES_GEN_SAL,
                CalculPaieService.CODE_IPRES_COMP_SAL), true);
        long ipresEmp = sommerLignes(b, List.of(CalculPaieService.CODE_IPRES_GEN_EMP,
                CalculPaieService.CODE_IPRES_COMP_EMP), false);
        long cssEmp = sommerLignes(b, List.of(CalculPaieService.CODE_CSS_PF_EMP,
                CalculPaieService.CODE_CSS_ATMP_EMP), false);

        long assietteIpres = b.getLignes().stream()
                .filter(l -> CalculPaieService.CODE_IPRES_GEN_SAL.equals(l.getCode()))
                .mapToLong(l -> l.getBase() != null ? l.getBase() : 0L).findFirst().orElse(0L);
        long assietteCss = b.getLignes().stream()
                .filter(l -> CalculPaieService.CODE_CSS_PF_EMP.equals(l.getCode()))
                .mapToLong(l -> l.getBase() != null ? l.getBase() : 0L).findFirst().orElse(0L);

        return LigneDeclaration.builder()
                .employeId(b.getEmployeId())
                .matricule(b.getMatricule())
                .nom(b.getNom())
                .prenom(b.getPrenom())
                .numeroIpres(b.getNumeroIpres())
                .numeroCss(b.getNumeroCss())
                .brutImposable(nv(b.getSalaireBrut()))
                .assietteIpres(assietteIpres)
                .cotisationIpresSalarie(ipresSal)
                .cotisationIpresEmployeur(ipresEmp)
                .assietteCss(assietteCss)
                .cotisationCssSalarie(0L)
                .cotisationCssEmployeur(cssEmp)
                .impotRevenu(nv(b.getImpotRevenu()))
                .trimf(nv(b.getTrimf()))
                .joursTravailles(b.getJoursTravailles())
                .build();
    }

    private long sommerLignes(BulletinPaie b, List<String> codes, boolean salariale) {
        if (b.getLignes() == null) return 0L;
        return b.getLignes().stream()
                .filter(l -> codes.contains(l.getCode()))
                .mapToLong(l -> salariale
                        ? nv(l.getMontantSalarial())
                        : nv(l.getMontantPatronal()))
                .sum();
    }

    private long nv(Long v) {
        return v != null ? v : 0L;
    }

    // ─── CRUD / workflow ─────────────────────────────────────────────────

    public DeclarationSocialeDto getById(String id) {
        return declarationSocialeMapper.toDto(declarationSocialeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Déclaration introuvable : " + id)));
    }

    public List<DeclarationSocialeDto> getAll(TypeDeclaration type, Integer mois, Integer annee,
                                              StatutDeclaration statut) {
        return declarationSocialeRepository.findAll().stream()
                .filter(d -> type == null || d.getType() == type)
                .filter(d -> mois == null || (d.getMois() != null && d.getMois().equals(mois)))
                .filter(d -> annee == null || d.getAnnee() == annee)
                .filter(d -> statut == null || d.getStatut() == statut)
                .map(declarationSocialeMapper::toDto)
                .collect(Collectors.toList());
    }

    public DeclarationSocialeDto transmettre(String id, String referenceExterne) {
        DeclarationSociale d = declarationSocialeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Déclaration introuvable : " + id));
        d.setStatut(StatutDeclaration.TRANSMISE);
        d.setDateTransmission(LocalDate.now());
        d.setReferenceExterne(referenceExterne);
        return declarationSocialeMapper.toDto(declarationSocialeRepository.save(d));
    }

    // ─── Exports ─────────────────────────────────────────────────────────

    public byte[] exportPdf(String id) {
        DeclarationSociale d = declarationSocialeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Déclaration introuvable : " + id));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph titre = new Paragraph("DÉCLARATION SOCIALE — " + d.getLibelle(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13));
            titre.setAlignment(Element.ALIGN_CENTER);
            doc.add(titre);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Type : " + d.getType(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
            doc.add(new Paragraph("Effectif : " + d.getEffectif(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
            doc.add(new Paragraph("Statut : " + d.getStatut(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
            doc.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            String[] headers = {"Matricule", "Nom complet", "N° IPRES", "Brut", "IPRES sal.", "IPRES emp.", "CSS emp."};
            for (String h : headers) {
                PdfPCell hc = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8)));
                hc.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                table.addCell(hc);
            }
            for (LigneDeclaration l : d.getLignes()) {
                table.addCell(phrase(l.getMatricule()));
                table.addCell(phrase(ns(l.getNom()) + " " + ns(l.getPrenom())));
                table.addCell(phrase(l.getNumeroIpres()));
                table.addCell(phrase(fmt(l.getBrutImposable())));
                table.addCell(phrase(fmt(l.getCotisationIpresSalarie())));
                table.addCell(phrase(fmt(l.getCotisationIpresEmployeur())));
                table.addCell(phrase(fmt(l.getCotisationCssEmployeur())));
            }
            doc.add(table);
            doc.add(Chunk.NEWLINE);

            PdfPTable totaux = new PdfPTable(2);
            totaux.setWidthPercentage(60);
            totaux.setHorizontalAlignment(Element.ALIGN_RIGHT);
            ajouterTotalCell(totaux, "Total brut", fmt(d.getTotalBrut()));
            ajouterTotalCell(totaux, "Total IPRES salarié", fmt(d.getTotalIpresSalarie()));
            ajouterTotalCell(totaux, "Total IPRES employeur", fmt(d.getTotalIpresEmployeur()));
            ajouterTotalCell(totaux, "Total CSS employeur", fmt(d.getTotalCssEmployeur()));
            ajouterTotalCell(totaux, "Total IR", fmt(d.getTotalIr()));
            ajouterTotalCell(totaux, "Total TRIMF", fmt(d.getTotalTrimf()));
            ajouterTotalCell(totaux, "TOTAL À VERSER", fmt(d.getTotalPayable()));
            doc.add(totaux);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF déclaration : " + e.getMessage(), e);
        }
    }

    public byte[] exportExcel(String id) {
        DeclarationSociale d = declarationSocialeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Déclaration introuvable : " + id));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(d.getType().name());

            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            String[] cols = {"Matricule", "Nom", "Prénom", "N° IPRES", "N° CSS", "Brut imposable",
                    "Assiette IPRES", "IPRES salarié", "IPRES employeur",
                    "Assiette CSS", "CSS salarié", "CSS employeur", "IR", "TRIMF"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }

            int rowNum = 1;
            for (LigneDeclaration l : d.getLignes()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(ns(l.getMatricule()));
                row.createCell(1).setCellValue(ns(l.getNom()));
                row.createCell(2).setCellValue(ns(l.getPrenom()));
                row.createCell(3).setCellValue(ns(l.getNumeroIpres()));
                row.createCell(4).setCellValue(ns(l.getNumeroCss()));
                row.createCell(5).setCellValue(nv(l.getBrutImposable()));
                row.createCell(6).setCellValue(nv(l.getAssietteIpres()));
                row.createCell(7).setCellValue(nv(l.getCotisationIpresSalarie()));
                row.createCell(8).setCellValue(nv(l.getCotisationIpresEmployeur()));
                row.createCell(9).setCellValue(nv(l.getAssietteCss()));
                row.createCell(10).setCellValue(nv(l.getCotisationCssSalarie()));
                row.createCell(11).setCellValue(nv(l.getCotisationCssEmployeur()));
                row.createCell(12).setCellValue(nv(l.getImpotRevenu()));
                row.createCell(13).setCellValue(nv(l.getTrimf()));
            }

            org.apache.poi.ss.usermodel.Row totalRow = sheet.createRow(rowNum + 1);
            totalRow.createCell(0).setCellValue("TOTAUX");
            totalRow.createCell(5).setCellValue(nv(d.getTotalBrut()));
            totalRow.createCell(7).setCellValue(nv(d.getTotalIpresSalarie()));
            totalRow.createCell(8).setCellValue(nv(d.getTotalIpresEmployeur()));
            totalRow.createCell(10).setCellValue(nv(d.getTotalCssSalarie()));
            totalRow.createCell(11).setCellValue(nv(d.getTotalCssEmployeur()));
            totalRow.createCell(12).setCellValue(nv(d.getTotalIr()));
            totalRow.createCell(13).setCellValue(nv(d.getTotalTrimf()));

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération Excel déclaration : " + e.getMessage(), e);
        }
    }

    private Phrase phrase(String s) {
        return new Phrase(ns(s), FontFactory.getFont(FontFactory.HELVETICA, 8));
    }

    private void ajouterTotalCell(PdfPTable table, String label, String value) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
        PdfPCell c2 = new PdfPCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c1);
        table.addCell(c2);
    }

    private String ns(String s) {
        return s != null ? s : "";
    }

    private String fmt(Long v) {
        return v != null ? String.format(Locale.FRENCH, "%,d", v) : "0";
    }
}