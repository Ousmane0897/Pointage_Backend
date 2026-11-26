package com.example.Pointage_Cleanic.services.evenements;

import com.example.Pointage_Cleanic.Enum.evenements.StatutEvenement;
import com.example.Pointage_Cleanic.configurations.CorrelationId;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publication d'un événement de domaine dans l'outbox (lot 1 / 1b, décision D5).
 *
 * <p>L'événement est écrit dans la <b>transaction courante</b> : il n'existe que si la pièce a
 * été enregistrée, et il disparaît avec elle si la transaction est annulée. Un appel hors
 * transaction est refusé : c'est la garantie de l'outbox.
 */
@Service
@RequiredArgsConstructor
public class EvenementPublisher {

    public static final String COLLECTION = "evenements_domaine";

    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;

    public EvenementDomaine publier(String type, String agregatType, String agregatId, String siteId,
                                    Map<String, Object> payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Événement publié hors transaction : il doit être écrit avec la pièce qui le produit");
        }
        Instant maintenant = clock.instant();
        EvenementDomaine evt = EvenementDomaine.builder()
                .type(type)
                .version(1)
                .agregatType(agregatType)
                .agregatId(agregatId)
                .siteId(siteId)
                .survenuLe(maintenant)
                .auteurCompteId(currentUserProvider.currentUserId())
                .auteurNom(currentUserProvider.currentUserNom())
                .correlationId(CorrelationId.courant())
                .payload(payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload))
                .statut(StatutEvenement.A_TRAITER)
                .tentatives(0)
                .prochainEssai(maintenant)
                .build();
        return mongoTemplate.insert(evt, COLLECTION);
    }
}
