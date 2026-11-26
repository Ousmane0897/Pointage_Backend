package com.example.Pointage_Cleanic.migrations;

import com.example.Pointage_Cleanic.services.pieces.NumerotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Crée la collection {@code compteurs_pieces} (lot 1 / 1b, D4) avant tout trafic.
 *
 * <p>Pourquoi : le premier compteur d'une série est créé par un upsert <b>dans la transaction de
 * validation</b>. MongoDB 7 sait créer une collection dans une transaction, mais deux premières
 * validations simultanées se disputeraient alors la création. La collection existe donc dès le
 * démarrage. Idempotente : rien si elle existe déjà.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompteursPiecesMigration {

    private final MongoTemplate mongoTemplate;

    public void executer() {
        if (!mongoTemplate.collectionExists(NumerotationService.COLLECTION)) {
            mongoTemplate.createCollection(NumerotationService.COLLECTION);
            log.info("Collection {} créée", NumerotationService.COLLECTION);
        }
    }
}
