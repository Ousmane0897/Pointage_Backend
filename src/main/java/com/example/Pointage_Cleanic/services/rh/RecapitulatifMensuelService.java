package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.PointageCentraliseDto;
import com.example.Pointage_Cleanic.Dto.rh.RecapitulatifMensuelDto;
import com.example.Pointage_Cleanic.Dto.rh.RecapitulatifMensuelDto.RecapSiteDto;
import com.example.Pointage_Cleanic.Enum.rh.TypeConge;
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
import java.time.Clock;
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
    /** Source des créneaux (employé × jour × site) : mêmes règles que la vue jour. */
    private final PointageCentraliseService pointageCentralise;
    /** Horloge métier (Africa/Dakar) : un jour à venir n'est jamais une absence. */
    private final Clock clock;

    /** Libellé de la ligne qui regroupe les pointages qu'aucun créneau n'explique. */
    static final String SITE_HORS_PLANNING = "Hors planning";

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
                .filter(e -> matchesSite(e, site))
                .filter(e -> matchesQ(q, e.getNom(), e.getPrenom(), e.getMatricule()))
                .collect(Collectors.toList());

        // ⚠ Une seule lecture du calendrier des fériés pour tous les employés (cf.
        // getRecapitulatif) : jamais une par employé.
        Set<LocalDate> feries = jourFerieService.datesFeriees(debut, fin);

        // Pointages du mois groupés par codeSecret -> (jour -> liste).
        // ⚠ Plus de filtrage sur les seuls jours ouvrables : un férié TRAVAILLÉ doit se
        // voir, et les jours ouvrables ne sont plus les mêmes d'un employé à l'autre.
        List<Pointage> pointagesDuMois = pointageRepository.findByDateBetween(debut, fin);
        Map<String, Map<LocalDate, List<Pointage>>> pointagesParAgent = pointagesDuMois.stream()
                .filter(p -> p.getCodeSecret() != null && p.getDate() != null)
                .collect(Collectors.groupingBy(Pointage::getCodeSecret,
                        Collectors.groupingBy(Pointage::getDate)));

        List<HeureSupplementaire> hsDuMois = heureSupplementaireRepository
                .findByStatutAndDateBetween(StatutValidationHS.VALIDEE, debut, fin);

        List<DemandeConge> congesDuMois = demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
                        StatutDemande.APPROUVE, fin, debut);

        // Créneaux du mois (employé × jour × site) : rattachement par plage horaire, retard
        // au-delà de la tolérance, congé prioritaire — exactement les règles de la vue
        // « Pointage centralisé », avec les données déjà chargées ci-dessus.
        Map<String, List<PointageCentraliseDto>> creneauxParEmploye = pointageCentralise
                .lignesPlage(debut, fin, employes, feries, pointagesDuMois, congesDuMois).stream()
                .collect(Collectors.groupingBy(PointageCentraliseDto::getEmployeId));

        // ⚠ Un jour dû mais encore à venir n'est pas une absence : sur le mois en cours,
        // l'ancien calcul comptait absents tous les jours ouvrables restants.
        LocalDate aujourdhui = LocalDate.now(clock);

        return employes.stream().map(e -> {
            Map<LocalDate, List<Pointage>> pointagesEmploye =
                    pointagesParAgent.getOrDefault(e.getAgentId(), Map.of());

            // Jours ouvrables PROPRES À CET EMPLOYÉ (rythme du site, jour de repos, fériés).
            List<LocalDate> joursOuvrables = calendrier.joursOuvrables(e, debut, fin, feries);
            Set<LocalDate> ouvrables = new HashSet<>(joursOuvrables);

            // Détail par site, puis cumuls de l'agent. Les retards ne sont plus figés à 0 :
            // les affectations portent leur horaire, chaque créneau est jugé sur le sien.
            List<PointageCentraliseDto> creneaux = creneauxParEmploye.getOrDefault(e.getId(), List.of());
            List<RecapSiteDto> tousLesSites = agregerParSite(creneaux);
            int nombreRetards = tousLesSites.stream().mapToInt(RecapSiteDto::getNombreRetards).sum();
            int minutesRetardTotal = tousLesSites.stream().mapToInt(RecapSiteDto::getMinutesRetardTotal).sum();
            int minutesTravaillees = tousLesSites.stream().mapToInt(RecapSiteDto::getMinutesTravaillees).sum();
            int creneauxSansDepart = tousLesSites.stream().mapToInt(RecapSiteDto::getCreneauxSansDepart).sum();
            int horsPlan = (int) creneaux.stream()
                    .filter(l -> PointageCentraliseService.HORS_PLAN.equals(l.getStatut())).count();

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

            // Congés ventilés par nature. ⚠ Une absence non justifiée « approuvée » reste une
            // ABSENCE : elle ne couvre pas le jour (elle était jusqu'ici comptée en congé).
            Set<LocalDate> reposMedical = joursDeConge(
                    filtrerType(congesDuMois, t -> t == TypeConge.REPOS_MEDICAL), e, debut, fin, ouvrables);
            Set<LocalDate> congesEmploye = joursDeConge(
                    filtrerType(congesDuMois, t -> t != TypeConge.REPOS_MEDICAL
                            && t != TypeConge.ABSENCE_NON_JUSTIFIEE), e, debut, fin, ouvrables);
            int joursConge = congesEmploye.size();

            // Cardinal de l'UNION : un jour à la fois pointé et en congé ne doit pas être
            // décompté deux fois. Positif par construction, aucun plancher nécessaire.
            List<LocalDate> joursDus = joursOuvrables.stream()
                    .filter(d -> !d.isAfter(aujourdhui))
                    .toList();
            long couverts = joursDus.stream()
                    .filter(d -> congesEmploye.contains(d) || reposMedical.contains(d)
                            || joursPointes.contains(d))
                    .count();
            int joursAbsence = (int) (joursDus.size() - couverts);

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
                    .joursReposMedical(reposMedical.size())
                    .minutesTravaillees(minutesTravaillees)
                    .creneauxSansDepart(creneauxSansDepart)
                    .horsPlan(horsPlan)
                    .agentId(e.getAgentId())
                    .dateCreationAgentId(e.getDateCreationAgentId())
                    .dateCreationAgentIdEstimee(Boolean.TRUE.equals(e.getDateCreationAgentIdEstimee()))
                    // Filtre « Site » : n'affiche que les lignes du site demandé, les cumuls
                    // de l'agent restant tous sites confondus.
                    .sites(tousLesSites.stream().filter(r -> siteCorrespond(r, site)).toList())
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

    private static List<DemandeConge> filtrerType(List<DemandeConge> conges,
                                                  java.util.function.Predicate<TypeConge> garder) {
        // Type nul (données antérieures) ⇒ congé ordinaire : même arbitrage prudent que
        // TypeConge.decompteSoldeAnnuel, on ne requalifie rien sur la foi d'une donnée absente.
        return conges.stream()
                .filter(c -> garder.test(c.getType() == null ? TypeConge.ANNUEL : c.getType()))
                .toList();
    }

    /**
     * Agrège les créneaux d'un agent par site. Les pointages hors planning forment une
     * ligne à part, en dernier ; les lignes {@code CONGE} n'appartiennent à aucun site (un
     * congé est un fait de la journée) et sont comptées au niveau de l'agent.
     */
    List<RecapSiteDto> agregerParSite(List<PointageCentraliseDto> creneaux) {
        Map<String, List<PointageCentraliseDto>> parSite = new LinkedHashMap<>();
        for (PointageCentraliseDto l : creneaux) {
            if (PointageCentraliseService.CONGE.equals(l.getStatut())) continue;
            String cle = PointageCentraliseService.HORS_PLAN.equals(l.getStatut())
                    ? SITE_HORS_PLANNING
                    : (l.getSite() == null || l.getSite().isBlank() ? "—" : l.getSite().trim());
            parSite.computeIfAbsent(cle, k -> new ArrayList<>()).add(l);
        }
        return parSite.entrySet().stream()
                .map(en -> agregerSite(en.getKey(), en.getValue()))
                .sorted(Comparator.comparing(RecapSiteDto::isHorsPlanning)
                        .thenComparing(RecapSiteDto::getSite, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private RecapSiteDto agregerSite(String site, List<PointageCentraliseDto> lignes) {
        Set<LocalDate> joursPointes = new HashSet<>();
        Set<LocalDate> joursAbsents = new HashSet<>();
        Set<String> horaires = new TreeSet<>();
        int prevus = 0, minutes = 0, sansDepart = 0, absents = 0, retards = 0, minutesRetard = 0;

        for (PointageCentraliseDto l : lignes) {
            if (l.isPlanifie()) {
                prevus++;
                if (l.getSiteHoraireDebut() != null && l.getSiteHoraireFin() != null) {
                    horaires.add(l.getSiteHoraireDebut() + "–" + l.getSiteHoraireFin());
                }
            }
            if (l.getHeureArrivee() != null) {
                joursPointes.add(l.getDate());
                // Durée inconnue (départ non pointé) : signalée, jamais inventée.
                if (l.getDureeMinutes() != null) minutes += l.getDureeMinutes();
                else sansDepart++;
            }
            if (PointageCentraliseService.ABSENT.equals(l.getStatut())) {
                absents++;
                joursAbsents.add(l.getDate());
            }
            if (PointageCentraliseService.RETARD.equals(l.getStatut())) {
                retards++;
                minutesRetard += l.getRetardMinutes() != null ? l.getRetardMinutes() : 0;
            }
        }

        return RecapSiteDto.builder()
                .site(site)
                .horsPlanning(SITE_HORS_PLANNING.equals(site))
                .horaires(horaires.isEmpty() ? null : String.join(", ", horaires))
                .creneauxPrevus(prevus)
                .joursTravailles(joursPointes.size())
                .minutesTravaillees(minutes)
                .creneauxSansDepart(sansDepart)
                .creneauxAbsents(absents)
                .joursAbsence(joursAbsents.size())
                .nombreRetards(retards)
                .minutesRetardTotal(minutesRetard)
                .build();
    }

    /** Filtre employé : site affecté (chaîne legacy) ou l'une de ses affectations. */
    private static boolean matchesSite(DossierEmploye e, String site) {
        if (site == null || site.isBlank()) return true;
        String cherche = site.toLowerCase();
        if (e.getSiteAffecte() != null && e.getSiteAffecte().toLowerCase().contains(cherche)) return true;
        return e.getAffectations() != null && e.getAffectations().stream()
                .anyMatch(a -> a != null && a.getSite() != null && a.getSite().toLowerCase().contains(cherche));
    }

    private static boolean siteCorrespond(RecapSiteDto ligne, String site) {
        if (site == null || site.isBlank()) return true;
        return !ligne.isHorsPlanning() && ligne.getSite().toLowerCase().contains(site.toLowerCase());
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