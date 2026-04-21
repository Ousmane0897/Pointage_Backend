package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.KpiRhDto;
import com.example.Pointage_Cleanic.Dto.RepartitionItemDto;
import com.example.Pointage_Cleanic.entities.BulletinPaie;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.entities.RhAbsence;
import com.example.Pointage_Cleanic.entities.Sanction;
import com.example.Pointage_Cleanic.entities.SessionFormation;
import com.example.Pointage_Cleanic.repositories.BulletinPaieRepository;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import com.example.Pointage_Cleanic.repositories.ParticipationFormationRepository;
import com.example.Pointage_Cleanic.repositories.RhAbsenceRepository;
import com.example.Pointage_Cleanic.repositories.SanctionRepository;
import com.example.Pointage_Cleanic.repositories.SessionFormationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agrège les KPIs du tableau de bord RH à partir des collections 6.1 à 6.4.
 *
 * Stratégie : agrégations parallèles via CompletableFuture plutôt qu'un
 * $lookup unique. Les collections sources n'ont pas de clé de jointure
 * commune évidente ; chaque KPI reste testable isolément et la réponse
 * finale est composée à partir des futures.
 *
 * Note : EmployeComplet n'a pas de champ "departement" direct. On utilise
 * `poste` comme proxy pour les répartitions et filtres partant des employés.
 * Les collections RH (Sanction, RhAbsence, DemandeConge, EvaluationPeriodique,
 * BulletinPaie) exposent un vrai `departement` et sont filtrées comme tel.
 */
@Service
@RequiredArgsConstructor
public class TableauBordRhService {

    private static final DateTimeFormatter MOIS_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final EmployeCompletRepository employeCompletRepository;
    private final RhAbsenceRepository rhAbsenceRepository;
    private final BulletinPaieRepository bulletinPaieRepository;
    private final SessionFormationRepository sessionFormationRepository;
    private final ParticipationFormationRepository participationFormationRepository;
    private final SanctionRepository sanctionRepository;

    public KpiRhDto calculer(LocalDate dateDebut, LocalDate dateFin,
                             String departement, String site) {
        LocalDate debut = dateDebut != null ? dateDebut : LocalDate.now().withDayOfYear(1);
        LocalDate fin = dateFin != null ? dateFin : LocalDate.now();

        CompletableFuture<KpiRhDto> personnel = CompletableFuture.supplyAsync(
                () -> calculerPersonnel(departement, site));
        CompletableFuture<KpiRhDto> tempsPresence = CompletableFuture.supplyAsync(
                () -> calculerTempsPresence(debut, fin, departement));
        CompletableFuture<KpiRhDto> paie = CompletableFuture.supplyAsync(
                () -> calculerPaie(debut, fin, departement));
        CompletableFuture<KpiRhDto> formation = CompletableFuture.supplyAsync(
                () -> calculerFormation(debut, fin));
        CompletableFuture<KpiRhDto> sanctions = CompletableFuture.supplyAsync(
                () -> calculerSanctions(debut, fin, departement));

        CompletableFuture.allOf(personnel, tempsPresence, paie, formation, sanctions).join();

        KpiRhDto resultat = KpiRhDto.builder().build();
        fusionner(resultat, personnel.join());
        fusionner(resultat, tempsPresence.join());
        fusionner(resultat, paie.join());
        fusionner(resultat, formation.join());
        fusionner(resultat, sanctions.join());

        if (resultat.getEffectifTotal() != null && resultat.getEffectifTotal() > 0
                && resultat.getMasseSalarialeMensuelle() != null) {
            resultat.setCoutMoyenParEmploye(
                    resultat.getMasseSalarialeMensuelle() / resultat.getEffectifTotal());
        }
        return resultat;
    }

    // ====================== 6.1 Personnel ======================

