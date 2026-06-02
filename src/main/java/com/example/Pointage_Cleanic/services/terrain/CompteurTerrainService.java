package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.entities.terrain.CompteurTerrain;
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
 * Génération atomique de séquences quotidiennes via {@code findAndModify($inc, upsert, returnNew)}.
 * Même pattern que {@code CompteurLotService} (Production Chimie).
 */
@Service
@RequiredArgsConstructor
public class CompteurTerrainService {

    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MongoTemplate mongoTemplate;

    public String genererNumeroIntervention(LocalDate date) {
        return generer("INT", date);
    }

    public String genererNumeroApplication(LocalDate date) {
        return generer("PHYTO", date);
    }

    private String generer(String prefixe, LocalDate date) {
        String jour = date.format(KEY_FORMAT);
        String key = prefixe + "-" + jour;

        Query query = new Query(Criteria.where("_id").is(key));
        Update update = new Update().inc("compteur", 1L);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);

        CompteurTerrain compteur = mongoTemplate.findAndModify(query, update, options, CompteurTerrain.class);
        long valeur = compteur == null ? 1L : compteur.getCompteur();
        return String.format("%s-%s-%03d", prefixe, jour, valeur);
    }
}