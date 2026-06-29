package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.RapportTableauBordStockDto;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.repositories.stockv2.CategorieStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TableauBordStockService {

    private static final int DEFAULT_MOIS_DORMANCE = 3;
    private static final DateTimeFormatter MOIS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ProduitStockRepository produitRepository;
    private final CategorieStockRepository categorieRepository;
    private final StockBalanceService balanceService;
    private final MongoTemplate mongoTemplate;

    public RapportTableauBordStockDto rapport(LocalDate dateDebut, LocalDate dateFin, String siteId,
                                              String categorieId, Integer moisDormance) {
        LocalDate fin = dateFin != null ? dateFin : LocalDate.now();
        LocalDate debut = dateDebut != null ? dateDebut : fin.minusMonths(5).withDayOfMonth(1);
        String site = (siteId == null || siteId.isBlank()) ? null : siteId;
        int dormance = (moisDormance == null || moisDormance < 1) ? DEFAULT_MOIS_DORMANCE : moisDormance;
        LocalDate seuilDormance = LocalDate.now().minusMonths(dormance);

        List<ProduitStock> produits = produitRepository.findAll().stream()
                .filter(p -> categorieId == null || categorieId.isBlank() || categorieId.equals(p.getCategorieId()))
                .toList();
        Map<String, String> categories = libellesCategories(produits);

        // Mouvements du périmètre jusqu'à dateFin (pour évolution, sorties, dormance)
        List<MouvementStock> mouvements;
        if (produits.isEmpty()) {
            mouvements = List.of();
        } else {
            Query q = new Query(Criteria.where("date").lte(fin)
                    .and("produitId").in(produits.stream().map(ProduitStock::getId).toList()));
            mouvements = mongoTemplate.find(q, MouvementStock.class);
        }
        Map<String, List<MouvementStock>> mvtParProduit = mouvements.stream()
                .collect(Collectors.groupingBy(MouvementStock::getProduitId));

        // --- KPIs + agrégats par produit
        double valeurTotale = 0;
        long nbRupture = 0;
        long nbAlerte = 0;
        long nbDormants = 0;
        double sommeRotation = 0;
        int nbRotationComptes = 0;

        Map<String, Long> valeurParCategorie = new LinkedHashMap<>();
        List<RapportTableauBordStockDto.TopConsommation> consommations = new ArrayList<>();
        List<RapportTableauBordStockDto.ProduitDormant> dormants = new ArrayList<>();

        for (ProduitStock p : produits) {
            List<MouvementStock> mvts = mvtParProduit.getOrDefault(p.getId(), List.of());
            double stockActuel = site == null
                    ? balanceService.quantiteTotale(p.getId())
                    : balanceService.quantite(p.getId(), site);
            double valeur = stockActuel * p.getPrixUnitaire();
            valeurTotale += valeur;

            if (stockActuel <= 0) {
                nbRupture++;
            } else if (stockActuel <= p.getSeuilAlerte()) {
                nbAlerte++;
            }

            double sortiesPeriode = mvts.stream()
                    .filter(m -> dansPeriode(m.getDate(), debut, fin))
                    .mapToDouble(m -> StockImpactCalculator.sortie(m, site))
                    .sum();
            if (stockActuel > 0) {
                sommeRotation += sortiesPeriode / stockActuel;
                nbRotationComptes++;
            }
            if (sortiesPeriode > 0) {
                consommations.add(RapportTableauBordStockDto.TopConsommation.builder()
                        .produitLibelle(p.getLibelle())
                        .quantite(sortiesPeriode)
                        .unite(p.getUnite())
                        .build());
            }

            LocalDate dernierMvtDate = mvts.stream().map(MouvementStock::getDate)
                    .filter(d -> d != null).max(Comparator.naturalOrder()).orElse(null);
            LocalDateTime dernierMvt = mvts.stream().map(MouvementStock::getCreatedAt)
                    .filter(d -> d != null).max(Comparator.naturalOrder()).orElse(null);
            boolean dormant = dernierMvtDate == null || dernierMvtDate.isBefore(seuilDormance);
            if (dormant) {
                nbDormants++;
                dormants.add(RapportTableauBordStockDto.ProduitDormant.builder()
                        .produitId(p.getId())
                        .produitCode(p.getCode())
                        .produitLibelle(p.getLibelle())
                        .dernierMouvement(dernierMvt)
                        .quantite(stockActuel)
                        .valeur(Math.round(valeur))
                        .build());
            }

            String catLibelle = categories.getOrDefault(p.getCategorieId(), "Non catégorisé");
            valeurParCategorie.merge(catLibelle, Math.round(valeur), Long::sum);
        }

        consommations.sort(Comparator.comparingDouble(
                RapportTableauBordStockDto.TopConsommation::getQuantite).reversed());
        List<RapportTableauBordStockDto.TopConsommation> top10 =
                consommations.size() > 10 ? consommations.subList(0, 10) : consommations;

        RapportTableauBordStockDto.Kpis kpis = RapportTableauBordStockDto.Kpis.builder()
                .valeurTotale(Math.round(valeurTotale))
                .nbProduits(produits.size())
                .nbRupture(nbRupture)
                .nbAlerte(nbAlerte)
                .tauxRotationMoyen(nbRotationComptes == 0 ? 0.0 : sommeRotation / nbRotationComptes)
                .nbDormants(nbDormants)
                .build();

        return RapportTableauBordStockDto.builder()
                .kpis(kpis)
                .valeurParCategorie(valeurParCategorie.entrySet().stream()
                        .map(e -> RapportTableauBordStockDto.ValeurCategorie.builder()
                                .categorie(e.getKey()).valeur(e.getValue()).build())
                        .collect(Collectors.toList()))
                .evolutionValeur(evolution(produits, mvtParProduit, site, debut, fin))
                .topConsommations(new ArrayList<>(top10))
                .produitsDormants(dormants)
                .build();
    }

    /** Valeur du stock à la fin de chaque mois de la période, reconstruite depuis l'historique. */
    private List<RapportTableauBordStockDto.EvolutionValeur> evolution(
            List<ProduitStock> produits, Map<String, List<MouvementStock>> mvtParProduit,
            String site, LocalDate debut, LocalDate fin) {
        List<RapportTableauBordStockDto.EvolutionValeur> evolution = new ArrayList<>();
        YearMonth courant = YearMonth.from(debut);
        YearMonth dernier = YearMonth.from(fin);
        while (!courant.isAfter(dernier)) {
            LocalDate finMois = courant.atEndOfMonth();
            double valeurMois = 0;
            for (ProduitStock p : produits) {
                double cumul = mvtParProduit.getOrDefault(p.getId(), List.of()).stream()
                        .filter(m -> m.getDate() != null && !m.getDate().isAfter(finMois))
                        .mapToDouble(m -> StockImpactCalculator.signedDelta(m, site))
                        .sum();
                valeurMois += cumul * p.getPrixUnitaire();
            }
            evolution.add(RapportTableauBordStockDto.EvolutionValeur.builder()
                    .mois(courant.format(MOIS_FORMAT))
                    .valeur(Math.round(valeurMois))
                    .build());
            courant = courant.plusMonths(1);
        }
        return evolution;
    }

    private boolean dansPeriode(LocalDate date, LocalDate debut, LocalDate fin) {
        return date != null && !date.isBefore(debut) && !date.isAfter(fin);
    }

    private Map<String, String> libellesCategories(List<ProduitStock> produits) {
        var ids = produits.stream().map(ProduitStock::getCategorieId)
                .filter(c -> c != null && !c.isBlank()).collect(Collectors.toSet());
        Map<String, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        categorieRepository.findAllById(ids).forEach(c -> map.put(c.getId(), c.getLibelle()));
        return map;
    }
}
