package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.SyntheseMensuelleDto;
import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.repositories.stockv2.CategorieStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import com.example.Pointage_Cleanic.services.terrain.SiteClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SyntheseMensuelleService {

    private final ProduitStockRepository produitRepository;
    private final MouvementStockRepository mouvementRepository;
    private final CategorieStockRepository categorieRepository;
    private final SiteClientService siteClientService;
    private final MongoTemplate mongoTemplate;

    public SyntheseMensuelleDto synthese(String mois, String siteId, String categorieId) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(mois);
        } catch (Exception e) {
            throw new IllegalArgumentException("Paramètre 'mois' invalide (attendu yyyy-MM) : " + mois);
        }
        LocalDate premierJour = ym.atDay(1);
        LocalDate dernierJour = ym.atEndOfMonth();
        String site = (siteId == null || siteId.isBlank()) ? null : siteId;

        List<ProduitStock> produits = produitsScope(categorieId);
        Map<String, ProduitStock> parId = produits.stream()
                .collect(Collectors.toMap(ProduitStock::getId, p -> p, (a, b) -> a));
        Map<String, String> categories = libellesCategories(produits);

        // Tous les mouvements jusqu'à la fin du mois, sur les produits du périmètre
        Query query = new Query(Criteria.where("date").lte(dernierJour));
        if (!parId.isEmpty()) {
            query.addCriteria(Criteria.where("produitId").in(parId.keySet()));
        }
        List<MouvementStock> mouvements = mongoTemplate.find(query, MouvementStock.class);

        Map<String, double[]> agg = new HashMap<>(); // [stockInitial, entrees, sorties]
        for (MouvementStock m : mouvements) {
            if (!parId.containsKey(m.getProduitId())) {
                continue;
            }
            double delta = StockImpactCalculator.signedDelta(m, site);
            if (delta == 0.0) {
                continue;
            }
            double[] a = agg.computeIfAbsent(m.getProduitId(), k -> new double[3]);
            if (m.getDate() != null && m.getDate().isBefore(premierJour)) {
                a[0] += delta;
            } else {
                if (delta > 0) {
                    a[1] += delta;
                } else {
                    a[2] += -delta;
                }
            }
        }

        List<SyntheseMensuelleDto.LigneSynthese> lignes = new ArrayList<>();
        double totalEntrees = 0;
        double totalSorties = 0;
        long valeurStockFinal = 0;
        for (ProduitStock p : produits) {
            double[] a = agg.getOrDefault(p.getId(), new double[3]);
            double stockInitial = a[0];
            double entrees = a[1];
            double sorties = a[2];
            double stockFinal = stockInitial + entrees - sorties;
            long valeurFinale = Math.round(stockFinal * p.getPrixUnitaire());
            totalEntrees += entrees;
            totalSorties += sorties;
            valeurStockFinal += valeurFinale;
            lignes.add(SyntheseMensuelleDto.LigneSynthese.builder()
                    .produitId(p.getId())
                    .produitCode(p.getCode())
                    .produitLibelle(p.getLibelle())
                    .unite(p.getUnite())
                    .categorieLibelle(categories.get(p.getCategorieId()))
                    .stockInitial(stockInitial)
                    .entrees(entrees)
                    .sorties(sorties)
                    .stockFinal(stockFinal)
                    .valeurFinale(valeurFinale)
                    .build());
        }

        return SyntheseMensuelleDto.builder()
                .mois(mois)
                .siteId(site)
                .siteNom(site == null ? null : nomSite(site))
                .lignes(lignes)
                .totalEntrees(totalEntrees)
                .totalSorties(totalSorties)
                .valeurStockFinal(valeurStockFinal)
                .build();
    }

    private List<ProduitStock> produitsScope(String categorieId) {
        if (categorieId != null && !categorieId.isBlank()) {
            return produitRepository.findByCategorieId(categorieId);
        }
        return produitRepository.findAll();
    }

    private String nomSite(String siteId) {
        try {
            return siteClientService.loadOrThrow(siteId).getNom();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Map<String, String> libellesCategories(List<ProduitStock> produits) {
        var ids = produits.stream().map(ProduitStock::getCategorieId)
                .filter(c -> c != null && !c.isBlank()).collect(Collectors.toSet());
        Map<String, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        categorieRepository.findAllById(ids).forEach((CategorieStock c) -> map.put(c.getId(), c.getLibelle()));
        return map;
    }
}
