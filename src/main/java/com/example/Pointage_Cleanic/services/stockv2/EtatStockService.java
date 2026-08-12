package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.EtatStockDto;
import com.example.Pointage_Cleanic.Dto.stockv2.SeuilPayload;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutStock;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.stockv2.CategorieStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.StockParSiteRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EtatStockService {

    private final ProduitStockRepository produitRepository;
    private final StockParSiteRepository stockParSiteRepository;
    private final CategorieStockRepository categorieRepository;
    private final ReferentielSiteService referentielSite;
    private final MongoTemplate mongoTemplate;

    public PageResponse<EtatStockDto> list(int page, int size, String q, String categorieId,
                                           TypeProduit typeProduit, String siteId, StatutStock statut,
                                           Boolean parSite) {
        List<ProduitStock> produits = produitsFiltres(q, categorieId, typeProduit);
        Map<String, String> categories = libellesCategories(produits);

        List<EtatStockDto> rows = new ArrayList<>();
        boolean detailParSite = Boolean.TRUE.equals(parSite);

        if (detailParSite) {
            Map<String, List<StockParSite>> soldesParProduit = produits.isEmpty() ? Map.of()
                    : stockParSiteRepository.findByProduitIdIn(produits.stream().map(ProduitStock::getId).toList())
                    .stream().collect(Collectors.groupingBy(StockParSite::getProduitId));
            for (ProduitStock p : produits) {
                for (StockParSite solde : soldesParProduit.getOrDefault(p.getId(), List.of())) {
                    if (siteId != null && !siteId.isBlank() && !siteId.equals(solde.getSiteId())) {
                        continue;
                    }
                    double seuil = solde.getSeuilAlerteOverride() != null ? solde.getSeuilAlerteOverride() : p.getSeuilAlerte();
                    rows.add(ligne(p, categories, solde.getSiteId(), solde.getQuantite(), seuil, solde.getDateMaj()));
                }
            }
        } else {
            for (ProduitStock p : produits) {
                List<StockParSite> soldes = stockParSiteRepository.findByProduitId(p.getId());
                double quantite = soldes.stream().mapToDouble(StockParSite::getQuantite).sum();
                LocalDateTime dateMaj = soldes.stream().map(StockParSite::getDateMaj)
                        .filter(d -> d != null).max(LocalDateTime::compareTo).orElse(null);
                rows.add(ligne(p, categories, null, quantite, p.getSeuilAlerte(), dateMaj));
            }
        }

        if (statut != null) {
            rows = rows.stream().filter(r -> r.getStatut() == statut).collect(Collectors.toList());
        }

        long totalElements = rows.size();
        int from = Math.min(Math.max(page, 0) * Math.max(size, 1), rows.size());
        int to = Math.min(from + size, rows.size());
        List<EtatStockDto> content = from >= to ? List.of() : new ArrayList<>(rows.subList(from, to));
        return new PageResponse<>(content, totalElements);
    }

    /**
     * État de stock d'un seul produit, consolidé ou raffiné par site.
     *
     * <p>Sert les colonnes de stock des bons (7.4) : « Reste » sur un bon de sortie, « Stock
     * actuel » sur un bon d'entrée. Un {@code siteId} absent renvoie le solde tous sites confondus.
     *
     * <p>Un produit jamais mouvementé (sur ce site ou globalement) n'est pas une anomalie : la
     * quantité vaut alors 0, et c'est au client de distinguer « 0 » de « inconnu ».
     */
    public EtatStockDto getParProduit(String produitId, String siteId) {
        ProduitStock produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable : " + produitId));
        Map<String, String> categories = libellesCategories(List.of(produit));
        String site = (siteId == null || siteId.isBlank()) ? null : siteId;

        if (site != null) {
            return stockParSiteRepository.findByProduitIdAndSiteId(produitId, site)
                    .map(solde -> ligne(produit, categories, site, solde.getQuantite(),
                            seuilEffectif(solde, produit), solde.getDateMaj()))
                    .orElseGet(() -> ligne(produit, categories, site, 0.0, produit.getSeuilAlerte(), null));
        }

        List<StockParSite> soldes = stockParSiteRepository.findByProduitId(produitId);
        double quantite = soldes.stream().mapToDouble(StockParSite::getQuantite).sum();
        LocalDateTime dateMaj = soldes.stream().map(StockParSite::getDateMaj)
                .filter(d -> d != null).max(LocalDateTime::compareTo).orElse(null);
        return ligne(produit, categories, null, quantite, produit.getSeuilAlerte(), dateMaj);
    }

    /** Le seuil du site prime s'il est défini, sinon celui du produit. */
    private double seuilEffectif(StockParSite solde, ProduitStock produit) {
        return solde.getSeuilAlerteOverride() != null
                ? solde.getSeuilAlerteOverride()
                : produit.getSeuilAlerte();
    }

    public EtatStockDto majSeuil(SeuilPayload payload) {
        ProduitStock produit = produitRepository.findById(payload.getProduitId())
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable : " + payload.getProduitId()));
        Map<String, String> categories = libellesCategories(List.of(produit));

        if (payload.getSiteId() != null && !payload.getSiteId().isBlank()) {
            StockParSite solde = stockParSiteRepository
                    .findByProduitIdAndSiteId(produit.getId(), payload.getSiteId())
                    .orElseGet(() -> StockParSite.builder()
                            .produitId(produit.getId())
                            .siteId(payload.getSiteId())
                            .quantite(0.0)
                            .dateMaj(LocalDateTime.now())
                            .build());
            solde.setSeuilAlerteOverride(payload.getSeuilAlerte());
            solde = stockParSiteRepository.save(solde);
            return ligne(produit, categories, solde.getSiteId(), solde.getQuantite(),
                    payload.getSeuilAlerte(), solde.getDateMaj());
        }

        produit.setSeuilAlerte(payload.getSeuilAlerte());
        produit.setUpdatedAt(LocalDateTime.now());
        produitRepository.save(produit);
        List<StockParSite> soldes = stockParSiteRepository.findByProduitId(produit.getId());
        double quantite = soldes.stream().mapToDouble(StockParSite::getQuantite).sum();
        LocalDateTime dateMaj = soldes.stream().map(StockParSite::getDateMaj)
                .filter(d -> d != null).max(LocalDateTime::compareTo).orElse(null);
        return ligne(produit, categories, null, quantite, produit.getSeuilAlerte(), dateMaj);
    }

    // ---------------------------------------------------------------- helpers

    private List<ProduitStock> produitsFiltres(String q, String categorieId, TypeProduit typeProduit) {
        Query query = new Query();
        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("code").regex(regex, "i"),
                    Criteria.where("libelle").regex(regex, "i")
            ));
        }
        if (categorieId != null && !categorieId.isBlank()) {
            query.addCriteria(Criteria.where("categorieId").is(categorieId));
        }
        if (typeProduit != null) {
            query.addCriteria(Criteria.where("typeProduit").is(typeProduit));
        }
        return mongoTemplate.find(query, ProduitStock.class);
    }

    private EtatStockDto ligne(ProduitStock p, Map<String, String> categories, String siteId,
                               double quantite, double seuilAlerte, LocalDateTime dateMaj) {
        return EtatStockDto.builder()
                .produitId(p.getId())
                .produitCode(p.getCode())
                .produitLibelle(p.getLibelle())
                .typeProduit(p.getTypeProduit())
                .categorieLibelle(categories.get(p.getCategorieId()))
                .unite(p.getUnite())
                .siteId(siteId)
                .siteNom(referentielSite.nomDuSite(siteId))
                .quantite(quantite)
                .seuilAlerte(seuilAlerte)
                .statut(calculerStatut(quantite, seuilAlerte))
                .prixUnitaire(p.getPrixUnitaire())
                .valeur(quantite * p.getPrixUnitaire())
                .dateMaj(dateMaj)
                .build();
    }

    private StatutStock calculerStatut(double quantite, double seuilAlerte) {
        if (quantite <= 0) {
            return StatutStock.RUPTURE;
        }
        if (quantite <= seuilAlerte) {
            return StatutStock.CRITIQUE;
        }
        return StatutStock.OK;
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
