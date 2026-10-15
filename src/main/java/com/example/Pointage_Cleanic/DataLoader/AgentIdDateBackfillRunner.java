package com.example.Pointage_Cleanic.DataLoader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Rattrapage : date d'attribution du code de pointage ({@code agentId}) sur les dossiers
 * antérieurs à {@code dateCreationAgentId}.
 *
 * <p>Aucune date n'ayant jamais été stockée, la meilleure approximation est la date de
 * création du <b>dossier</b>, lue dans l'horodatage de son ObjectId. Elle est marquée
 * {@code dateCreationAgentIdEstimee = true} : le récapitulatif l'affiche comme
 * approximative. ⚠ Elle est fausse pour un code changé depuis la création du dossier — on
 * ne peut pas faire mieux, aucune trace du changement n'existe.
 *
 * <p>Idempotent : seuls les dossiers portant un code et pas encore de date sont visités.
 * Placé après {@link DossierAgentIdMigrationRunner} (950), qui renomme la clé du code.
 */
@Slf4j
@Component
@Order(960)
@RequiredArgsConstructor
public class AgentIdDateBackfillRunner implements CommandLineRunner {

    private static final String COLLECTION = "dossiers_employes";

    private final MongoTemplate mongoTemplate;
    /** Fuseau métier (Africa/Dakar) pour convertir l'instant de l'ObjectId. */
    private final Clock clock;

    @Override
    public void run(String... args) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("agentId").exists(true).ne(null),
                Criteria.where("dateCreationAgentId").exists(false)));
        query.fields().include("_id");

        List<Document> dossiers = mongoTemplate.find(query, Document.class, COLLECTION);
        int modifies = 0;
        for (Document dossier : dossiers) {
            if (!(dossier.get("_id") instanceof ObjectId id)) continue;
            LocalDateTime date = LocalDateTime.ofInstant(id.getDate().toInstant(), clock.getZone());
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(id)),
                    new Update().set("dateCreationAgentId", date).set("dateCreationAgentIdEstimee", true),
                    COLLECTION);
            modifies++;
        }
        if (modifies > 0) {
            log.info("Backfill dossiers_employes : date du code agent estimée sur {} dossier(s)", modifies);
        }
    }
}
