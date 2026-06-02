package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.ComparaisonPeriodesTerrain;
import com.example.Pointage_Cleanic.Dto.terrain.IncidentsParSite;
import com.example.Pointage_Cleanic.Dto.terrain.InterventionsParSite;
import com.example.Pointage_Cleanic.Dto.terrain.KpiTerrain;
import com.example.Pointage_Cleanic.Dto.terrain.PointEvolution;
import com.example.Pointage_Cleanic.Dto.terrain.RapportTableauBordTerrain;
import com.example.Pointage_Cleanic.Dto.terrain.SatisfactionParSite;
import com.example.Pointage_Cleanic.Enum.terrain.DecisionControleTerrain;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAlerte;
import com.example.Pointage_Cleanic.Enum.terrain.StatutIntervention;
import com.example.Pointage_Cleanic.Enum.terrain.TypeAlerteTerrain;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import com.example.Pointage_Cleanic.entities.terrain.AlerteTerrain;
import com.example.Pointage_Cleanic.entities.terrain.ControleQualiteTerrain;
import com.example.Pointage_Cleanic.entities.terrain.FicheIntervention;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TableauBordTerrainService {

    private static final Set<StatutIntervention> REALISEES =
            Set.of(StatutIntervention.TERMINEE, StatutIntervention.VALIDEE);
    private static final Set<TypeAlerteTerrain> INCIDENTS = Set.of(
            TypeAlerteTerrain.ABSENCE, TypeAlerteTerrain.DEPART_PREMATURE, TypeAlerteTerrain.POINTAGE_HORS_ZONE);

    private final MongoTemplate mongoTemplate;

    // ───────────────────────── Endpoints ─────────────────────────

    public RapportTableauBordTerrain rapport(LocalDate dateDebut, LocalDate dateFin, String siteId,
                                             String employeId, String typeIntervention) {
        return RapportTableauBordTerrain.builder()
                .kpis(kpis(dateDebut, dateFin, siteId, employeId, typeIntervention))
                .interventionsParSite(interventionsParSite(dateDebut, dateFin, siteId, employeId))
                .evolutionCouverture(evolutionCouverture(dateDebut, dateFin, siteId, employeId))
                .incidentsParSite(incidentsParSite(dateDebut, dateFin, siteId, employeId))
                .evolutionSatisfaction(evolutionSatisfaction(dateDebut, dateFin, siteId))
                .comparaison(comparaisonPeriodes(dateDebut, dateFin, siteId, employeId, typeIntervention))
                .build();
    }

    public KpiTerrain kpis(LocalDate dateDebut, LocalDate dateFin, String siteId,
                           String employeId, String typeIntervention) {
        List<AffectationAgent> affectations = affectations(dateDebut, dateFin, siteId, employeId);
        List<FicheIntervention> interventions = interventions(dateDebut, dateFin, siteId, employeId);
        List<ControleQualiteTerrain> controles = controles(dateDebut, dateFin, siteId);
        List<AlerteTerrain> alertes = alertes(dateDebut, dateFin, siteId, employeId);

        long planifiees = affectations.size();
        long realisees = interventions.stream().filter(i -> REALISEES.contains(i.getStatut())).count();
        long nbControles = controles.size();
        long conformes = controles.stream()
                .filter(c -> c.getDecision() == DecisionControleTerrain.CONFORME).count();
        double satisfaction = controles.stream()
                .filter(c -> c.getNoteGlobale() != null)
                .mapToDouble(ControleQualiteTerrain::getNoteGlobale).average().orElse(0d);

        return KpiTerrain.builder()
                .dateDebut(dateDebut.toString())
                .dateFin(dateFin.toString())
                .nbAffectationsPlanifiees(planifiees)
                .nbInterventionsRealisees(realisees)
                .tauxCouverture(ratio(realisees, planifiees))
                .nbAgentsActifs(interventions.stream().map(FicheIntervention::getEmployeId)
                        .filter(java.util.Objects::nonNull).distinct().count())
                .nbSitesActifs(interventions.stream().map(FicheIntervention::getSiteId)
                        .filter(java.util.Objects::nonNull).distinct().count())
                .satisfactionMoyenne(satisfaction)
                .nbControles(nbControles)
                .nbControlesConformes(conformes)
                .nbIncidents(alertes.stream().filter(a -> INCIDENTS.contains(a.getType())).count())
                .nbAlertesEscaladees(alertes.stream()
                        .filter(a -> a.getStatut() == StatutAlerte.ESCALADEE).count())
                .build();
    }

    public List<InterventionsParSite> interventionsParSite(LocalDate dateDebut, LocalDate dateFin,
                                                           String siteId, String employeId) {
        List<FicheIntervention> interventions = interventions(dateDebut, dateFin, siteId, employeId);
        List<AffectationAgent> affectations = affectations(dateDebut, dateFin, siteId, employeId);

        Map<String, Long> prevuesParSite = affectations.stream()
                .filter(a -> a.getSiteId() != null)
                .collect(Collectors.groupingBy(AffectationAgent::getSiteId, Collectors.counting()));

        Map<String, List<FicheIntervention>> parSite = interventions.stream()
                .filter(i -> i.getSiteId() != null)
                .collect(Collectors.groupingBy(FicheIntervention::getSiteId));

        List<InterventionsParSite> result = new ArrayList<>();
        for (Map.Entry<String, List<FicheIntervention>> e : parSite.entrySet()) {
            FicheIntervention ref = e.getValue().get(0);
            long nb = e.getValue().size();
            long prevues = prevuesParSite.getOrDefault(e.getKey(), 0L);
            result.add(InterventionsParSite.builder()
                    .siteId(e.getKey())
                    .siteCode(ref.getSiteCode())
                    .siteNom(ref.getSiteNom())
                    .nbInterventions(nb)
                    .nbPrevues(prevues)
                    .tauxCouverture(ratio(nb, prevues))
                    .build());
        }
        return result;
    }

    public List<PointEvolution> evolutionCouverture(LocalDate dateDebut, LocalDate dateFin,
                                                    String siteId, String employeId) {
        List<FicheIntervention> interventions = interventions(dateDebut, dateFin, siteId, employeId);
        List<AffectationAgent> affectations = affectations(dateDebut, dateFin, siteId, employeId);
        boolean parMois = parMois(dateDebut, dateFin);

        Map<String, Long> realiseesBucket = new TreeMap<>();
        for (FicheIntervention i : interventions) {
            if (i.getDateDebut() == null || !REALISEES.contains(i.getStatut())) continue;
            realiseesBucket.merge(bucket(i.getDateDebut(), parMois), 1L, Long::sum);
        }
        Map<String, Long> planifieesBucket = new TreeMap<>();
        for (AffectationAgent a : affectations) {
            if (a.getDateDebut() == null) continue;
            planifieesBucket.merge(bucket(a.getDateDebut(), parMois), 1L, Long::sum);
        }

        Set<String> buckets = new java.util.TreeSet<>();
        buckets.addAll(realiseesBucket.keySet());
        buckets.addAll(planifieesBucket.keySet());

        List<PointEvolution> points = new ArrayList<>();
        for (String b : buckets) {
            points.add(PointEvolution.builder()
                    .date(b)
                    .valeur(ratio(realiseesBucket.getOrDefault(b, 0L), planifieesBucket.getOrDefault(b, 0L)))
                    .build());
        }
        return points;
    }

    public List<IncidentsParSite> incidentsParSite(LocalDate dateDebut, LocalDate dateFin,
                                                   String siteId, String employeId) {
        List<AlerteTerrain> alertes = alertes(dateDebut, dateFin, siteId, employeId);

        Map<String, List<AlerteTerrain>> parSite = alertes.stream()
                .filter(a -> a.getSiteId() != null)
                .collect(Collectors.groupingBy(AlerteTerrain::getSiteId));

        List<IncidentsParSite> result = new ArrayList<>();
        for (Map.Entry<String, List<AlerteTerrain>> e : parSite.entrySet()) {
            Map<String, Integer> parType = new LinkedHashMap<>();
            long nbIncidents = 0;
            for (AlerteTerrain a : e.getValue()) {
                parType.merge(a.getType().name(), 1, Integer::sum);
                if (INCIDENTS.contains(a.getType())) nbIncidents++;
            }
            result.add(IncidentsParSite.builder()
                    .siteId(e.getKey())
                    .siteNom(e.getValue().get(0).getSiteNom())
                    .nbIncidents(nbIncidents)
                    .parType(parType)
                    .build());
        }
        return result;
    }

    public List<PointEvolution> evolutionSatisfaction(LocalDate dateDebut, LocalDate dateFin, String siteId) {
        List<ControleQualiteTerrain> controles = controles(dateDebut, dateFin, siteId);
        boolean parMois = parMois(dateDebut, dateFin);

        Map<String, List<Double>> notesBucket = new TreeMap<>();
        for (ControleQualiteTerrain c : controles) {
            if (c.getDateControle() == null || c.getNoteGlobale() == null) continue;
            notesBucket.computeIfAbsent(bucket(c.getDateControle(), parMois), k -> new ArrayList<>())
                    .add(c.getNoteGlobale());
        }
        List<PointEvolution> points = new ArrayList<>();
        for (Map.Entry<String, List<Double>> e : notesBucket.entrySet()) {
            double moyenne = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0d);
            points.add(PointEvolution.builder().date(e.getKey()).valeur(moyenne).build());
        }
        return points;
    }

    public List<SatisfactionParSite> satisfactionParSite(LocalDate dateDebut, LocalDate dateFin, String siteId) {
        List<ControleQualiteTerrain> controles = controles(dateDebut, dateFin, siteId);

        Map<String, List<ControleQualiteTerrain>> parSite = controles.stream()
                .filter(c -> c.getSiteId() != null)
                .collect(Collectors.groupingBy(ControleQualiteTerrain::getSiteId));

        List<SatisfactionParSite> result = new ArrayList<>();
        for (Map.Entry<String, List<ControleQualiteTerrain>> e : parSite.entrySet()) {
            List<ControleQualiteTerrain> liste = e.getValue();
            double moyenne = liste.stream().filter(c -> c.getNoteGlobale() != null)
                    .mapToDouble(ControleQualiteTerrain::getNoteGlobale).average().orElse(0d);
            DecisionControleTerrain majoritaire = liste.stream()
                    .filter(c -> c.getDecision() != null)
                    .collect(Collectors.groupingBy(ControleQualiteTerrain::getDecision, Collectors.counting()))
                    .entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            result.add(SatisfactionParSite.builder()
                    .siteId(e.getKey())
                    .siteNom(liste.get(0).getSiteNom())
                    .noteMoyenne(moyenne)
                    .nbControles(liste.size())
                    .decisionMajoritaire(majoritaire)
                    .build());
        }
        return result;
    }

    public ComparaisonPeriodesTerrain comparaisonPeriodes(LocalDate dateDebut, LocalDate dateFin, String siteId,
                                                          String employeId, String typeIntervention) {
        KpiTerrain courante = kpis(dateDebut, dateFin, siteId, employeId, typeIntervention);

        long jours = ChronoUnit.DAYS.between(dateDebut, dateFin) + 1;
        LocalDate precFin = dateDebut.minusDays(1);
        LocalDate precDebut = precFin.minusDays(jours - 1);
        KpiTerrain precedente = kpis(precDebut, precFin, siteId, employeId, typeIntervention);

        return ComparaisonPeriodesTerrain.builder()
                .periodeCourante(courante)
                .periodePrecedente(precedente)
                .deltaCouverturePoints((courante.getTauxCouverture() - precedente.getTauxCouverture()) * 100d)
                .deltaInterventionsPourcent(deltaPourcent(
                        courante.getNbInterventionsRealisees(), precedente.getNbInterventionsRealisees()))
                .deltaSatisfactionPoints(courante.getSatisfactionMoyenne() - precedente.getSatisfactionMoyenne())
                .deltaIncidentsPourcent(deltaPourcent(courante.getNbIncidents(), precedente.getNbIncidents()))
                .build();
    }

    // ───────────────────────── Requêtes ─────────────────────────

    private List<AffectationAgent> affectations(LocalDate d1, LocalDate d2, String siteId, String employeId) {
        Query q = periode("dateDebut", d1, d2);
        q.addCriteria(Criteria.where("statut").ne(StatutAffectation.ANNULEE));
        filtres(q, siteId, employeId);
        return mongoTemplate.find(q, AffectationAgent.class);
    }

    private List<FicheIntervention> interventions(LocalDate d1, LocalDate d2, String siteId, String employeId) {
        Query q = periode("dateDebut", d1, d2);
        filtres(q, siteId, employeId);
        return mongoTemplate.find(q, FicheIntervention.class);
    }

    private List<ControleQualiteTerrain> controles(LocalDate d1, LocalDate d2, String siteId) {
        Query q = periode("dateControle", d1, d2);
        if (siteId != null && !siteId.isBlank()) q.addCriteria(Criteria.where("siteId").is(siteId));
        return mongoTemplate.find(q, ControleQualiteTerrain.class);
    }

    private List<AlerteTerrain> alertes(LocalDate d1, LocalDate d2, String siteId, String employeId) {
        Query q = periode("dateEvenement", d1, d2);
        filtres(q, siteId, employeId);
        return mongoTemplate.find(q, AlerteTerrain.class);
    }

    private Query periode(String champ, LocalDate d1, LocalDate d2) {
        LocalDateTime debut = d1.atStartOfDay();
        LocalDateTime fin = d2.atTime(LocalTime.MAX);
        return new Query(Criteria.where(champ).gte(debut).lte(fin));
    }

    private void filtres(Query q, String siteId, String employeId) {
        if (siteId != null && !siteId.isBlank()) q.addCriteria(Criteria.where("siteId").is(siteId));
        if (employeId != null && !employeId.isBlank()) q.addCriteria(Criteria.where("employeId").is(employeId));
    }

    // ───────────────────────── Helpers ─────────────────────────

    private double ratio(long numerateur, long denominateur) {
        return denominateur == 0 ? 0d : (double) numerateur / (double) denominateur;
    }

    private double deltaPourcent(long courant, long precedent) {
        return precedent == 0 ? 0d : ((double) (courant - precedent) / (double) precedent) * 100d;
    }

    private boolean parMois(LocalDate d1, LocalDate d2) {
        return ChronoUnit.DAYS.between(d1, d2) > 62;
    }

    private String bucket(LocalDateTime date, boolean parMois) {
        return parMois
                ? date.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                : date.toLocalDate().toString();
    }
}