    private KpiRhDto calculerPersonnel(String departement, String site) {
        List<EmployeComplet> tous = employeCompletRepository.findAll();

        List<EmployeComplet> filtres = tous.stream()
                .filter(e -> departement == null || departement.equals(e.getPoste()))
                .filter(e -> site == null || matchSite(e, site))
                .collect(Collectors.toList());

        List<EmployeComplet> actifs = filtres.stream()
                .filter(e -> e.getStatut() == EmployeComplet.StatutEmploye.ACTIF)
                .collect(Collectors.toList());

        long nbActifs = actifs.size();
        long nbSorties = filtres.stream()
                .filter(e -> e.getStatut() == EmployeComplet.StatutEmploye.SORTIE)
                .count();
        double turnover = (nbActifs + nbSorties) == 0 ? 0.0
                : Math.round(((double) nbSorties / (nbActifs + nbSorties)) * 1000.0) / 10.0;

        return KpiRhDto.builder()
                .effectifTotal(nbActifs)
                .repartitionDepartement(grouper(actifs, EmployeComplet::getPoste))
                .repartitionSite(grouper(actifs, EmployeComplet::getCodeSite))
                .repartitionTypeContrat(grouper(actifs, EmployeComplet::getTypeContrat))
                .turnover(turnover)
                .build();
    }

    private boolean matchSite(EmployeComplet e, String site) {
        if (site.equals(e.getCodeSite()) || site.equals(e.getVilleSite())) return true;
        if (site.equals(e.getCodeSite2()) || site.equals(e.getVilleSite2())) return true;
        return false;
    }

