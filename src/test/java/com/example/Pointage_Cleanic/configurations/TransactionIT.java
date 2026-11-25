package com.example.Pointage_Cleanic.configurations;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.exception.ConflitConcurrentException;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Critère 1a « TransactionIT » (décision D1) : une transaction qui échoue n'écrit rien, et
 * {@link ExecuteurTransactionnel} rejoue les conflits d'écriture sans perdre de mise à jour.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class TransactionIT extends MongoTestContainer {

    private static final String COLLECTION = "essai_transactions";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ExecuteurTransactionnel executeur;

    @BeforeEach
    void preparer() {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            mongoTemplate.createCollection(COLLECTION);
        }
        mongoTemplate.remove(new Query(), COLLECTION);
    }

    private long nombre() {
        return mongoTemplate.getCollection(COLLECTION).countDocuments();
    }

    @Test
    void transaction_en_echec_n_ecrit_aucun_des_deux_documents() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(statut -> {
            mongoTemplate.insert(new Document("n", 1), COLLECTION);
            mongoTemplate.insert(new Document("n", 2), COLLECTION);
            throw new IllegalStateException("échec après deux écritures");
        })).hasMessage("échec après deux écritures");

        assertThat(nombre()).isZero();
    }

    @Test
    void executeur_en_echec_n_ecrit_rien_et_ne_rejoue_pas_une_erreur_metier() {
        AtomicInteger essais = new AtomicInteger();
        assertThatThrownBy(() -> executeur.executer(() -> {
            essais.incrementAndGet();
            mongoTemplate.insert(new Document("n", 1), COLLECTION);
            mongoTemplate.insert(new Document("n", 2), COLLECTION);
            throw new IllegalArgumentException("refus métier");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(nombre()).isZero();
        assertThat(essais).hasValue(1);
    }

    @Test
    void executeur_valide_les_deux_documents() {
        executeur.executer(() -> {
            mongoTemplate.insert(new Document("n", 1), COLLECTION);
            mongoTemplate.insert(new Document("n", 2), COLLECTION);
        });
        assertThat(nombre()).isEqualTo(2);
    }

    // apresCommit a été retiré en 1b : les effets externes partent de l'outbox (OutboxIT).

    /**
     * Deux transactions lisent le même compteur (0), puis écrivent toutes deux « lu + 1 ».
     * Sans rejeu, la seconde échouerait (WriteConflict) ou écraserait la première. Avec
     * l'exécuteur, la perdante est rejouée, relit 1 et écrit 2 : aucune mise à jour perdue.
     */
    @Test
    void conflit_d_ecriture_rejoue_sans_perdre_de_mise_a_jour() throws Exception {
        mongoTemplate.insert(new Document("_id", "compteur").append("valeur", 0), COLLECTION);
        CyclicBarrier lectureFaite = new CyclicBarrier(2);
        AtomicInteger essais = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Runnable incrementer = () -> executeur.executer(() -> {
                boolean premierEssai = essais.incrementAndGet() <= 2;
                Document lu = mongoTemplate.findById("compteur", Document.class, COLLECTION);
                if (premierEssai) {
                    try {
                        lectureFaite.await(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is("compteur")),
                        Update.update("valeur", lu.getInteger("valeur") + 1), COLLECTION);
            });
            List<CompletableFuture<Void>> taches = List.of(
                    CompletableFuture.runAsync(incrementer, pool),
                    CompletableFuture.runAsync(incrementer, pool));
            CompletableFuture.allOf(taches.toArray(CompletableFuture[]::new)).get(2, TimeUnit.MINUTES);
        } finally {
            pool.shutdownNow();
        }

        Document fin = mongoTemplate.findById("compteur", Document.class, COLLECTION);
        assertThat(fin.getInteger("valeur")).isEqualTo(2);
        assertThat(essais.get()).as("au moins un rejeu").isGreaterThan(2);
    }

    @Test
    void conflit_persistant_donne_conflit_concurrent_sans_ecriture() {
        mongoTemplate.insert(new Document("_id", "verrouille").append("valeur", 0), COLLECTION);
        AtomicInteger essais = new AtomicInteger();
        // Une transaction extérieure garde le document verrouillé pendant tous les essais.
        transactionTemplate.executeWithoutResult(exterieure -> {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is("verrouille")),
                    Update.update("valeur", 100), COLLECTION);
            CompletableFuture<Void> concurrente = CompletableFuture.runAsync(() -> executeur.executer(() -> {
                essais.incrementAndGet();
                mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is("verrouille")),
                        Update.update("valeur", 1), COLLECTION);
            }));
            assertThatThrownBy(() -> concurrente.get(1, TimeUnit.MINUTES))
                    .hasCauseInstanceOf(ConflitConcurrentException.class);
        });

        assertThat(essais).hasValue(ExecuteurTransactionnel.ESSAIS_MAX);
        assertThat(mongoTemplate.findById("verrouille", Document.class, COLLECTION).getInteger("valeur"))
                .isEqualTo(100);
    }
}
