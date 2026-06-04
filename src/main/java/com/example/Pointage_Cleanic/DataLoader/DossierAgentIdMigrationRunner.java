package com.example.Pointage_Cleanic.DataLoader;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Migration : l'entité DossierEmploye déclarait son code agent sous le nom de
 * champ "AgentId" (majuscule) — clé Mongo stockée telle quelle. Ce nom non
 * conventionnel empêchait MapStruct de mapper la propriété vers le builder
 * Lombok (toEntity) : tout dossier créé via POST perdait silencieusement son
 * agentId, pourtant clé de résolution du pointage mobile.
 *
 * Le champ Java est renommé "agentId" ; cette migration renomme la clé Mongo
 * des documents existants. Idempotent : $exists ne matche que les documents
 * portant encore l'ancienne clé.
 */
@Slf4j
@Component
@Order(950)
@RequiredArgsConstructor
public class DossierAgentIdMigrationRunner implements CommandLineRunner {

    private static final String COLLECTION = "dossiers_employes";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        Query query = Query.query(Criteria.where("AgentId").exists(true));
        Update update = new Update().rename("AgentId", "agentId");
        UpdateResult result = mongoTemplate.updateMulti(query, update, COLLECTION);
        if (result.getModifiedCount() > 0) {
            log.info("Migration dossiers_employes : clé AgentId renommée en agentId sur {} document(s)",
                    result.getModifiedCount());
        }
    }
}