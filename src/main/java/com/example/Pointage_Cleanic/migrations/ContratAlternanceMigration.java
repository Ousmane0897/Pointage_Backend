package com.example.Pointage_Cleanic.migrations;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContratAlternanceMigration {

    private final MongoTemplate mongoTemplate;

    public void executer() {
        Query query = Query.query(Criteria.where("typeContrat").is("ALTERNANCE"));
        Update update = Update.update("typeContrat", "PRESTATION");
        UpdateResult result = mongoTemplate.updateMulti(query, update, "contrats");
        if (result.getModifiedCount() > 0) {
            log.info("Migration contrats : {} document(s) ALTERNANCE migrés vers PRESTATION",
                    result.getModifiedCount());
        }
    }
}