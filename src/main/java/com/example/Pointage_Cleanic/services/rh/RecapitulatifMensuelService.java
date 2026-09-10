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
    private final CalendrierTravailService calendrier;
    private final JourFerieService jourFerieService;

    /**
     * Jours du mois réellement couverts par un congé approuvé, <b>intersectés avec les
     * jours ouvrables de l'employé</b>.
     *
     * <p>⚠ Remplace la somme des {@code nombreJours} des demandes chevauchant le mois :
     * une demande à cheval sur deux mois y était comptée <b>en entier dans chacun</b>, ce
     * qui pouvait ramener les absences à zéro et masquer une absence réelle. On raisonne
     * désormais jour par jour, sur la seule fenêtre demandée.
     */
    private Set<LocalDate> joursDeConge(List<DemandeConge> congesDuMois, DossierEmploye employe,
                                        LocalDate debut, LocalDate fin, Set<LocalDate> ouvrables) {
        Set<LocalDate> jours = new HashSet<>();
        for (DemandeConge c : congesDuMois) {
            if (!Objects.equals(c.getEmployeId(), employe.getId())) continue;
            LocalDate d = c.getDateDebut() == null || c.getDateDebut().isBefore(debut)
                    ? debut : c.getDateDebut();
            LocalDate f = c.getDateFin() == null || c.getDateFin().isAfter(fin)
                    ? fin : c.getDateFin();
            for (; !d.isAfter(f); d = d.plusDays(1)) {
                if (ouvrables.contains(d)) jours.add(d);
            }
        }
        return jours;
    }

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

        // ⚠ Une seule lecture du calendrier des fériés pour TOUS les employés : une par
        // employé serait N requêtes Mongo pour une valeur identique, et deux lignes du même
        // tableau pourraient reposer sur des calendriers différents si l'exécution
        // chevauchait une saisie RH.
        Set<LocalDate> feries = jourFerieService.datesFeriees(debut, fin);

        // HS validées du mois
        List<HeureSupplementaire> hsDuMois = heureSupplementaireRepository
                .findByStatutAndDateBetween(StatutValidationHS.VALIDEE, debut, fin);

        // Congés approuvés du mois
        List<DemandeConge> congesDuMois = demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                        StatutDemande.APPROUVE, fin, debut);

        return employes.stream().map(e -> {
            // Jours ouvrables PROPRES À CET EMPLOYÉ : rythme du site, jour de repos et
            // fériés compris. Le calcul lundi-vendredi en dur ignorait les samedis des
            // agents de terrain, dont les absences étaient donc systématiquement fausses.
            List<LocalDate> joursOuvrables = calendrier.joursOuvrables(e, debut, fin, feries);
            Set<LocalDate> ouvrables = new HashSet<>(joursOuvrables);

            // Jours pointés, matérialisés une fois : les interroger deux fois doublerait
            // le nombre de requêtes, déjà d'une par jour et par employé.
            Set<LocalDate> joursPointes = joursOuvrables.stream()
                    .filter(d -> pointageRepository.existsByCodeSecretAndDate(e.getAgentId(), d))
                    .collect(Collectors.toSet());
            long presences = joursPointes.size();

            Set<LocalDate> joursConge = joursDeConge(congesDuMois, e, debut, fin, ouvrables);

            // HS validées (somme des heures)
            double totalHS = hsDuMois.stream()
                    .filter(h -> h.getEmployeId().equals(e.getId()))
                    .mapToDouble(h -> h.getNombreHeures() != null ? h.getNombreHeures() : 0)
                    .sum();

            // Un jour à la fois pointé et en congé ne doit pas être décompté deux fois :
            // on soustrait le cardinal de l'UNION, jamais deux compteurs indépendants.
            // Le résultat est positif par construction, aucun plancher n'est nécessaire.
            long couverts = joursOuvrables.stream()
                    .filter(d -> joursConge.contains(d) || joursPointes.contains(d))
                    .count();
            long absences = joursOuvrables.size() - couverts;

            long conge = joursConge.size();

            return LigneRecapDto.builder()
                    .employeId(e.getId())
                    .matricule(e.getMatricule())
                    .nomComplet(nomComplet(e))
                    .poste(e.getPoste())
                    .departement(e.getDepartement())
                    .joursOuvrables(joursOuvrables.size())
                    .joursFeries(calendrier.feriesTravailles(e, debut, fin, feries).size())
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

        // ⚠ Une seule lecture du calendrier des fériés pour tous les employés (cf.
        // getRecapitulatif) : jamais une par employé.
        Set<LocalDate> feries = jourFerieService.datesFeriees(debut, fin);

        // Pointages du mois groupés par codeSecret -> (jour -> liste).
        // ⚠ Plus de filtrage sur les seuls jours ouvrables : un férié TRAVAILLÉ doit se
        // voir, et les jours ouvrables ne sont plus les mêmes d'un employé à l'autre.
        Map<String, Map<LocalDate, List<Pointage>>> pointagesParAgent = pointageRepository
                .findByDateBetween(debut, fin).stream()
                .filter(p -> p.getCodeSecret() != null && p.getDate() != null)
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

            // Jours ouvrables PROPRES À CET EMPLOYÉ (rythme du site, jour de repos, fériés).
            List<LocalDate> joursOuvrables = calendrier.joursOuvrables(e, debut, fin, feries);
            Set<LocalDate> ouvrables = new HashSet<>(joursOuvrables);

            // Retard non dérivé : DossierEmploye ne porte pas d'heure de début et les
            // horaires sont hétérogènes (même décision que la vue pointage centralisé).
            // Le contrat conserve nombreRetards / minutesRetardTotal, figés à 0.
            int nombreRetards = 0;
            int minutesRetardTotal = 0;

            // ⚠ DEUX compteurs distincts, à ne pas fusionner.
            // `joursTravailles` compte TOUS les jours pointés du mois, fériés compris : le
            // travail réellement effectué doit se voir. `travaillesOuvrables` ne sert qu'au
            // calcul des absences, qui ne se juge que sur les jours dus. Avec un compteur
            // unique, soit un férié chômé deviendrait une absence, soit un férié travaillé
            // ferait passer les absences en négatif.
            int joursTravailles = 0;
            Set<LocalDate> joursPointes = new HashSet<>();
            for (Map.Entry<LocalDate, List<Pointage>> entree : pointagesEmploye.entrySet()) {
                LocalDate jour = entree.getKey();
                if (jour.isBefore(debut) || jour.isAfter(fin)) continue;
                if (entree.getValue() == null || entree.getValue().isEmpty()) continue;
                joursTravailles++;
                joursPointes.add(jour);
            }

            List<LocalDate> feriesDeLEmploye = calendrier.feriesTravailles(e, debut, fin, feries);
            int joursFeries = feriesDeLEmploye.size();
            int joursTravaillesFeries = (int) feriesDeLEmploye.stream()
                    .filter(joursPointes::contains)
                    .count();

            Set<LocalDate> congesEmploye = joursDeConge(congesDuMois, e, debut, fin, ouvrables);
            int joursConge = congesEmploye.size();

            // Cardinal de l'UNION : un jour à la fois pointé et en congé ne doit pas être
            // décompté deux fois. Positif par construction, aucun plancher nécessaire.
            long couverts = joursOuvrables.stream()
                    .filter(d -> congesEmploye.contains(d) || joursPointes.contains(d))
                    .count();
            int joursAbsence = (int) (joursOuvrables.size() - couverts);

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
                    .joursFeries(joursFeries)
                    .joursTravaillesFeries(joursTravaillesFeries)
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
                    "Jours ouvrables", "Fériés", "Présents", "Absents", "Congés", "Heures sup"};
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
                row.createCell(5).setCellValue(l.getJoursFeries());
                row.createCell(6).setCellValue(l.getJoursPresents());
                row.createCell(7).setCellValue(l.getJoursAbsents());
                row.createCell(8).setCellValue(l.getJoursConge());
                row.createCell(9).setCellValue(l.getTotalHeuresSup());
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

            com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(10);
            table.setWidthPercentage(100);
            String[] cols = {"Matricule", "Nom complet", "Poste", "Département",
                    "Jours ouv.", "Fériés", "Présents", "Absents", "Congés", "H. sup"};
            for (String col : cols) {
                table.addCell(col);
            }
            for (LigneRecapDto l : lignes) {
                table.addCell(l.getMatricule() != null ? l.getMatricule() : "");
                table.addCell(l.getNomComplet() != null ? l.getNomComplet() : "");
                table.addCell(l.getPoste() != null ? l.getPoste() : "");
                table.addCell(l.getDepartement() != null ? l.getDepartement() : "");
                table.addCell(String.valueOf(l.getJoursOuvrables()));
                table.addCell(String.valueOf(l.getJoursFeries()));
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
        /** Fériés du mois tombant un jour que l'employé aurait travaillé. */
        private int joursFeries;
        private int joursPresents;
        private int joursAbsents;
        private int joursConge;
        private double totalHeuresSup;
    }
}