package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.repositories.stockv2.CategorieStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Socle commun aux endpoints analytiques 7.5 : charge le périmètre des sorties EFFECTIVES
 * (mouvements {@code type=SORTIE} générés par un bon validé, {@code origine="BON"}) et fournit
 * la valorisation FCFA + les libellés de catégories. Réutilise le pattern de
 * {@link ConsommationStockService}.
 */
@Component
@RequiredArgsConstructor
public class AnalyseSupport {

    public static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MongoTemplate mongoTemplate;
    private final CategorieStockRepository categorieRepository;

    /** Périmètre résolu : mouvements de sortie effectifs + catalogue produit indexé par id. */
    public record Perimetre(List<MouvementStock> mouvements, Map<String, ProduitStock> produits) {
    }

    /** Charge les sorties EFFECTIVES filtrées. Le filtre {@code categorieId} est appliqué en mémoire. */
    public Perimetre sortiesEffectives(LocalDate dateDebut, LocalDate dateFin, String siteId,
                                       String produitId, TypeSortie typeSortie, String categorieId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("type").is(TypeMouvement.SORTIE));
        query.addCriteria(Criteria.where("origine").is("BON"));
        appliquerDates(query, dateDebut, dateFin);
        if (notBlank(siteId)) {
            query.addCriteria(Criteria.where("siteSourceId").is(siteId));
        }
        if (notBlank(produitId)) {
            query.addCriteria(Criteria.where("produitId").is(produitId));
        }
        if (typeSortie != null) {
            query.addCriteria(Criteria.where("categorieSortie").is(typeSortie));
        }
        List<MouvementStock> mouvements = mongoTemplate.find(query, MouvementStock.class);
        Map<String, ProduitStock> produits = produitsByIds(mouvements);
        if (notBlank(categorieId)) {
            mouvements = mouvements.stream()
                    .filter(m -> {
                        ProduitStock p = produits.get(m.getProduitId());
                        return p != null && categorieId.equals(p.getCategorieId());
                    })
                    .toList();
        }
        return new Perimetre(mouvements, produits);
    }

    /** Montant FCFA (entier) d'un mouvement : quantité × prix unitaire fixe du produit. */
    public long montant(MouvementStock m, Map<String, ProduitStock> produits) {
        ProduitStock p = produits.get(m.getProduitId());
        long prix = p == null ? 0L : p.getPrixUnitaire();
        return Math.round(m.getQuantite() * prix);
    }

    public long prixUnitaire(String produitId, Map<String, ProduitStock> produits) {
        ProduitStock p = produits.get(produitId);
        return p == null ? 0L : p.getPrixUnitaire();
    }

    public Map<String, ProduitStock> produitsByIds(List<MouvementStock> mouvements) {
        Set<String> ids = mouvements.stream().map(MouvementStock::getProduitId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mongoTemplate.find(new Query(Criteria.where("_id").in(ids)), ProduitStock.class).stream()
                .collect(Collectors.toMap(ProduitStock::getId, p -> p, (a, b) -> a));
    }

    /** Libellés de catégories indexés par id, pour les produits fournis. */
    public Map<String, String> libellesCategories(Map<String, ProduitStock> produits) {
        Set<String> ids = produits.values().stream()
                .map(ProduitStock::getCategorieId)
                .filter(AnalyseSupport::notBlank)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categorieRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(CategorieStock::getId, CategorieStock::getLibelle, (a, b) -> a));
    }

    public static void appliquerDates(Query query, LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut != null && dateFin != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut).lte(dateFin));
        } else if (dateDebut != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut));
        } else if (dateFin != null) {
            query.addCriteria(Criteria.where("date").lte(dateFin));
        }
    }

    public static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
