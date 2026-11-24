package com.example.Pointage_Cleanic.migrations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pose {@code version = 0} sur les documents existants des entités qui reçoivent {@code @Version}
 * en 1a (décision D1.4). Sans elle, Spring Data prendrait un document existant sans version pour
 * un nouveau document : son enregistrement tenterait un insert et échouerait (doublon d'_id).
 * Idempotente : ne touche que les documents sans champ {@code version}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VersionInitialeStockMigration {

    static final List<String> COLLECTIONS = List.of(
            "stockv2_bons_entree", "stockv2_bons_sortie", "stockv2_inventaires", "stockv2_produits");

    private final MongoTemplate mongoTemplate;

    public void executer() {
        for (String collection : COLLECTIONS) {
            long modifies = mongoTemplate.updateMulti(
                    Query.query(Criteria.where("version").exists(false)),
                    Update.update("version", 0L), collection).getModifiedCount();
            if (modifies > 0) {
                log.info("Version initiale posée sur {} document(s) de {}", modifies, collection);
            }
        }
    }
}
