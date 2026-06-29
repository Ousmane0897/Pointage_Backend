package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ValeurStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.PeriodeComparaison;
import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Valeur du stock temps réel (Stock v2 7.6) : valeur courante, répartition par catégorie, lignes,
 * et valeur à un instant T précédent (reconstruite par rejeu des mouvements au coût courant).
 */
@Service
@RequiredArgsConstructor
public class ValeurStockService {

    private final ProduitStockRepository produitRepository;
    private final CategorieStockRepository categorieRepository;
    private final StockBalanceService balanceService;
    private final MongoTemplate mongoTemplate;

    public ValeurStockDto valeur(String siteId, String categorieId, PeriodeComparaison comparer) {
        String site = (siteId == null || siteId.isBlank()) ? null : siteId;

        List<ProduitStock> produits = produitRepository.findAll().stream()
                .filter(p -> categorieId == null || categorieId.isBlank() || categorieId.equals(p.getCategorieId()))
                .toList();
        Map<String, String> categories = libellesCategories(produits);

        long valeurTotale = 0;
        int nbProduits = 0;
        Map<String, long[]> valeurParCat = new LinkedHashMap<>();   // [valeur, quantite arrondie]
        Map<String, Double> quantiteParCat = new LinkedHashMap<>();
        List<ValeurStockDto.LigneValeur> lignes = new ArrayList<>();

        for (ProduitStock p : produits) {
            double quantite = site == null
                    ? balanceService.quantiteTotale(p.getId())
                    : balanceService.quantite(p.getId(), site);
            if (quantite == 0) {
                continue;
            }
            long valeur = Math.round(quantite * p.getPrixUnitaire());
            valeurTotale += valeur;
            nbProduits++;

            String cat = categories.getOrDefault(p.getCategorieId(), "Non catégorisé");
            valeurParCat.computeIfAbsent(cat, k -> new long[]{0})[0] += valeur;
            quantiteParCat.merge(cat, quantite, Double::sum);

            lignes.add(ValeurStockDto.LigneValeur.builder()
                    .produitId(p.getId())
                    .produitCode(p.getCode())
                    .produitLibelle(p.getLibelle())
                    .categorieLibelle(categories.get(p.getCategorieId()))
                    .quantite(quantite)
                    .coutUnitaire(p.getPrixUnitaire())
                    .valeur(valeur)
                    .build());
        }

        List<ValeurStockDto.RepartitionCategorie> repartition = valeurParCat.entrySet().stream()
                .map(e -> ValeurStockDto.RepartitionCategorie.builder()
                        .categorie(e.getKey())
                        .valeur(e.getValue()[0])
                        .quantite(quantiteParCat.get(e.getKey()))
                        .build())
                .toList();

        ValeurStockDto.Kpis.KpisBuilder kpis = ValeurStockDto.Kpis.builder()
                .valeurTotale(valeurTotale)
                .nbProduits(nbProduits);

        if (comparer != null) {
            long valeurPrecedente = valeurAuPasse(produits, site, dateReference(comparer));
            long ecart = valeurTotale - valeurPrecedente;
            kpis.valeurPrecedente(valeurPrecedente)
                    .ecartValeur(ecart)
                    .ecartPct(valeurPrecedente == 0 ? null : ecart * 100.0 / valeurPrecedente);
        }

        return ValeurStockDto.builder()
                .kpis(kpis.build())
                .repartitionCategorie(repartition)
                .lignes(lignes)
                .dateCalcul(LocalDateTime.now().toString())
                .build();
    }

    private LocalDate dateReference(PeriodeComparaison comparer) {
        LocalDate today = LocalDate.now();
        return switch (comparer) {
            case JOUR -> today.minusDays(1);
            case SEMAINE -> today.minusDays(7);
            case MOIS -> today.minusMonths(1);
        };
    }

    /** Valeur du stock à {@code refDate} : quantités reconstruites par rejeu × coût courant. */
    private long valeurAuPasse(List<ProduitStock> produits, String site, LocalDate refDate) {
        if (produits.isEmpty()) {
            return 0;
        }
        List<String> ids = produits.stream().map(ProduitStock::getId).toList();
        Query q = new Query(Criteria.where("produitId").in(ids).and("date").lte(refDate));
        Map<String, List<MouvementStock>> parProduit = mongoTemplate.find(q, MouvementStock.class).stream()
                .collect(Collectors.groupingBy(MouvementStock::getProduitId));

        long valeur = 0;
        for (ProduitStock p : produits) {
            double quantite = parProduit.getOrDefault(p.getId(), List.of()).stream()
                    .mapToDouble(m -> StockImpactCalculator.signedDelta(m, site))
                    .sum();
            valeur += Math.round(quantite * p.getPrixUnitaire());
        }
        return valeur;
    }

    private Map<String, String> libellesCategories(List<ProduitStock> produits) {
        Set<String> ids = produits.stream()
                .map(ProduitStock::getCategorieId)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        categorieRepository.findAllById(ids).forEach(c -> map.put(c.getId(), c.getLibelle()));
        return map;
    }
}
