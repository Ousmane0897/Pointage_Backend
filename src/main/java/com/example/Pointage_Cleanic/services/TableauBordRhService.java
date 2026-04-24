package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.KpiRhDto;
import com.example.Pointage_Cleanic.Dto.PointageCentraliseDto;
import com.example.Pointage_Cleanic.Dto.RepartitionItemDto;
import com.example.Pointage_Cleanic.Enum.StatutDossierEmploye;
import com.example.Pointage_Cleanic.entities.BulletinPaie;
import com.example.Pointage_Cleanic.entities.DossierEmploye;
import com.example.Pointage_Cleanic.entities.Sanction;
import com.example.Pointage_Cleanic.entities.SessionFormation;
import com.example.Pointage_Cleanic.repositories.BulletinPaieRepository;
import com.example.Pointage_Cleanic.repositories.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.ParticipationFormationRepository;
import com.example.Pointage_Cleanic.repositories.SanctionRepository;
import com.example.Pointage_Cleanic.repositories.SessionFormationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agrège les KPIs du tableau de bord RH à partir des collections 6.1 à 6.4.
 *
 * Stratégie : agrégations parallèles via CompletableFuture plutôt qu'un
 * $lookup unique. Chaque KPI reste testable isolément.
 *
 * Département : côté EmployeComplet on utilise `agence[0]` (même convention
 * que PointageCentraliseService). Les collections RH (Sanction, BulletinPaie,
 * EvaluationPeriodique…) exposent un vrai `departement` et sont filtrées
 * tel quel. Site : codeSite / villeSite (2 postes possibles).
 *
 * Le calcul des présences / retards réutilise PointageCentraliseService
 * (source de vérité 6.2) — itération jour par jour sur la fenêtre. Adapté
 * aux dashboards mensuels (~30 jours) ; pour des périodes plus longues,
 * une pipeline Mongo dédiée serait plus efficace.
 */
@Service
@RequiredArgsConstructor
public class TableauBordRhService {

    private static final DateTimeFormatter MOIS_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int TAILLE_PAGE_POINTAGES = 10_000;

    // KPIs effectif / turnover / répartitions : DossierEmploye (indépendance RH).
    // Les KPIs de présence (retards, absentéisme) passent par
    // PointageCentraliseService qui reste sur EmployeComplet (exception).
    private final DossierEmployeRepository dossierEmployeRepository;
    private final BulletinPaieRepository bulletinPaieRepository;
    private final SessionFormationRepository sessionFormationRepository;
    private final ParticipationFormationRepository participationFormationRepository;
    private final SanctionRepository sanctionRepository;
    private final PointageCentraliseService pointageCentraliseService;

