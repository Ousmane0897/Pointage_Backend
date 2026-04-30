package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Enum.StatutDemande;
import com.example.Pointage_Cleanic.Enum.StatutValidationHS;
import com.example.Pointage_Cleanic.entities.DemandeConge;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.entities.HeureSupplementaire;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import com.example.Pointage_Cleanic.repositories.HeureSupplementaireRepository;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import lombok.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecapitulatifMensuelService {

    private final EmployeCompletRepository employeCompletRepository;
    private final PointageRepository pointageRepository;
    private final DemandeCongeRepository demandeCongeRepository;
    private final HeureSupplementaireRepository heureSupplementaireRepository;

    public List<LigneRecapDto> getRecapitulatif(int mois, int annee, String departement) {
        YearMonth yearMonth = YearMonth.of(annee, mois);
        LocalDate debut = yearMonth.atDay(1);
        LocalDate fin = yearMonth.atEndOfMonth();

        List<EmployeComplet> employes = employeCompletRepository.findByStatut(EmployeComplet.StatutEmploye.ACTIF);

        if (departement != null && !departement.isBlank()) {
            employes = employes.stream()
                    .filter(e -> e.getAgence() != null && e.getAgence().length > 0
                            && departement.equalsIgnoreCase(e.getAgence()[0]))
                    .collect(Collectors.toList());
        }

        // Pointages du mois
        List<LocalDate> joursOuvrables = debut.datesUntil(fin.plusDays(1))
                .filter(d -> d.getDayOfWeek().getValue() < 6)
                .collect(Collectors.toList());

        // HS validées du mois
        List<HeureSupplementaire> hsDuMois = heureSupplementaireRepository
                .findByStatutAndDateBetween(StatutValidationHS.VALIDEE, debut, fin);

        // Congés approuvés du mois
        List<DemandeConge> congesDuMois = demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                        StatutDemande.APPROUVE, fin, debut);

        return employes.stream().map(e -> {
            // Jours de présence = jours ouvrables avec un pointage
            long presences = joursOuvrables.stream()
                    .filter(d -> pointageRepository.findByCodeSecretAndDate(e.getAgentId(), d).isPresent())
                    .count();

            // Jours en congé
            long conge = congesDuMois.stream()
                    .filter(c -> c.getEmployeId().equals(e.getId()))
                    .mapToInt(c -> c.getNombreJours() != null ? c.getNombreJours() : 0)
                    .sum();

            // HS validées (somme des heures)
            double totalHS = hsDuMois.stream()
                    .filter(h -> h.getEmployeId().equals(e.getId()))
                    .mapToDouble(h -> h.getNombreHeures() != null ? h.getNombreHeures() : 0)
                    .sum();

            long absences = joursOuvrables.size() - presences - conge;
            if (absences < 0) absences = 0;

            return LigneRecapDto.builder()
                    .employeId(e.getId())
                    .matricule(e.getMatricule())
                    .nomComplet(e.getNomComplet())
                    .poste(e.getPoste())
                    .departement(e.getAgence() != null && e.getAgence().length > 0 ? e.getAgence()[0] : null)
                    .joursOuvrables(joursOuvrables.size())
                    .joursPresents((int) presences)
                    .joursAbsents((int) absences)
                    .joursConge((int) conge)
                    .totalHeuresSup(totalHS)
                    .build();
        }).collect(Collectors.toList());
    }

    public byte[] exportExcel(int mois, int annee, String departement) throws IOException {
        List<LigneRecapDto> lignes = getRecapitulatif(mois, annee, departement);
        String moisLabel = YearMonth.of(annee, mois).getMonth()
                .getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + annee;

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Récapitulatif " + moisLabel);

            Row header = sheet.createRow(0);
            String[] cols = {"Matricule", "Nom complet", "Poste", "Département",
                    "Jours ouvrables", "Présents", "Absents", "Congés", "Heures sup"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }

            int rowNum = 1;
            for (LigneRecapDto l : lignes) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(l.getMatricule() != null ? l.getMatricule() : "");
                row.createCell(1).setCellValue(l.getNomComplet() != null ? l.getNomComplet() : "");
                row.createCell(2).setCellValue(l.getPoste() != null ? l.getPoste() : "");
                row.createCell(3).setCellValue(l.getDepartement() != null ? l.getDepartement() : "");
                row.createCell(4).setCellValue(l.getJoursOuvrables());
                row.createCell(5).setCellValue(l.getJoursPresents());
                row.createCell(6).setCellValue(l.getJoursAbsents());
                row.createCell(7).setCellValue(l.getJoursConge());
                row.createCell(8).setCellValue(l.getTotalHeuresSup());
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportPdf(int mois, int annee, String departement) throws IOException {
        List<LigneRecapDto> lignes = getRecapitulatif(mois, annee, departement);
        String moisLabel = YearMonth.of(annee, mois).getMonth()
                .getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + annee;

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            com.lowagie.text.Document doc = new com.lowagie.text.Document();
            com.lowagie.text.pdf.PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(new com.lowagie.text.Paragraph("Récapitulatif mensuel — " + moisLabel));
            doc.add(com.lowagie.text.Chunk.NEWLINE);

            com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(9);
            table.setWidthPercentage(100);
            String[] cols = {"Matricule", "Nom complet", "Poste", "Département",
                    "Jours ouv.", "Présents", "Absents", "Congés", "H. sup"};
            for (String col : cols) {
                table.addCell(col);
            }
            for (LigneRecapDto l : lignes) {
                table.addCell(l.getMatricule() != null ? l.getMatricule() : "");
                table.addCell(l.getNomComplet() != null ? l.getNomComplet() : "");
                table.addCell(l.getPoste() != null ? l.getPoste() : "");
                table.addCell(l.getDepartement() != null ? l.getDepartement() : "");
                table.addCell(String.valueOf(l.getJoursOuvrables()));
                table.addCell(String.valueOf(l.getJoursPresents()));
                table.addCell(String.valueOf(l.getJoursAbsents()));
                table.addCell(String.valueOf(l.getJoursConge()));
                table.addCell(String.valueOf(l.getTotalHeuresSup()));
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        }
    }

    @Getter
    @Builder
    public static class LigneRecapDto {
        private String employeId;
        private String matricule;
        private String nomComplet;
        private String poste;
        private String departement;
        private int joursOuvrables;
        private int joursPresents;
        private int joursAbsents;
        private int joursConge;
        private double totalHeuresSup;
    }
}