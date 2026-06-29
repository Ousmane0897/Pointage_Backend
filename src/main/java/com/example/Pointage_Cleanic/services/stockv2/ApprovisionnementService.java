package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.SuggestionApproDto;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovisionnementService {

    private static final int DEFAULT_N_MOIS = 3;

    private final ProduitStockRepository produitRepository;
    private final MouvementStockRepository mouvementRepository;
    private final StockBalanceService balanceService;
    private final MongoTemplate mongoTemplate;

    public List<SuggestionApproDto> suggestions(Integer nMois, String siteId, String categorieId, String fournisseur) {
        int horizon = (nMois == null || nMois < 1) ? DEFAULT_N_MOIS : nMois;
        String site = (siteId == null || siteId.isBlank()) ? null : siteId;
        LocalDate debut = LocalDate.now().minusMonths(horizon);

        List<ProduitStock> produits = produitRepository.findAll().stream()
                .filter(p -> categorieId == null || categorieId.isBlank() || categorieId.equals(p.getCategorieId()))
                .filter(p -> fournisseur == null || fournisseur.isBlank()
                        || (p.getFournisseurPrincipal() != null
                        && p.getFournisseurPrincipal().toLowerCase().contains(fournisseur.toLowerCase())))
                .toList();

        if (produits.isEmpty()) {
            return List.of();
        }

        // Sorties par produit sur la fenêtre [debut, aujourd'hui]
        Query query = new Query(Criteria.where("date").gte(debut)
                .and("produitId").in(produits.stream().map(ProduitStock::getId).toList()));
        List<MouvementStock> mouvements = mongoTemplate.find(query, MouvementStock.class);
        Map<String, Double> sortiesParProduit = mouvements.stream()
                .collect(Collectors.groupingBy(MouvementStock::getProduitId,
                        Collectors.summingDouble(m -> StockImpactCalculator.sortie(m, site))));

        List<SuggestionApproDto> suggestions = new ArrayList<>();
        for (ProduitStock p : produits) {
            double stockActuel = site == null
                    ? balanceService.quantiteTotale(p.getId())
                    : balanceService.quantite(p.getId(), site);
            double consommationMoyenne = sortiesParProduit.getOrDefault(p.getId(), 0.0) / horizon;
            double consommationPrevisionnelle = consommationMoyenne; // projection sur 1 mois
            double besoin = p.getSeuilAlerte() + consommationPrevisionnelle - stockActuel;
            if (besoin <= 0) {
                continue;
            }
            double quantiteSuggeree = Math.ceil(besoin);
            long montantEstime = Math.round(quantiteSuggeree * p.getPrixUnitaire());
            suggestions.add(SuggestionApproDto.builder()
                    .produitId(p.getId())
                    .produitCode(p.getCode())
                    .produitLibelle(p.getLibelle())
                    .unite(p.getUnite())
                    .fournisseurPrincipal(p.getFournisseurPrincipal())
                    .stockActuel(stockActuel)
                    .seuilAlerte(p.getSeuilAlerte())
                    .consommationMoyenne(consommationMoyenne)
                    .consommationPrevisionnelle(consommationPrevisionnelle)
                    .besoin(besoin)
                    .quantiteSuggeree(quantiteSuggeree)
                    .prixUnitaire(p.getPrixUnitaire())
                    .montantEstime(montantEstime)
                    .build());
        }
        return suggestions;
    }
}
