package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.CompteurStock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Génération atomique de références journalières pour le module Stock v2.
 * Pattern findAndModify($inc, upsert, returnNew) — un document compteur par (préfixe, jour).
 * Référence produite : {PREFIXE}-yyyyMMdd-NNN (ex: MVT-20260617-001, INV-20260617-002).
 */
@Service
@RequiredArgsConstructor
public class CompteurStockService {

    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MongoTemplate mongoTemplate;

    public String genererReference(String prefixe) {
        return genererReference(prefixe, LocalDate.now());
    }

    public String genererReference(String prefixe, LocalDate date) {
        String jour = date.format(KEY_FORMAT);
        String key = prefixe + "-" + jour;

        Query query = new Query(Criteria.where("_id").is(key));
        Update update = new Update().inc("compteur", 1L);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);

        CompteurStock compteur = mongoTemplate.findAndModify(query, update, options, CompteurStock.class);
        long valeur = compteur == null ? 1L : compteur.getCompteur();
        return String.format("%s-%s-%03d", prefixe, jour, valeur);
    }
}
