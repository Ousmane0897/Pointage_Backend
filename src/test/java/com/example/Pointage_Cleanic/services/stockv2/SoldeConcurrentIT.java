package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.configurations.ExecuteurTransactionnel;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.exception.ConflitConcurrentException;
import com.example.Pointage_Cleanic.exception.StockInsuffisantException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.example.Pointage_Cleanic.services.stockv2.StockBalanceService.ENTREPOT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critère 1a « SoldeConcurrentIT » (décision D1.5) : 50 sorties concurrentes sur un stock de 10,
 * le solde n'est jamais négatif et vaut exactement 10 − nombre de sorties réussies.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class SoldeConcurrentIT extends MongoTestContainer {

    private static final int SORTIES = 50;
    private static final String PRODUIT = "produit-concurrence";

    @Autowired private StockBalanceService balanceService;
    @Autowired private ExecuteurTransactionnel executeur;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void preparer() {
        mongoTemplate.remove(new Query(), StockParSite.class);
        balanceService.appliquerDelta(PRODUIT, ENTREPOT, 10);
    }

    /** Lance {@link #SORTIES} sorties d'une unité, toutes libérées en même temps ; renvoie les issues. */
    private List<Throwable> sortiesConcurrentes(Runnable sortie) throws Exception {
        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(SORTIES);
        try {
            List<CompletableFuture<Throwable>> issues = new ArrayList<>();
            for (int i = 0; i < SORTIES; i++) {
                issues.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        depart.await();
                        sortie.run();
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }, pool));
            }
            depart.countDown();
            List<Throwable> resultats = new ArrayList<>();
            for (CompletableFuture<Throwable> issue : issues) {
                resultats.add(issue.get(2, TimeUnit.MINUTES));
            }
            return resultats;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void debit_garde_hors_transaction_exactement_dix_sorties() throws Exception {
        List<Throwable> issues = sortiesConcurrentes(() -> balanceService.debiter(PRODUIT, ENTREPOT, 1));

        assertThat(issues).filteredOn(t -> t == null).as("sorties réussies").hasSize(10);
        assertThat(issues).filteredOn(t -> t != null).allSatisfy(t -> assertThat(t).isInstanceOf(StockInsuffisantException.class));
        assertThat(balanceService.quantite(PRODUIT, ENTREPOT)).isZero();
    }

    @Test
    void debit_en_transaction_jamais_negatif_et_coherent() throws Exception {
        List<Throwable> issues = sortiesConcurrentes(() ->
                executeur.executer(() -> balanceService.debiterAvecRepli(PRODUIT, ENTREPOT, 1)));

        long reussies = issues.stream().filter(t -> t == null).count();
        assertThat(issues).filteredOn(t -> t != null).allSatisfy(t ->
                assertThat(t).isInstanceOfAny(StockInsuffisantException.class, ConflitConcurrentException.class));
        double solde = balanceService.quantite(PRODUIT, ENTREPOT);
        assertThat(solde).as("jamais négatif").isGreaterThanOrEqualTo(0.0);
        assertThat(solde).as("10 − sorties réussies").isEqualTo(10.0 - reussies);
        assertThat(reussies).isPositive().isLessThanOrEqualTo(10);
    }
}
