package com.example.Pointage_Cleanic.services.evenements;

import com.example.Pointage_Cleanic.Enum.evenements.StatutEvenement;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.TransitionInterditeException;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * Supervision de l'outbox (lot 1 / 1b, D5) : liste des événements par statut et relance
 * manuelle d'un événement {@code EN_ERREUR}. L'écran d'exploitation viendra plus tard (écart
 * consigné dans docs/refonte/1b-pieces.md) ; seules les routes existent.
 */
@Service
@RequiredArgsConstructor
public class SupervisionEvenementsService {

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public PageResponse<EvenementDomaine> lister(StatutEvenement statut, int page, int size) {
        int taille = Math.min(Math.max(size, 1), 200);
        int numero = Math.max(page, 0);
        Query query = new Query();
        if (statut != null) {
            query.addCriteria(Criteria.where("statut").is(statut));
        }
        long total = mongoTemplate.count(query, EvenementDomaine.class);
        query.with(PageRequest.of(numero, taille, Sort.by(Sort.Direction.DESC, "survenuLe")));
        List<EvenementDomaine> contenu = mongoTemplate.find(query, EvenementDomaine.class);
        return PageResponse.of(contenu, total);
    }

    /** Remet en file un événement EN_ERREUR, avec un compteur de tentatives remis à zéro. */
    public EvenementDomaine relancer(String id) {
        EvenementDomaine evt = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is(id).and("statut").is(StatutEvenement.EN_ERREUR)),
                new Update().set("statut", StatutEvenement.A_TRAITER)
                        .set("tentatives", 0)
                        .set("prochainEssai", clock.instant()),
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                EvenementDomaine.class);
        if (evt != null) {
            return evt;
        }
        if (mongoTemplate.findById(id, EvenementDomaine.class) == null) {
            throw new ResourceNotFoundException("Événement introuvable : " + id);
        }
        throw new TransitionInterditeException("Seul un événement EN_ERREUR peut être relancé.");
    }
}
