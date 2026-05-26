package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.entities.productionchimie.CompteurLot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class CompteurLotService {

    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MongoTemplate mongoTemplate;

    public String genererNumero() {
        return genererNumero(LocalDate.now());
    }

    public String genererNumero(LocalDate date) {
        String key = date.format(KEY_FORMAT);

        Query query = new Query(Criteria.where("_id").is(key));
        Update update = new Update().inc("compteur", 1L);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);

        CompteurLot compteur = mongoTemplate.findAndModify(query, update, options, CompteurLot.class);
        long valeur = compteur == null ? 1L : compteur.getCompteur();
        return String.format("%s-%03d", key, valeur);
    }
}