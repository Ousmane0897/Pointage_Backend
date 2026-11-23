package com.example.Pointage_Cleanic.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Écrit et relit les compteurs {@link AppelRouteLegacy}. */
@Service
@RequiredArgsConstructor
public class SuiviRoutesLegacyService {

    static final int LONGUEUR_MAX = 200;

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    /** Incrémente le compteur du jour pour cette route (upsert atomique, sûr en multi-instance). */
    public void enregistrer(String route, int statut, String userAgent, String origine) {
        LocalDateTime maintenant = LocalDateTime.now(clock);
        String jour = maintenant.toLocalDate().toString();
        Update update = new Update()
                .inc("compteur", 1)
                .inc("statuts." + statut, 1)
                .set("dernierAppel", maintenant)
                .set("dernierUserAgent", tronquer(userAgent))
                .set("derniereOrigine", tronquer(origine))
                .setOnInsert("route", route)
                .setOnInsert("jour", jour)
                .setOnInsert("premierAppel", maintenant);
        mongoTemplate.upsert(new Query(Criteria.where("_id").is(route + "|" + jour)), update, AppelRouteLegacy.class);
    }

    /** Tous les compteurs, du jour le plus récent au plus ancien. */
    public List<AppelRouteLegacy> lister() {
        Query q = new Query().with(Sort.by(Sort.Order.desc("jour"), Sort.Order.asc("route")));
        return mongoTemplate.find(q, AppelRouteLegacy.class);
    }

    private static String tronquer(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        return valeur.length() <= LONGUEUR_MAX ? valeur : valeur.substring(0, LONGUEUR_MAX);
    }
}
