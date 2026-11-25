package com.example.Pointage_Cleanic.services.evenements;

import com.example.Pointage_Cleanic.Enum.evenements.StatutEvenement;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.configurations.ExecuteurTransactionnel;
import com.example.Pointage_Cleanic.entities.evenements.EvenementConsomme;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.exception.TransitionInterditeException;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Critère 1b « OutboxIT » (décision D5) : un événement n'est écrit que si la transaction est
 * validée ; un consommateur rejoué n'a d'effet qu'une fois ; un échec répété passe
 * {@code EN_ERREUR} après 10 tentatives, à délais croissants ; un bail expiré rend l'événement
 * de nouveau réclamable.
 *
 * <p>Les consommateurs de test ne sont pas des beans (le contexte Spring partagé reste le même) :
 * le test construit son propre {@link DispatcherEvenements}, avec une horloge qu'il fait avancer.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class OutboxIT extends MongoTestContainer {

    private static final String EFFETS = "outbox_it_effets";
    private static final String TYPE = "TEST_OUTBOX";

    @Autowired private EvenementPublisher publisher;
    @Autowired private ExecuteurTransactionnel executeur;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private SupervisionEvenementsService supervision;

    private final HorlogeReglable horloge = new HorlogeReglable(
            Instant.now().plusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS)); // précision Mongo

    @BeforeEach
    void nettoyer() {
        mongoTemplate.remove(new Query(), EvenementDomaine.class);
        mongoTemplate.remove(new Query(), EvenementConsomme.class);
        if (!mongoTemplate.collectionExists(EFFETS)) {
            mongoTemplate.createCollection(EFFETS);
        }
        mongoTemplate.remove(new Query(), EFFETS);
    }

    @Test
    void evenement_ecrit_seulement_si_la_transaction_est_validee() {
        assertThatThrownBy(() -> executeur.executer(() -> {
            publisher.publier(TYPE, "Test", "agregat-1", "site-1", Map.of("cle", "valeur"));
            throw new IllegalStateException("panne après la publication");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(mongoTemplate.count(new Query(), EvenementDomaine.class)).isZero();

        EvenementDomaine publie = executeur.executer(() ->
                publisher.publier(TYPE, "Test", "agregat-2", "site-1", Map.of("cle", "valeur")));

        EvenementDomaine lu = mongoTemplate.findById(publie.getId(), EvenementDomaine.class);
        assertThat(lu).isNotNull();
        assertThat(lu.getStatut()).isEqualTo(StatutEvenement.A_TRAITER);
        assertThat(lu.getAgregatId()).isEqualTo("agregat-2");
        assertThat(lu.getPayload()).containsEntry("cle", "valeur");
        assertThat(lu.getCorrelationId()).isNotBlank();
        assertThat(lu.getSurvenuLe()).isNotNull();
    }

    @Test
    void publication_hors_transaction_refusee() {
        assertThatThrownBy(() -> publisher.publier(TYPE, "Test", "a", null, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hors transaction");
        assertThat(mongoTemplate.count(new Query(), EvenementDomaine.class)).isZero();
    }

    @Test
    void consommateur_rejoue_deux_fois_effet_une_seule_fois() {
        EvenementDomaine evt = publier("agregat-rejeu");
        DispatcherEvenements dispatcher = dispatcher(new ConsommateurEcrivant("ecrivant"));

        assertThat(dispatcher.traiterLot()).isEqualTo(1);
        assertThat(statut(evt)).isEqualTo(StatutEvenement.TRAITE);

        // Redistribution forcée (comme après un bail expiré) : le consommateur ne refait rien.
        mongoTemplate.updateFirst(parId(evt), new Update().set("statut", StatutEvenement.A_TRAITER)
                .set("prochainEssai", horloge.instant()), EvenementDomaine.class);
        assertThat(dispatcher.traiterLot()).isEqualTo(1);

        assertThat(mongoTemplate.count(new Query(), EFFETS)).isEqualTo(1);
        assertThat(mongoTemplate.count(new Query(), EvenementConsomme.class)).isEqualTo(1);
        assertThat(statut(evt)).isEqualTo(StatutEvenement.TRAITE);
    }

    @Test
    void un_consommateur_en_echec_ne_fait_pas_rejouer_les_autres() {
        EvenementDomaine evt = publier("agregat-mixte");
        AtomicInteger appels = new AtomicInteger();
        ConsommateurEvenement echoueUneFois = consommateur("echoue-une-fois", e -> {
            if (appels.incrementAndGet() == 1) {
                throw new IllegalStateException("indisponible");
            }
        });
        DispatcherEvenements dispatcher = dispatcher(new ConsommateurEcrivant("ecrivant"), echoueUneFois);

        dispatcher.traiterLot();
        EvenementDomaine apresEchec = lire(evt);
        assertThat(apresEchec.getStatut()).isEqualTo(StatutEvenement.A_TRAITER);
        assertThat(apresEchec.getTentatives()).isEqualTo(1);
        assertThat(apresEchec.getDerniereErreur()).contains("echoue-une-fois").contains("indisponible");

        horloge.avancer(Duration.ofMinutes(1));
        dispatcher.traiterLot();

        assertThat(statut(evt)).isEqualTo(StatutEvenement.TRAITE);
        assertThat(mongoTemplate.count(new Query(), EFFETS)).isEqualTo(1);
        assertThat(appels.get()).isEqualTo(2);
    }

    @Test
    void effets_d_un_consommateur_en_echec_annules_avec_sa_transaction() {
        EvenementDomaine evt = publier("agregat-annule");
        ConsommateurEvenement ecritPuisEchoue = consommateur("ecrit-puis-echoue", e -> {
            mongoTemplate.insert(new Document("evenementId", e.getId()), EFFETS);
            throw new IllegalStateException("panne après l'écriture");
        });

        dispatcher(ecritPuisEchoue).traiterLot();

        assertThat(mongoTemplate.count(new Query(), EFFETS)).isZero();
        assertThat(mongoTemplate.count(new Query(), EvenementConsomme.class)).isZero();
        assertThat(statut(evt)).isEqualTo(StatutEvenement.A_TRAITER);
    }

    @Test
    void echec_repete_en_erreur_apres_dix_tentatives_avec_delais_croissants() {
        EvenementDomaine evt = publier("agregat-echec");
        DispatcherEvenements dispatcher = dispatcher(consommateur("toujours-en-echec", e -> {
            throw new IllegalStateException("toujours en panne");
        }));

        List<Duration> delais = new ArrayList<>();
        for (int i = 1; i < DispatcherEvenements.TENTATIVES_MAX; i++) {
            assertThat(dispatcher.traiterLot()).isEqualTo(1);
            EvenementDomaine lu = lire(evt);
            assertThat(lu.getStatut()).isEqualTo(StatutEvenement.A_TRAITER);
            assertThat(lu.getTentatives()).isEqualTo(i);
            Duration delai = Duration.between(horloge.instant(), lu.getProchainEssai());
            delais.add(delai);
            // Pas de nouvel essai avant l'échéance.
            assertThat(dispatcher.traiterLot()).isZero();
            horloge.avancer(delai);
        }
        assertThat(dispatcher.traiterLot()).isEqualTo(1);

        EvenementDomaine enErreur = lire(evt);
        assertThat(enErreur.getStatut()).isEqualTo(StatutEvenement.EN_ERREUR);
        assertThat(enErreur.getTentatives()).isEqualTo(10);
        assertThat(enErreur.getDerniereErreur()).contains("toujours en panne");
        assertThat(delais).containsExactly(
                Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(4), Duration.ofMinutes(8),
                Duration.ofMinutes(16), Duration.ofMinutes(32), Duration.ofHours(1), Duration.ofHours(1),
                Duration.ofHours(1));

        // Plus jamais réclamé, même longtemps après.
        horloge.avancer(Duration.ofDays(1));
        assertThat(dispatcher.traiterLot()).isZero();

        // Relance manuelle (supervision) : de nouveau à traiter, compteur remis à zéro.
        EvenementDomaine relance = supervision.relancer(evt.getId());
        assertThat(relance.getStatut()).isEqualTo(StatutEvenement.A_TRAITER);
        assertThat(relance.getTentatives()).isZero();
        assertThatThrownBy(() -> supervision.relancer(evt.getId()))
                .isInstanceOf(TransitionInterditeException.class);
    }

    @Test
    void bail_expire_evenement_reclame_de_nouveau() {
        EvenementDomaine evt = publier("agregat-bail");
        // Une autre instance l'a réclamé puis s'est arrêtée : bail encore valable, puis expiré.
        mongoTemplate.updateFirst(parId(evt), new Update().set("statut", StatutEvenement.EN_COURS)
                .set("bailJusquA", horloge.instant().plus(Duration.ofMinutes(5))), EvenementDomaine.class);
        DispatcherEvenements dispatcher = dispatcher(new ConsommateurEcrivant("ecrivant"));

        assertThat(dispatcher.traiterLot()).isZero();

        horloge.avancer(Duration.ofMinutes(5));
        assertThat(dispatcher.traiterLot()).isEqualTo(1);
        assertThat(statut(evt)).isEqualTo(StatutEvenement.TRAITE);
        assertThat(mongoTemplate.count(new Query(), EFFETS)).isEqualTo(1);
    }

    @Test
    void consommateur_ne_recoit_que_ses_types() {
        EvenementDomaine evt = publier("agregat-type");
        AtomicInteger appels = new AtomicInteger();
        ConsommateurEvenement autreType = new ConsommateurEvenement() {
            @Override public String nom() { return "autre-type"; }
            @Override public Set<String> types() { return Set.of("AUTRE_TYPE"); }
            @Override public void traiter(EvenementDomaine e) { appels.incrementAndGet(); }
        };

        dispatcher(autreType).traiterLot();

        assertThat(appels.get()).isZero();
        assertThat(statut(evt)).isEqualTo(StatutEvenement.TRAITE);
    }

    @Test
    void delais_plafonnes_a_une_heure() {
        assertThat(DispatcherEvenements.delaiAvant(2)).isEqualTo(Duration.ofMinutes(1));
        assertThat(DispatcherEvenements.delaiAvant(7)).isEqualTo(Duration.ofMinutes(32));
        assertThat(DispatcherEvenements.delaiAvant(8)).isEqualTo(Duration.ofHours(1));
        assertThat(DispatcherEvenements.delaiAvant(60)).isEqualTo(Duration.ofHours(1));
    }

    // ---- outils ----------------------------------------------------------------------------

    private EvenementDomaine publier(String agregatId) {
        EvenementDomaine evt = executeur.executer(() ->
                publisher.publier(TYPE, "Test", agregatId, null, Map.of()));
        // L'horloge du test démarre après la publication : l'événement est réclamable tout de suite.
        mongoTemplate.updateFirst(parId(evt), new Update().set("prochainEssai", horloge.instant()),
                EvenementDomaine.class);
        return evt;
    }

    private DispatcherEvenements dispatcher(ConsommateurEvenement... consommateurs) {
        return new DispatcherEvenements(mongoTemplate, executeur, horloge, List.of(consommateurs));
    }

    private ConsommateurEvenement consommateur(String nom, java.util.function.Consumer<EvenementDomaine> action) {
        return new ConsommateurEvenement() {
            @Override public String nom() { return nom; }
            @Override public Set<String> types() { return Set.of(TYPE); }
            @Override public void traiter(EvenementDomaine e) { action.accept(e); }
        };
    }

    /** Consommateur transactionnel qui écrit un document par événement traité. */
    private class ConsommateurEcrivant implements ConsommateurEvenement {
        private final String nom;

        ConsommateurEcrivant(String nom) {
            this.nom = nom;
        }

        @Override public String nom() { return nom; }
        @Override public Set<String> types() { return Set.of(TYPE); }

        @Override
        public void traiter(EvenementDomaine e) {
            mongoTemplate.insert(new Document("evenementId", e.getId()).append("consommateur", nom), EFFETS);
        }
    }

    private static Query parId(EvenementDomaine evt) {
        return new Query(Criteria.where("_id").is(evt.getId()));
    }

    private EvenementDomaine lire(EvenementDomaine evt) {
        return mongoTemplate.findById(evt.getId(), EvenementDomaine.class);
    }

    private StatutEvenement statut(EvenementDomaine evt) {
        return lire(evt).getStatut();
    }

    /** Horloge que le test fait avancer. */
    private static final class HorlogeReglable extends Clock {
        private final AtomicReference<Instant> maintenant;

        HorlogeReglable(Instant depart) {
            this.maintenant = new AtomicReference<>(depart);
        }

        void avancer(Duration d) {
            maintenant.updateAndGet(i -> i.plus(d));
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant.get(); }
    }
}
