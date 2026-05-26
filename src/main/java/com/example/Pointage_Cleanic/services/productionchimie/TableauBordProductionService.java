package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.ComparaisonPeriodes;
import com.example.Pointage_Cleanic.Dto.productionchimie.EvolutionMensuelle;
import com.example.Pointage_Cleanic.Dto.productionchimie.KpiProductionPeriode;
import com.example.Pointage_Cleanic.Dto.productionchimie.RapportTableauBord;
import com.example.Pointage_Cleanic.Dto.productionchimie.RendementProduit;
import com.example.Pointage_Cleanic.Dto.productionchimie.RepartitionStatutCq;
import com.example.Pointage_Cleanic.Dto.productionchimie.VolumeParProduit;
import com.example.Pointage_Cleanic.Enum.StatutControleLot;
import com.example.Pointage_Cleanic.Enum.StatutOf;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import com.example.Pointage_Cleanic.entities.productionchimie.Lot;
import com.example.Pointage_Cleanic.entities.productionchimie.OrdreFabrication;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class TableauBordProductionService {

    private final MongoTemplate mongoTemplate;

    public RapportTableauBord rapport(LocalDate dateDebut, LocalDate dateFin, String produitNom, String operateurId) {
        KpiProductionPeriode kpis = kpis(dateDebut, dateFin, produitNom, operateurId);
        ComparaisonPeriodes comp = comparaisonPeriodes(dateDebut, dateFin, produitNom, operateurId);
        return RapportTableauBord.builder()
                .kpis(kpis)
                .volumesParProduit(volumesParProduit(dateDebut, dateFin, produitNom))
                .evolutionMensuelle(evolutionMensuelle(dateDebut, dateFin, produitNom))
                .rendements(rendements(dateDebut, dateFin, produitNom, operateurId))
                .repartitionCq(repartitionCq(dateDebut, dateFin, produitNom))
                .comparaison(comp)
                .build();
    }

    public KpiProductionPeriode kpis(LocalDate dateDebut, LocalDate dateFin, String produitNom, String operateurId) {
        List<Lot> lots = lotsDansPeriode(dateDebut, dateFin, produitNom);
        List<OrdreFabrication> ofs = ofsDansPeriode(dateDebut, dateFin, produitNom, operateurId);

        double volumeLitres = lots.stream().mapToDouble(l -> toLitres(l.getQuantiteProduite(), l.getUniteProduite())).sum();
        long nbTermines = ofs.stream().filter(o -> o.getStatut() == StatutOf.TERMINE).count();
        long nbAnnules = ofs.stream().filter(o -> o.getStatut() == StatutOf.ANNULE).count();
        long nbValide = lots.stream().filter(l -> l.getStatutControle() == StatutControleLot.VALIDE).count();
        long nbRejete = lots.stream().filter(l -> l.getStatutControle() == StatutControleLot.REJETE).count();
        long nbAttente = lots.stream().filter(l -> l.getStatutControle() == StatutControleLot.EN_ATTENTE_CONTROLE).count();

        double tauxReussite = (nbValide + nbRejete) == 0 ? 0.0 : (double) nbValide / (nbValide + nbRejete);

        double tauxPerteMoyen = ofs.stream()
                .filter(o -> o.getStatut() == StatutOf.TERMINE
                        && o.getQuantiteCible() != null
                        && o.getQuantiteReelle() != null)
                .mapToDouble(this::tauxPerteOf)
                .average()
                .orElse(0.0);

        return KpiProductionPeriode.builder()
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .volumeProduitLitres(volumeLitres)
                .nbOfTermines(nbTermines)
                .nbOfAnnules(nbAnnules)
                .tauxReussiteCq(tauxReussite)
                .tauxPerteMoyen(tauxPerteMoyen)
                .nbLotsValide(nbValide)
                .nbLotsRejete(nbRejete)
                .nbLotsEnAttenteControle(nbAttente)
                .nbLotsTotaux(lots.size())
                .build();
    }

    public List<VolumeParProduit> volumesParProduit(LocalDate dateDebut, LocalDate dateFin, String produitNom) {
        List<Lot> lots = lotsDansPeriode(dateDebut, dateFin, produitNom);
        Map<String, double[]> agg = new HashMap<>();
        for (Lot l : lots) {
            agg.computeIfAbsent(l.getProduitNom(), k -> new double[]{0.0, 0.0});
            agg.get(l.getProduitNom())[0] += toLitres(l.getQuantiteProduite(), l.getUniteProduite());
            agg.get(l.getProduitNom())[1] += 1;
        }
        List<VolumeParProduit> result = new ArrayList<>();
        agg.forEach((nom, arr) -> result.add(new VolumeParProduit(nom, arr[0], (long) arr[1])));
        result.sort((a, b) -> Double.compare(b.getVolumeLitres(), a.getVolumeLitres()));
        return result;
    }

    public List<EvolutionMensuelle> evolutionMensuelle(LocalDate dateDebut, LocalDate dateFin, String produitNom) {
        List<Lot> lots = lotsDansPeriode(dateDebut, dateFin, produitNom);
        Map<String, double[]> agg = new TreeMap<>();
        for (Lot l : lots) {
            if (l.getDateFabrication() == null) continue;
            String mois = YearMonth.from(l.getDateFabrication()).toString();
            agg.computeIfAbsent(mois, k -> new double[]{0.0, 0.0});
            agg.get(mois)[0] += toLitres(l.getQuantiteProduite(), l.getUniteProduite());
            agg.get(mois)[1] += 1;
        }
        List<EvolutionMensuelle> result = new ArrayList<>();
        agg.forEach((mois, arr) -> result.add(new EvolutionMensuelle(mois, arr[0], (long) arr[1])));
        return result;
    }

    public List<RendementProduit> rendements(LocalDate dateDebut, LocalDate dateFin, String produitNom, String operateurId) {
        List<OrdreFabrication> ofs = ofsDansPeriode(dateDebut, dateFin, produitNom, operateurId).stream()
                .filter(o -> o.getStatut() == StatutOf.TERMINE)
                .toList();
        Map<String, double[]> agg = new LinkedHashMap<>();
        for (OrdreFabrication of : ofs) {
            double cibleLitres = toLitres(of.getQuantiteCible(), of.getUniteCible());
            double reelleLitres = toLitres(of.getQuantiteReelle(), of.getUniteCible());
            agg.computeIfAbsent(of.getProduitNom(), k -> new double[]{0.0, 0.0, 0.0});
            agg.get(of.getProduitNom())[0] += cibleLitres;
            agg.get(of.getProduitNom())[1] += reelleLitres;
            agg.get(of.getProduitNom())[2] += 1;
        }
        List<RendementProduit> result = new ArrayList<>();
        agg.forEach((nom, arr) -> {
            double th = arr[0], re = arr[1];
            double ecart = th == 0 ? 0 : ((re - th) / th) * 100.0;
            result.add(new RendementProduit(nom, th, re, ecart, (long) arr[2]));
        });
        return result;
    }

    public RepartitionStatutCq repartitionCq(LocalDate dateDebut, LocalDate dateFin, String produitNom) {
        List<Lot> lots = lotsDansPeriode(dateDebut, dateFin, produitNom);
        long valides = lots.stream().filter(l -> l.getStatutControle() == StatutControleLot.VALIDE).count();
        long rejetes = lots.stream().filter(l -> l.getStatutControle() == StatutControleLot.REJETE).count();
        long attentes = lots.stream().filter(l -> l.getStatutControle() == StatutControleLot.EN_ATTENTE_CONTROLE).count();
        return new RepartitionStatutCq(valides, rejetes, attentes);
    }

    public ComparaisonPeriodes comparaisonPeriodes(LocalDate dateDebut, LocalDate dateFin, String produitNom, String operateurId) {
        long jours = ChronoUnit.DAYS.between(dateDebut, dateFin) + 1;
        LocalDate prevDebut = dateDebut.minusDays(jours);
        LocalDate prevFin = dateDebut.minusDays(1);

        KpiProductionPeriode courante = kpis(dateDebut, dateFin, produitNom, operateurId);
        KpiProductionPeriode precedente = kpis(prevDebut, prevFin, produitNom, operateurId);

        double deltaVol = precedente.getVolumeProduitLitres() == 0
                ? 0
                : ((courante.getVolumeProduitLitres() - precedente.getVolumeProduitLitres()) / precedente.getVolumeProduitLitres()) * 100.0;
        double deltaTaux = (courante.getTauxReussiteCq() - precedente.getTauxReussiteCq()) * 100.0;
        double deltaOf = precedente.getNbOfTermines() == 0
                ? 0
                : ((double) (courante.getNbOfTermines() - precedente.getNbOfTermines()) / precedente.getNbOfTermines()) * 100.0;

        return ComparaisonPeriodes.builder()
                .periodeCourante(courante)
                .periodePrecedente(precedente)
                .deltaVolumePourcent(deltaVol)
                .deltaTauxReussitePoints(deltaTaux)
                .deltaNbOfTerminesPourcent(deltaOf)
                .build();
    }

    private List<Lot> lotsDansPeriode(LocalDate dateDebut, LocalDate dateFin, String produitNom) {
        List<Criteria> c = new ArrayList<>();
        if (dateDebut != null) c.add(Criteria.where("dateFabrication").gte(dateDebut.atStartOfDay()));
        if (dateFin != null) c.add(Criteria.where("dateFabrication").lte(dateFin.atTime(23, 59, 59)));
        if (produitNom != null && !produitNom.isBlank()) c.add(Criteria.where("produitNom").is(produitNom));
        Query q = new Query();
        if (!c.isEmpty()) q.addCriteria(new Criteria().andOperator(c.toArray(new Criteria[0])));
        return mongoTemplate.find(q, Lot.class);
    }

    private List<OrdreFabrication> ofsDansPeriode(LocalDate dateDebut, LocalDate dateFin, String produitNom, String operateurId) {
        List<Criteria> c = new ArrayList<>();
        if (dateDebut != null) c.add(Criteria.where("createdAt").gte(dateDebut.atStartOfDay()));
        if (dateFin != null) c.add(Criteria.where("createdAt").lte(dateFin.atTime(23, 59, 59)));
        if (produitNom != null && !produitNom.isBlank()) c.add(Criteria.where("produitNom").is(produitNom));
        if (operateurId != null && !operateurId.isBlank()) c.add(Criteria.where("operateurResponsableId").is(operateurId));
        Query q = new Query();
        if (!c.isEmpty()) q.addCriteria(new Criteria().andOperator(c.toArray(new Criteria[0])));
        return mongoTemplate.find(q, OrdreFabrication.class);
    }

    private double tauxPerteOf(OrdreFabrication of) {
        double cible = toLitres(of.getQuantiteCible(), of.getUniteCible());
        double reelle = toLitres(of.getQuantiteReelle(), of.getUniteCible());
        if (cible == 0) return 0.0;
        return Math.max(0.0, (cible - reelle) / cible);
    }

    private double toLitres(Double quantite, UniteChimie unite) {
        if (quantite == null) return 0.0;
        if (unite == null) return quantite;
        return switch (unite) {
            case L -> quantite;
            case ML -> quantite / 1000.0;
            default -> quantite;
        };
    }
}
