package com.example.Pointage_Cleanic.services.evenements;

import com.example.Pointage_Cleanic.Enum.evenements.StatutEvenement;
import com.example.Pointage_Cleanic.configurations.CorrelationId;
import com.example.Pointage_Cleanic.configurations.ExecuteurTransactionnel;
import com.example.Pointage_Cleanic.entities.evenements.EvenementConsomme;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Remet les événements de l'outbox à leurs consommateurs (lot 1 / 1b, décision D5).
 *
 * <ul>
 *   <li><b>Réclamation</b> : un événement à traiter (ou dont le bail a expiré) passe
 *       {@code EN_COURS} par un {@code findAndModify} atomique, avec un bail de
 *       {@link #BAIL} : deux instances ne prennent jamais le même événement, sans ShedLock.</li>
 *   <li><b>Idempotence</b> : chaque consommateur ne traite un événement qu'une fois
 *       ({@code evenements_consommes}, index unique) ; un consommateur transactionnel écrit cette
 *       trace dans la même transaction que ses effets.</li>
 *   <li><b>Échec</b> : nouvel essai après un délai exponentiel (1 min, 2, 4… plafonné à 1 h) ;
 *       après {@link #TENTATIVES_MAX} tentatives, {@code EN_ERREUR} (supervision, relance
 *       manuelle). Les consommateurs déjà passés ne sont pas rejoués.</li>
 * </ul>
 *
 * <p>Appelé toutes les 2 s par {@link PlanificationDispatcher} (désactivée dans les tests, qui
 * appellent {@link #traiterLot()}).
 */
@Slf4j
@Service
public class DispatcherEvenements {

    public static final int TENTATIVES_MAX = 10;
    static final Duration BAIL = Duration.ofMinutes(5);
    static final Duration DELAI_INITIAL = Duration.ofMinutes(1);
    static final Duration DELAI_MAX = Duration.ofHours(1);
    static final int TAILLE_LOT = 50;
    private static final int LONGUEUR_ERREUR_MAX = 500;

    private final MongoTemplate mongoTemplate;
    private final ExecuteurTransactionnel executeur;
    private final Clock clock;
    private final List<ConsommateurEvenement> consommateurs;

    @Autowired
    public DispatcherEvenements(MongoTemplate mongoTemplate, ExecuteurTransactionnel executeur, Clock clock,
                                ObjectProvider<ConsommateurEvenement> consommateurs) {
        this(mongoTemplate, executeur, clock, consommateurs.orderedStream().toList());
    }

    /** Constructeur direct (tests : consommateurs et horloge choisis). */
    public DispatcherEvenements(MongoTemplate mongoTemplate, ExecuteurTransactionnel executeur, Clock clock,
                                List<ConsommateurEvenement> consommateurs) {
        this.mongoTemplate = mongoTemplate;
        this.executeur = executeur;
        this.clock = clock;
        this.consommateurs = List.copyOf(consommateurs);
    }

    /** Traite au plus {@value #TAILLE_LOT} événements réclamables. @return le nombre réclamé. */
    public int traiterLot() {
        int n = 0;
        while (n < TAILLE_LOT) {
            EvenementDomaine evt = reclamer();
            if (evt == null) {
                break;
            }
            n++;
            CorrelationId.sous(evt.getCorrelationId(), () -> {
                traiter(evt);
                return null;
            });
        }
        return n;
    }

    EvenementDomaine reclamer() {
        Instant maintenant = clock.instant();
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("statut").is(StatutEvenement.A_TRAITER).and("prochainEssai").lte(maintenant),
                Criteria.where("statut").is(StatutEvenement.EN_COURS).and("bailJusquA").lte(maintenant)))
                .with(Sort.by("survenuLe"));
        Update update = new Update()
                .set("statut", StatutEvenement.EN_COURS)
                .set("bailJusquA", maintenant.plus(BAIL));
        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true),
                EvenementDomaine.class);
    }

    private void traiter(EvenementDomaine evt) {
        for (ConsommateurEvenement c : consommateurs) {
            if (!c.types().contains(evt.getType())) {
                continue;
            }
            try {
                consommer(c, evt);
            } catch (RuntimeException e) {
                echec(evt, c, e);
                return;
            }
        }
        mongoTemplate.updateFirst(
                enCours(evt),
                new Update().set("statut", StatutEvenement.TRAITE)
                        .set("traiteLe", clock.instant())
                        .unset("bailJusquA")
                        .unset("prochainEssai"),
                EvenementDomaine.class);
    }

    private void consommer(ConsommateurEvenement c, EvenementDomaine evt) {
        if (c.effetsExternes()) {
            if (dejaConsomme(c, evt)) {
                return;
            }
            c.traiter(evt);
            marquerConsomme(c, evt);
            return;
        }
        try {
            executeur.executer(() -> {
                if (dejaConsomme(c, evt)) {
                    return;
                }
                c.traiter(evt);
                marquerConsomme(c, evt);
            });
        } catch (DuplicateKeyException dejaFait) {
            // Une autre instance l'a consommé entre-temps : ses effets font foi, les nôtres sont annulés.
            log.debug("Événement {} déjà consommé par {}", evt.getId(), c.nom());
        }
    }

    private boolean dejaConsomme(ConsommateurEvenement c, EvenementDomaine evt) {
        return mongoTemplate.exists(new Query(Criteria.where("consommateur").is(c.nom())
                .and("evenementId").is(evt.getId())), EvenementConsomme.class);
    }

    private void marquerConsomme(ConsommateurEvenement c, EvenementDomaine evt) {
        try {
            mongoTemplate.insert(EvenementConsomme.builder()
                    .consommateur(c.nom())
                    .evenementId(evt.getId())
                    .le(clock.instant())
                    .build());
        } catch (DuplicateKeyException e) {
            if (c.effetsExternes()) {
                return; // hors transaction : la trace existe déjà, rien à défaire
            }
            throw e;
        }
    }

    private void echec(EvenementDomaine evt, ConsommateurEvenement c, RuntimeException e) {
        int tentatives = evt.getTentatives() + 1;
        String erreur = tronquer(c.nom() + " : " + e.getClass().getSimpleName() + " : " + e.getMessage());
        Update update = new Update()
                .set("tentatives", tentatives)
                .set("derniereErreur", erreur)
                .unset("bailJusquA");
        if (tentatives >= TENTATIVES_MAX) {
            update.set("statut", StatutEvenement.EN_ERREUR).unset("prochainEssai");
            log.error("Événement {} ({}) EN_ERREUR après {} tentatives : {}",
                    evt.getId(), evt.getType(), tentatives, erreur);
        } else {
            update.set("statut", StatutEvenement.A_TRAITER)
                    .set("prochainEssai", clock.instant().plus(delaiAvant(tentatives + 1)));
            log.warn("Événement {} ({}) en échec, tentative {}/{} : {}",
                    evt.getId(), evt.getType(), tentatives, TENTATIVES_MAX, erreur);
        }
        mongoTemplate.updateFirst(enCours(evt), update, EvenementDomaine.class);
    }

    /** Délai avant la tentative {@code n} (n ≥ 2) : 1 min, 2 min, 4 min… plafonné à 1 h. */
    static Duration delaiAvant(int tentative) {
        int puissance = Math.max(0, tentative - 2);
        if (puissance >= 6) {
            return DELAI_MAX;
        }
        Duration d = DELAI_INITIAL.multipliedBy(1L << puissance);
        return d.compareTo(DELAI_MAX) > 0 ? DELAI_MAX : d;
    }

    private static Query enCours(EvenementDomaine evt) {
        return new Query(Criteria.where("_id").is(evt.getId()).and("statut").is(StatutEvenement.EN_COURS));
    }

    private static String tronquer(String s) {
        return s.length() <= LONGUEUR_ERREUR_MAX ? s : s.substring(0, LONGUEUR_ERREUR_MAX);
    }
}