    public KpiRhDto calculer(LocalDate dateDebut, LocalDate dateFin,
                             String departement, String site) {
        LocalDate debut = dateDebut != null ? dateDebut : LocalDate.now().withDayOfYear(1);
        LocalDate fin = dateFin != null ? dateFin : LocalDate.now();

        CompletableFuture<KpiRhDto> personnel = CompletableFuture.supplyAsync(
                () -> calculerPersonnel(departement, site));
        CompletableFuture<KpiRhDto> tempsPresence = CompletableFuture.supplyAsync(
                () -> calculerTempsPresence(debut, fin, departement, site));
        CompletableFuture<KpiRhDto> paie = CompletableFuture.supplyAsync(
                () -> calculerPaie(fin, departement));
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
        List<DossierEmploye> tous = dossierEmployeRepository.findAll();

        List<DossierEmploye> filtres = tous.stream()
                .filter(e -> departement == null || departement.equalsIgnoreCase(e.getDepartement()))
                .filter(e -> site == null || site.equalsIgnoreCase(e.getSiteAffecte()))
                .collect(Collectors.toList());

        List<DossierEmploye> actifs = filtres.stream()
                .filter(e -> e.getStatut() == StatutDossierEmploye.ACTIF
                        || e.getStatut() == StatutDossierEmploye.EN_PERIODE_ESSAI)
                .collect(Collectors.toList());

        long nbActifs = actifs.size();
        long nbSorties = filtres.stream()
                .filter(e -> e.getStatut() == StatutDossierEmploye.SORTI)
                .count();
        double turnover = (nbActifs + nbSorties) == 0 ? 0.0
                : Math.round(((double) nbSorties / (nbActifs + nbSorties)) * 1000.0) / 10.0;

        return KpiRhDto.builder()
                .effectifTotal(nbActifs)
                .repartitionDepartement(grouper(actifs, DossierEmploye::getDepartement))
                .repartitionSite(grouper(actifs, DossierEmploye::getSiteAffecte))
                .repartitionTypeContrat(grouper(actifs, d -> d.getStatut() != null ? d.getStatut().name() : null))
                .turnover(turnover)
                .build();
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

    private KpiRhDto calculerTempsPresence(LocalDate debut, LocalDate fin,
                                           String departement, String site) {
        long totalRetardMinutes = 0;
        long nbRetards = 0;
        long totalAbsents = 0;
        long totalObservations = 0;

        for (LocalDate d = debut; !d.isAfter(fin); d = d.plusDays(1)) {
            Page<PointageCentraliseDto> page = pointageCentraliseService.getPointages(
                    d, departement, site, null, null, 0, TAILLE_PAGE_POINTAGES);
            for (PointageCentraliseDto p : page.getContent()) {
                totalObservations++;
                String statut = p.getStatut();
                if ("ABSENT".equals(statut)) {
                    totalAbsents++;
                } else if ("RETARD".equals(statut)
                        && p.getRetardMinutes() != null && p.getRetardMinutes() > 0) {
                    totalRetardMinutes += p.getRetardMinutes();
                    nbRetards++;
                }
            }
        }

        double tauxAbsenteisme = totalObservations == 0 ? 0.0
                : Math.round(((double) totalAbsents / totalObservations) * 10000.0) / 100.0;
        double retardsMoyens = nbRetards == 0 ? 0.0
                : Math.round(((double) totalRetardMinutes / nbRetards) * 100.0) / 100.0;

        return KpiRhDto.builder()
                .tauxAbsenteisme(tauxAbsenteisme)
                .retardsMoyensMinutes(retardsMoyens)
                .build();
    }

    // ====================== 6.3 Paie ======================

    private KpiRhDto calculerPaie(LocalDate fin, String departement) {
        YearMonth moisCourant = YearMonth.from(fin);
        List<BulletinPaie> bulletinsMois = bulletinPaieRepository
                .findByPeriodeMoisAndPeriodeAnnee(moisCourant.getMonthValue(), moisCourant.getYear()).stream()
                .filter(b -> departement == null || departement.equals(b.getDepartement()))
                .collect(Collectors.toList());

        long masseMensuelle = bulletinsMois.stream()
                .mapToLong(b -> b.getSalaireBrut() == null ? 0L : b.getSalaireBrut())
                .sum();

        List<BulletinPaie> bulletinsAnnee = bulletinPaieRepository.findByPeriodeAnnee(fin.getYear()).stream()
                .filter(b -> departement == null || departement.equals(b.getDepartement()))
                .collect(Collectors.toList());

        long masseAnnuelle = bulletinsAnnee.stream()
                .mapToLong(b -> b.getSalaireBrut() == null ? 0L : b.getSalaireBrut())
                .sum();

        // Solde congés moyen : dernier bulletin par employé sur l'année en cours
        Map<String, BulletinPaie> derniers = new HashMap<>();
        for (BulletinPaie b : bulletinsAnnee) {
            derniers.merge(b.getEmployeId(), b, (prev, curr) -> comparePeriode(curr, prev) > 0 ? curr : prev);
        }
        double soldeMoyen = derniers.values().stream()
                .filter(b -> b.getSoldeConges() != null)
                .mapToInt(BulletinPaie::getSoldeConges)
                .average()
                .orElse(0.0);
        soldeMoyen = Math.round(soldeMoyen * 100.0) / 100.0;

        return KpiRhDto.builder()
                .masseSalarialeMensuelle(masseMensuelle)
                .masseSalarialeAnnuelle(masseAnnuelle)
                .soldeCongesMoyen(soldeMoyen)
                .build();
    }

    private int comparePeriode(BulletinPaie a, BulletinPaie b) {
        if (a.getPeriode() == null && b.getPeriode() == null) return 0;
        if (a.getPeriode() == null) return -1;
        if (b.getPeriode() == null) return 1;
        int cmp = Integer.compare(a.getPeriode().getAnnee(), b.getPeriode().getAnnee());
        if (cmp != 0) return cmp;
        return Integer.compare(a.getPeriode().getMois(), b.getPeriode().getMois());
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