    private <T> List<RepartitionItemDto> grouper(List<T> items, Function<T, String> keyFn) {
        Map<String, Long> counts = items.stream()
                .map(keyFn)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> RepartitionItemDto.builder().label(e.getKey()).value(e.getValue()).build())
                .collect(Collectors.toList());
    }

    // ====================== 6.2 Temps & Présences ======================

    private KpiRhDto calculerTempsPresence(LocalDate debut, LocalDate fin, String departement) {
        List<RhAbsence> absences = rhAbsenceRepository.findAll().stream()
                .filter(a -> a.getDateDebut() != null
                        && !a.getDateDebut().isAfter(fin)
                        && (a.getDateFin() == null || !a.getDateFin().isBefore(debut)))
                .filter(a -> departement == null || departement.equals(a.getDepartement()))
                .collect(Collectors.toList());

        long joursAbsence = absences.stream()
                .mapToLong(a -> a.getNombreJours() != null ? a.getNombreJours() : 0)
                .sum();

        long joursPeriode = ChronoUnit.DAYS.between(debut, fin) + 1;
        long effectifActif = employeCompletRepository.findAll().stream()
                .filter(e -> e.getStatut() == EmployeComplet.StatutEmploye.ACTIF)
                .count();
        long joursOuvresTheoriques = Math.max(1L, joursPeriode * effectifActif);

        double tauxAbsenteisme = Math.round(
                ((double) joursAbsence / joursOuvresTheoriques) * 10000.0) / 100.0;

        // retardsMoyensMinutes et soldeCongesMoyen : nécessitent des schémas
        // enrichis (minutes de retard sur Pointage, compteur de solde sur
        // DemandeConge). Valeurs neutres en attendant.
        return KpiRhDto.builder()
                .tauxAbsenteisme(tauxAbsenteisme)
                .retardsMoyensMinutes(0.0)
                .soldeCongesMoyen(0.0)
                .build();
    }

    // ====================== 6.3 Paie ======================

    private KpiRhDto calculerPaie(LocalDate debut, LocalDate fin, String departement) {
        YearMonth moisCourant = YearMonth.from(fin);
        List<BulletinPaie> bulletinsMois = bulletinPaieRepository
                .findByPeriodeMoisAndPeriodeAnnee(moisCourant.getMonthValue(), moisCourant.getYear()).stream()
                .filter(b -> departement == null || departement.equals(b.getDepartement()))
                .collect(Collectors.toList());

        long masseMensuelle = bulletinsMois.stream()
                .mapToLong(b -> b.getSalaireBrut() == null ? 0L : b.getSalaireBrut())
                .sum();

        long masseAnnuelle = bulletinPaieRepository.findByPeriodeAnnee(fin.getYear()).stream()
                .filter(b -> departement == null || departement.equals(b.getDepartement()))
                .mapToLong(b -> b.getSalaireBrut() == null ? 0L : b.getSalaireBrut())
                .sum();

        return KpiRhDto.builder()
                .masseSalarialeMensuelle(masseMensuelle)
                .masseSalarialeAnnuelle(masseAnnuelle)
                .build();
    }

    // ====================== 6.4 Formation ======================

    private KpiRhDto calculerFormation(LocalDate debut, LocalDate fin) {
        List<SessionFormation> sessionsTerminees = sessionFormationRepository.findAll().stream()
                .filter(s -> s.getStatut() != null && s.getStatut().name().equals("TERMINEE"))
                .filter(s -> s.getDateFin() != null
                        && !s.getDateFin().isBefore(debut)
                        && !s.getDateFin().isAfter(fin))
                .collect(Collectors.toList());

        long formationsRealisees = sessionsTerminees.size();

        long totalPresents = participationFormationRepository.countByPresent(true);
        long total = participationFormationRepository.count();
        double tauxParticipation = total == 0 ? 0.0
                : Math.round(((double) totalPresents / total) * 10000.0) / 100.0;

        return KpiRhDto.builder()
                .formationsRealisees(formationsRealisees)
                .tauxParticipationFormation(tauxParticipation)
                .build();
    }

    // ====================== 6.4 Sanctions ======================

    private KpiRhDto calculerSanctions(LocalDate debut, LocalDate fin, String departement) {
        List<Sanction> sanctions = (departement == null
                ? sanctionRepository.findByDateSanctionBetween(debut, fin)
                : sanctionRepository.findByDepartementAndDateSanctionBetween(departement, debut, fin));

        Map<String, Long> parType = sanctions.stream()
                .filter(s -> s.getType() != null)
                .collect(Collectors.groupingBy(s -> s.getType().name(), Collectors.counting()));

        Map<String, Long> parPeriode = sanctions.stream()
                .filter(s -> s.getDateSanction() != null)
                .collect(Collectors.groupingBy(s -> MOIS_FMT.format(s.getDateSanction()),
                        Collectors.counting()));

        return KpiRhDto.builder()
                .sanctionsParType(toRepartition(parType, Map.Entry.<String, Long>comparingByValue().reversed()))
                .sanctionsParPeriode(toRepartition(parPeriode, Map.Entry.comparingByKey()))
                .build();
    }

    private List<RepartitionItemDto> toRepartition(Map<String, Long> counts,
                                                   Comparator<Map.Entry<String, Long>> order) {
        return counts.entrySet().stream()
                .sorted(order)
                .map(e -> RepartitionItemDto.builder().label(e.getKey()).value(e.getValue()).build())
                .collect(Collectors.toList());
    }

    // ====================== Fusion ======================

    private void fusionner(KpiRhDto cible, KpiRhDto source) {
        if (source.getEffectifTotal() != null) cible.setEffectifTotal(source.getEffectifTotal());
        if (!source.getRepartitionDepartement().isEmpty())
            cible.setRepartitionDepartement(source.getRepartitionDepartement());
        if (!source.getRepartitionSite().isEmpty())
            cible.setRepartitionSite(source.getRepartitionSite());
        if (!source.getRepartitionTypeContrat().isEmpty())
            cible.setRepartitionTypeContrat(source.getRepartitionTypeContrat());
        if (source.getTurnover() != null) cible.setTurnover(source.getTurnover());
        if (source.getTauxAbsenteisme() != null) cible.setTauxAbsenteisme(source.getTauxAbsenteisme());
        if (source.getRetardsMoyensMinutes() != null)
            cible.setRetardsMoyensMinutes(source.getRetardsMoyensMinutes());
        if (source.getSoldeCongesMoyen() != null) cible.setSoldeCongesMoyen(source.getSoldeCongesMoyen());
        if (source.getMasseSalarialeMensuelle() != null)
            cible.setMasseSalarialeMensuelle(source.getMasseSalarialeMensuelle());
        if (source.getMasseSalarialeAnnuelle() != null)
            cible.setMasseSalarialeAnnuelle(source.getMasseSalarialeAnnuelle());
        if (source.getFormationsRealisees() != null)
            cible.setFormationsRealisees(source.getFormationsRealisees());
        if (source.getTauxParticipationFormation() != null)
            cible.setTauxParticipationFormation(source.getTauxParticipationFormation());
        if (!source.getSanctionsParType().isEmpty())
            cible.setSanctionsParType(source.getSanctionsParType());
        if (!source.getSanctionsParPeriode().isEmpty())
            cible.setSanctionsParPeriode(source.getSanctionsParPeriode());
    }
}