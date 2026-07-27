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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Génération atomique de références pour le module Stock v2.
 * Pattern findAndModify($inc, upsert, returnNew) — un document compteur par (préfixe, période).
 * Références produites :
 *  - journalière : {PREFIXE}-yyyyMMdd-NNN (ex: MVT-20260617-001, INV-20260617-002) ;
 *  - annuelle    : {PREFIXE}-yyyy-NNN    (ex: CH-2026-001) — séquence remise à 001 chaque année.
 */
@Service
@RequiredArgsConstructor
public class CompteurStockService {

    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter KEY_FORMAT_ANNEE = DateTimeFormatter.ofPattern("yyyy");
    private static final ZoneId ZONE = ZoneId.of("Africa/Dakar");

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

    /** Référence annuelle atomique : {PREFIXE}-yyyy-NNN (séquence remise à 001 chaque année). */
    public String genererReferenceAnnuelle(String prefixe) {
        String annee = LocalDate.now(ZONE).format(KEY_FORMAT_ANNEE);
        String key = prefixe + "-" + annee;

        Query query = new Query(Criteria.where("_id").is(key));
        Update update = new Update().inc("compteur", 1L);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);

        CompteurStock compteur = mongoTemplate.findAndModify(query, update, options, CompteurStock.class);
        long valeur = compteur == null ? 1L : compteur.getCompteur();
        return String.format("%s-%s-%03d", prefixe, annee, valeur);
    }

    /** Aperçu best-effort (SANS incrément) de la prochaine référence annuelle. */
    public String apercuReferenceAnnuelle(String prefixe) {
        String annee = LocalDate.now(ZONE).format(KEY_FORMAT_ANNEE);
        CompteurStock compteur = mongoTemplate.findById(prefixe + "-" + annee, CompteurStock.class);
        long prochain = (compteur == null ? 0L : compteur.getCompteur()) + 1;
        return String.format("%s-%s-%03d", prefixe, annee, prochain);
    }
}
