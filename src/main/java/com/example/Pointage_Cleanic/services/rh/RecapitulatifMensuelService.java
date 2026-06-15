package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.RecapitulatifMensuelDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.StatutValidationHS;
import com.example.Pointage_Cleanic.Enum.rh.TypeMajoration;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.HeureSupplementaire;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.rh.HeureSupplementaireRepository;
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

    // Source de vérité RH : périmètre = employés ACTIF + EN_PERIODE_ESSAI.
    private static final List<StatutDossierEmploye> STATUTS_ACTIFS =
            List.of(StatutDossierEmploye.ACTIF, StatutDossierEmploye.EN_PERIODE_ESSAI);

    private final DossierEmployeRepository dossierEmployeRepository;
    private final PointageRepository pointageRepository;
    private final DemandeCongeRepository demandeCongeRepository;
    private final HeureSupplementaireRepository heureSupplementaireRepository;

    private static String nomComplet(DossierEmploye e) {
        String prenom = e.getPrenom() == null ? "" : e.getPrenom().trim();
        String nom = e.getNom() == null ? "" : e.getNom().trim();
        return (prenom + " " + nom).trim();
    }

    public List<LigneRecapDto> getRecapitulatif(int mois, int annee, String departement) {
        YearMonth yearMonth = YearMonth.of(annee, mois);
        LocalDate debut = yearMonth.atDay(1);
        LocalDate fin = yearMonth.atEndOfMonth();

        List<DossierEmploye> employes = dossierEmployeRepository.findByStatutIn(STATUTS_ACTIFS);

        if (departement != null && !departement.isBlank()) {
            employes = employes.stream()
                    .filter(e -> departement.equalsIgnoreCase(e.getDepartement()))
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
                    .filter(d -> pointageRepository.existsByCodeSecretAndDate(e.getAgentId(), d))
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
                    .nomComplet(nomComplet(e))
                    .poste(e.getPoste())
                    .departement(e.getDepartement())
                    .joursOuvrables(joursOuvrables.size())
                    .joursPresents((int) presences)
                    .joursAbsents((int) absences)
                    .joursConge((int) conge)
                    .totalHeuresSup(totalHS)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Récapitulatif détaillé consommé par la façade /api/temps-presences/recapitulatif.
     * Étend {@link #getRecapitulatif} avec retards (depuis les pointages), nom/prénom
     * séparés, et la ventilation des heures supplémentaires par type de majoration.
     */
    public List<RecapitulatifMensuelDto> getRecapitulatifDetaille(
            int mois, int annee, String departement, String site, String q) {

        YearMonth yearMonth = YearMonth.of(annee, mois);
        LocalDate debut = yearMonth.atDay(1);
        LocalDate fin = yearMonth.atEndOfMonth();

        List<DossierEmploye> employes = dossierEmployeRepository.findByStatutIn(STATUTS_ACTIFS).stream()
                .filter(e -> departement == null || departement.isBlank()
                        || departement.equalsIgnoreCase(e.getDepartement()))
                .filter(e -> site == null || site.isBlank()
                        || (e.getSiteAffecte() != null
                            && e.getSiteAffecte().toLowerCase().contains(site.toLowerCase())))
                .filter(e -> matchesQ(q, e.getNom(), e.getPrenom(), e.getMatricule()))
                .collect(Collectors.toList());

        List<LocalDate> joursOuvrables = debut.datesUntil(fin.plusDays(1))
                .filter(d -> d.getDayOfWeek().getValue() < 6)
                .collect(Collectors.toList());
        Set<LocalDate> joursOuvrablesSet = new HashSet<>(joursOuvrables);

        // Pointages du mois groupés par codeSecret -> (jour -> liste)
        Map<String, Map<LocalDate, List<Pointage>>> pointagesParAgent = pointageRepository
                .findByDateBetween(debut, fin).stream()
                .filter(p -> p.getCodeSecret() != null && joursOuvrablesSet.contains(p.getDate()))
                .collect(Collectors.groupingBy(Pointage::getCodeSecret,
                        Collectors.groupingBy(Pointage::getDate)));

        List<HeureSupplementaire> hsDuMois = heureSupplementaireRepository
                .findByStatutAndDateBetween(StatutValidationHS.VALIDEE, debut, fin);

        List<DemandeConge> congesDuMois = demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                        StatutDemande.APPROUVE, fin, debut);

        return employes.stream().map(e -> {
            Map<LocalDate, List<Pointage>> pointagesEmploye =
                    pointagesParAgent.getOrDefault(e.getAgentId(), Map.of());

            int joursTravailles = 0;
            // Retard non dérivé : DossierEmploye ne porte pas d'heure de début et les
            // horaires sont hétérogènes (même décision que la vue pointage centralisé).
            // Le contrat conserve nombreRetards / minutesRetardTotal, figés à 0.
            int nombreRetards = 0;
            int minutesRetardTotal = 0;
            for (LocalDate jour : joursOuvrables) {
                List<Pointage> duJour = pointagesEmploye.get(jour);
                if (duJour == null || duJour.isEmpty()) continue;
                joursTravailles++;
            }

            int joursConge = congesDuMois.stream()
                    .filter(c -> c.getEmployeId().equals(e.getId()))
                    .mapToInt(c -> c.getNombreJours() != null ? c.getNombreJours() : 0)
                    .sum();

            int joursAbsence = joursOuvrables.size() - joursTravailles - joursConge;
            if (joursAbsence < 0) joursAbsence = 0;

            List<HeureSupplementaire> hsEmploye = hsDuMois.stream()
                    .filter(h -> h.getEmployeId().equals(e.getId()))
                    .collect(Collectors.toList());
            double heuresSupTotal = hsEmploye.stream()
                    .mapToDouble(h -> h.getNombreHeures() != null ? h.getNombreHeures() : 0).sum();
            double heuresSupMajorees = hsEmploye.stream()
                    .mapToDouble(h -> h.getHeuresMajoreesEquivalent() != null ? h.getHeuresMajoreesEquivalent() : 0).sum();

            RecapitulatifMensuelDto.HeuresSupParTypeDto parType =
                    RecapitulatifMensuelDto.HeuresSupParTypeDto.builder()
                            .t15(sommeHeuresParType(hsEmploye, TypeMajoration.T_15))
                            .t40(sommeHeuresParType(hsEmploye, TypeMajoration.T_40))
                            .t60(sommeHeuresParType(hsEmploye, TypeMajoration.T_60))
                            .t100(sommeHeuresParType(hsEmploye, TypeMajoration.T_100))
                            .build();

            return RecapitulatifMensuelDto.builder()
                    .employeId(e.getId())
                    .matricule(e.getMatricule())
                    .nom(e.getNom())
                    .prenom(e.getPrenom())
                    .departement(e.getDepartement())
                    .poste(e.getPoste())
                    .mois(mois)
                    .annee(annee)
                    .joursOuvrables(joursOuvrables.size())
                    .joursTravailles(joursTravailles)
                    .joursAbsence(joursAbsence)
                    .joursConge(joursConge)
                    .nombreRetards(nombreRetards)
                    .minutesRetardTotal(minutesRetardTotal)
                    .heuresSupTotal(heuresSupTotal)
                    .heuresSupMajoreesEquivalent(heuresSupMajorees)
                    .heuresSupParType(parType)
                    .build();
        }).collect(Collectors.toList());
    }

    private double sommeHeuresParType(List<HeureSupplementaire> hs, TypeMajoration type) {
        return hs.stream()
                .filter(h -> h.getTypeMajoration() == type)
                .mapToDouble(h -> h.getNombreHeures() != null ? h.getNombreHeures() : 0)
                .sum();
    }

    private boolean matchesQ(String q, String nom, String prenom, String matricule) {
        if (q == null || q.isBlank()) return true;
        String s = q.toLowerCase();
        return (nom != null && nom.toLowerCase().contains(s))
                || (prenom != null && prenom.toLowerCase().contains(s))
                || (matricule != null && matricule.toLowerCase().contains(s));
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