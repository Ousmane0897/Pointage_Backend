package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.repositories.stockv2.StockParSiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.example.Pointage_Cleanic.services.stockv2.StockBalanceService.ENTREPOT;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class StockEntrepotUniqueMigrationRunnerIT extends MongoTestContainer {

    private static final String PRODUIT = "produit-1";
    private static final String SITE_A = "siteA";
    private static final String SITE_B = "siteB";

    @Autowired private StockEntrepotUniqueMigrationRunner runner;
    @Autowired private StockParSiteRepository repository;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), StockParSite.class);
    }

    private void solde(String siteId, double quantite, LocalDateTime dateMaj) {
        repository.save(StockParSite.builder()
                .produitId(PRODUIT).siteId(siteId).quantite(quantite).dateMaj(dateMaj).build());
    }

    @Test
    void fusionne_les_soldes_site_dans_l_entrepot() {
        // Mongo stocke les dates à la milliseconde : tronquer pour comparer à l'identique.
        LocalDateTime ancien = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime recent = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.MILLIS);
        solde(ENTREPOT, 5, ancien);
        solde(SITE_A, 12, recent);
        solde(SITE_B, 3, ancien);

        runner.run();

        List<StockParSite> soldes = repository.findByProduitId(PRODUIT);
        assertThat(soldes).hasSize(1);
        assertThat(soldes.get(0).getSiteId()).isNull();
        assertThat(soldes.get(0).getQuantite()).isEqualTo(20.0);
        assertThat(soldes.get(0).getDateMaj()).isEqualTo(recent);
    }

    @Test
    void cree_le_solde_entrepot_s_il_est_absent() {
        solde(SITE_A, 7, LocalDateTime.now());

        runner.run();

        assertThat(repository.findByProduitIdAndSiteId(PRODUIT, SITE_A)).isEmpty();
        assertThat(repository.findByProduitIdAndSiteId(PRODUIT, ENTREPOT))
                .get().extracting(StockParSite::getQuantite).isEqualTo(7.0);
    }

    @Test
    void idempotent_deuxieme_passage_sans_effet() {
        solde(ENTREPOT, 5, LocalDateTime.now());
        solde(SITE_A, 12, LocalDateTime.now());

        runner.run();
        runner.run();

        List<StockParSite> soldes = repository.findByProduitId(PRODUIT);
        assertThat(soldes).hasSize(1);
        assertThat(soldes.get(0).getQuantite()).isEqualTo(17.0);
    }
}
