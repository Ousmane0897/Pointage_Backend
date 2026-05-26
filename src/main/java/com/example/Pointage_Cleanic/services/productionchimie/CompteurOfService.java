package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.entities.productionchimie.CompteurOf;
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
public class CompteurOfService {

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

        CompteurOf compteur = mongoTemplate.findAndModify(query, update, options, CompteurOf.class);
        long valeur = compteur == null ? 1L : compteur.getCompteur();
        return String.format("OF-%s-%03d", key, valeur);
    }
}