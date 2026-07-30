package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fusionne les soldes de stock rattachés à un site dans le solde de l'entrepôt unique
 * ({@code siteId = null}).
 * <p>
 * Historiquement, la validation d'un bon d'entrée créditait le site de destination du bon,
 * ce qui fragmentait le stock d'un produit en plusieurs soldes alors que le métier ne gère
 * qu'un seul lieu de stockage. La clôture d'un inventaire global, elle, impute l'écart au
 * seul solde de l'entrepôt : celui-ci pouvait donc devenir négatif pendant que les soldes
 * par site restaient gonflés (le total consolidé, lui, restait juste).
 * <p>
 * Le runner est idempotent : une fois la fusion faite, il ne trouve plus de solde rattaché
 * à un site et ne fait rien.
 */
@Slf4j
@Component
@Order(1010)
@RequiredArgsConstructor
public class StockEntrepotUniqueMigrationRunner implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        List<StockParSite> soldesSite = mongoTemplate.find(
                Query.query(Criteria.where("siteId").ne(null)), StockParSite.class);
        if (soldesSite.isEmpty()) {
            return;
        }

        Map<String, List<StockParSite>> parProduit = soldesSite.stream()
                .collect(Collectors.groupingBy(StockParSite::getProduitId));

        int produitsFusionnes = 0;
        for (Map.Entry<String, List<StockParSite>> entree : parProduit.entrySet()) {
            fusionner(entree.getKey(), entree.getValue());
            produitsFusionnes++;
        }

        log.info("Migration stock : {} solde(s) rattaché(s) à un site fusionné(s) dans l'entrepôt "
                + "pour {} produit(s)", soldesSite.size(), produitsFusionnes);
    }

    private void fusionner(String produitId, List<StockParSite> soldesSite) {
        StockParSite entrepot = mongoTemplate.findOne(
                Query.query(Criteria.where("produitId").is(produitId).and("siteId").is(null)),
                StockParSite.class);
        if (entrepot == null) {
            entrepot = StockParSite.builder()
                    .produitId(produitId)
                    .siteId(null)
                    .quantite(0.0)
                    .build();
        }

        double quantiteSites = soldesSite.stream().mapToDouble(StockParSite::getQuantite).sum();
        entrepot.setQuantite(entrepot.getQuantite() + quantiteSites);
        entrepot.setDateMaj(plusRecente(entrepot, soldesSite));

        for (StockParSite solde : soldesSite) {
            if (solde.getSeuilAlerteOverride() != null) {
                log.info("Migration stock : surcharge de seuil abandonnée (produit {}, site {}, seuil {}) "
                                + "— le seuil global du produit s'applique désormais",
                        produitId, solde.getSiteId(), solde.getSeuilAlerteOverride());
            }
        }

        // Écrire l'entrepôt AVANT de supprimer les soldes site : l'index unique
        // (produitId, siteId) interdit de réassigner siteId = null sur un document existant.
        mongoTemplate.save(entrepot);
        soldesSite.forEach(solde -> mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(solde.getId())), StockParSite.class));
    }

    private LocalDateTime plusRecente(StockParSite entrepot, List<StockParSite> soldesSite) {
        LocalDateTime maxSites = soldesSite.stream()
                .map(StockParSite::getDateMaj)
                .filter(d -> d != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (entrepot.getDateMaj() == null) {
            return maxSites != null ? maxSites : LocalDateTime.now();
        }
        return maxSites == null || entrepot.getDateMaj().isAfter(maxSites) ? entrepot.getDateMaj() : maxSites;
    }
}
