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

@Repository
public class ProduitRepositoryImpl
        implements ProduitRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public ProduitRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Produit> search(
            String q,
            String category,
            String destination,
            Pageable pageable
    ) {

        Query query = new Query();

        if (q != null && !q.isBlank()) {
            query.addCriteria(
                    new Criteria().orOperator(
                            Criteria.where("codeProduit").regex(q, "i"),
                            Criteria.where("nom").regex(q, "i")
                    )
            );
        }

        if (category != null && !category.isBlank()) {
            query.addCriteria(
                    Criteria.where("category").is(category)
            );
        }

        if (destination != null && !destination.isBlank()) {
            query.addCriteria(
                    Criteria.where("destination").is(destination)
            );
        }

        long total = mongoTemplate.count(query, Produit.class);

        query.with(pageable);

        List<Produit> produits =
                mongoTemplate.find(query, Produit.class);

        return new PageImpl<>(produits, pageable, total);
    }
}
