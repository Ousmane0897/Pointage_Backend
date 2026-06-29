package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.CoutProduitDto;
import com.example.Pointage_Cleanic.Dto.stockv2.HistoriqueCoutProduitDto;
import com.example.Pointage_Cleanic.Enum.stockv2.AlerteCout;
import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
import com.example.Pointage_Cleanic.entities.stockv2.HistoriquePointCout;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.stockv2.CategorieStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.HistoriquePointCoutRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Coût unitaire par produit (Stock v2 7.6) : coût courant, méthode effective, valeur de stock,
 * alertes, historique des points de coût.
 */
@Service
@RequiredArgsConstructor
public class CoutProduitService {

    private final ProduitStockRepository produitRepository;
    private final CategorieStockRepository categorieRepository;
    private final HistoriquePointCoutRepository historiqueRepository;
    private final StockBalanceService balanceService;
    private final ParametrageValorisationService parametrageService;
    private final ValorisationSupport valorisationSupport;
    private final MongoTemplate mongoTemplate;

    public PageResponse<CoutProduitDto> list(int page, int size, String q, TypeProduit typeProduit,
                                             String categorieId, MethodeValorisation methode, Boolean avecAlerte) {
        Query query = new Query().with(Sort.by("libelle").ascending());
        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("code").regex(regex, "i"),
                    Criteria.where("libelle").regex(regex, "i")));
        }
        if (typeProduit != null) {
            query.addCriteria(Criteria.where("typeProduit").is(typeProduit));
        }
        if (categorieId != null && !categorieId.isBlank()) {
            query.addCriteria(Criteria.where("categorieId").is(categorieId));
        }

        List<ProduitStock> produits = mongoTemplate.find(query, ProduitStock.class);
        MethodeValorisation defaut = parametrageService.methodeDefaut();
        Map<String, String> categories = libellesCategories(produits);

        List<CoutProduitDto> all = new ArrayList<>(produits.size());
        for (ProduitStock p : produits) {
            CoutProduitDto dto = toCoutProduit(p, defaut, categories);
            if (methode != null && dto.getMethodeEffective() != methode) {
                continue;
            }
            if (Boolean.TRUE.equals(avecAlerte) && dto.getAlertes().isEmpty()) {
                continue;
            }
            all.add(dto);
        }

        long totalElements = all.size();
        int from = Math.min(Math.max(page, 0) * Math.max(size, 1), all.size());
        int to = Math.min(from + Math.max(size, 1), all.size());
        List<CoutProduitDto> content = from >= to ? List.of() : new ArrayList<>(all.subList(from, to));
        return new PageResponse<>(content, totalElements);
    }

    public HistoriqueCoutProduitDto historique(String id) {
        ProduitStock produit = produitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable : " + id));
        List<HistoriquePointCout> points = historiqueRepository.findByProduitIdOrderByDateAsc(id);
        List<HistoriqueCoutProduitDto.PointDto> pointsDto = points.stream()
                .map(pt -> HistoriqueCoutProduitDto.PointDto.builder()
                        .date(pt.getDate())
                        .cout(pt.getCout())
                        .methode(pt.getMethode())
                        .reference(pt.getReferenceMouvement())
                        .build())
                .toList();
        return HistoriqueCoutProduitDto.builder()
                .produitId(produit.getId())
                .produitCode(produit.getCode())
                .produitLibelle(produit.getLibelle())
                .points(pointsDto)
                .build();
    }

    private CoutProduitDto toCoutProduit(ProduitStock p, MethodeValorisation defaut, Map<String, String> categories) {
        long coutCourant = p.getPrixUnitaire();
        MethodeValorisation effective = valorisationSupport.methodeEffective(p, defaut);
        double quantiteTotale = balanceService.quantiteTotale(p.getId());

        // Deux derniers points : [0] = dernier calcul (coût courant), [1] = coût précédent.
        List<HistoriquePointCout> recents = historiqueRepository.findByProduitIdOrderByCreatedAtDesc(p.getId());
        Long dernierCout = recents.size() >= 2 ? recents.get(1).getCout() : null;
        var dateDernierCalcul = recents.isEmpty() ? null : recents.get(0).getCreatedAt();

        List<AlerteCout> alertes = new ArrayList<>();
        if (p.getMethodeValorisation() == null && defaut == null) {
            alertes.add(AlerteCout.METHODE_NON_DEFINIE);
        }
        if (coutCourant <= 0) {
            alertes.add(AlerteCout.COUT_ZERO);
        }
        if (dernierCout != null && dernierCout != 0
                && Math.abs(coutCourant - dernierCout) / (double) Math.abs(dernierCout) * 100.0
                        > ValorisationSupport.ECART_COUT_ANORMAL) {
            alertes.add(AlerteCout.ECART_ANORMAL);
        }

        return CoutProduitDto.builder()
                .produitId(p.getId())
                .produitCode(p.getCode())
                .produitLibelle(p.getLibelle())
                .typeProduit(p.getTypeProduit())
                .categorieLibelle(categories.get(p.getCategorieId()))
                .unite(p.getUnite())
                .coutCourant(coutCourant)
                .methodeEffective(effective)
                .methodeProduit(p.getMethodeValorisation())
                .prixVente(p.getPrixVente())
                .quantiteTotale(quantiteTotale)
                .valeurStock(Math.round(quantiteTotale * coutCourant))
                .alertes(alertes)
                .dernierCout(dernierCout)
                .dateDernierCalcul(dateDernierCalcul)
                .build();
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
