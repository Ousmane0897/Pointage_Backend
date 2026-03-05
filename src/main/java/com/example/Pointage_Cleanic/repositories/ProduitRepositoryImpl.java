package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.stock.Produit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.regex.Pattern;

@Repository
public class ProduitRepositoryImpl
        implements ProduitRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public ProduitRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Produit> search(String q, String category, String destination, Pageable pageable) {

        Query query = new Query();

        // 🔹 Recherche texte
        if (q != null && !q.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("codeProduit").regex(q, "i"),
                    Criteria.where("nomProduit").regex(q, "i")
            ));
        }

        // 🔹 Filtre catégorie
        if (category != null && !category.isBlank()) {
            query.addCriteria(Criteria.where("categorie").is(category));
        }

        // 🔹 Filtre destination
        if (destination != null && !destination.isBlank()) {
            query.addCriteria(Criteria.where("destination").is(destination));
        }

        // 🔹 Total avant pagination
        long total = mongoTemplate.count(query, Produit.class);

        // 🔹 Pagination et tri si besoin
        query.with(pageable);

        // 🔹 Récupération des produits avec images
        List<Produit> produits = mongoTemplate.find(query, Produit.class);

        return new PageImpl<>(produits, pageable, total);
    }


}
