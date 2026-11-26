package com.example.Pointage_Cleanic.migrations;

import com.example.Pointage_Cleanic.PointageCleanicApplication;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Jeu de départ des essais de bout en bout (profil {@code e2e}, ChangeUnits 9101/9102) : créé sur
 * une base d'essai, refusé sur toute autre base, absent hors profil.
 */
class E2eSeedIT {

    private static MongoClient client;

    @BeforeAll
    static void connecter() {
        client = MongoClients.create(MongoTestContainer.conteneur().getReplicaSetUrl());
    }

    @AfterAll
    static void fermer() {
        client.close();
    }

    private static ConfigurableApplicationContext demarrer(String base, String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.data.mongodb.uri=" + MongoTestContainer.conteneur().getReplicaSetUrl(base),
                "--server.port=0",
                "--spring.mail.host=localhost"));
        args.addAll(List.of(extra));
        return new SpringApplicationBuilder(PointageCleanicApplication.class).run(args.toArray(String[]::new));
    }

    private static long executions(MongoDatabase db, String changeId) {
        return db.getCollection("mongockChangeLog")
                .countDocuments(new Document("changeId", changeId).append("state", "EXECUTED"));
    }

    @Test
    void profil_e2e_cree_comptes_articles_et_stock_initial() {
        String base = "cleanic_e2e_" + UUID.randomUUID().toString().substring(0, 8);
        String mdpSuperadmin = UUID.randomUUID().toString();
        String mdpStock = UUID.randomUUID().toString();
        MongoDatabase db = client.getDatabase(base);

        try (ConfigurableApplicationContext contexte = demarrer(base, "--spring.profiles.active=e2e",
                "--E2E_SUPERADMIN_PASSWORD=" + mdpSuperadmin, "--E2E_STOCK_PASSWORD=" + mdpStock)) {
            PasswordEncoder encodeur = contexte.getBean(PasswordEncoder.class);

            assertThat(executions(db, "9101-e2e-comptes")).isEqualTo(1);
            assertThat(executions(db, "9102-e2e-stock")).isEqualTo(1);

            Document superadmin = db.getCollection("login")
                    .find(new Document("email", E2eComptesSeed.EMAIL_SUPERADMIN)).first();
            assertThat(superadmin).isNotNull();
            assertThat(superadmin.getString("role")).isEqualTo("SUPERADMIN");
            assertThat(superadmin.getBoolean("mustChangePassword")).isFalse();
            assertThat(encodeur.matches(mdpSuperadmin, superadmin.getString("password"))).isTrue();

            Document loginStock = db.getCollection("login")
                    .find(new Document("email", E2eComptesSeed.EMAIL_STOCK)).first();
            assertThat(encodeur.matches(mdpStock, loginStock.getString("password"))).isTrue();

            Document stock = db.getCollection("utilisateur")
                    .find(new Document("email", E2eComptesSeed.EMAIL_STOCK)).first();
            assertThat(stock.getBoolean("active")).isTrue();
            Document modules = stock.get("modulesAutorises", Document.class);
            assertThat(modules.getBoolean("dashboard")).isFalse();
            assertThat(modules.get("rh")).as("aucun droit RH").isNull();
            Document drapeauxStock = modules.get("stock", Document.class);
            assertThat(drapeauxStock.getBoolean("bonsEntree")).isTrue();
            assertThat(drapeauxStock.getBoolean("inventaires")).isTrue();
            assertThat(drapeauxStock.values()).allMatch(Boolean.TRUE::equals);
        }

        assertThat(db.getCollection("sites_clients").countDocuments(new Document("code", E2eStockSeed.CODE_SITE)))
                .isEqualTo(1);
        assertThat(db.getCollection("stockv2_produits").countDocuments())
                .isEqualTo(E2eStockSeed.ARTICLES.size());
        Document sortie = db.getCollection("stockv2_produits").find(new Document("code", "E2E-SORTIE")).first();
        Document solde = db.getCollection("stockv2_etats_stock")
                .find(new Document("produitId", sortie.getObjectId("_id").toHexString())).first();
        assertThat(solde).as("stock initial dans l'entrepôt unique").isNotNull();
        assertThat(solde.get("siteId")).isNull();
        assertThat(solde.getDouble("quantite")).isEqualTo(50.0);
    }

    @Test
    void profil_e2e_refuse_une_base_qui_n_est_pas_une_base_d_essai() {
        String base = "cleanic_" + UUID.randomUUID().toString().substring(0, 8);

        assertThatThrownBy(() -> demarrer(base, "--spring.profiles.active=e2e",
                "--E2E_SUPERADMIN_PASSWORD=x", "--E2E_STOCK_PASSWORD=y").close())
                .hasStackTraceContaining("n'est pas une base d'essai");
        assertThat(client.getDatabase(base).getCollection("login").countDocuments()).isZero();
    }

    @Test
    void profil_e2e_sans_mot_de_passe_refuse_de_demarrer() {
        String base = "cleanic_e2e_" + UUID.randomUUID().toString().substring(0, 8);

        assertThatThrownBy(() -> demarrer(base, "--spring.profiles.active=e2e",
                "--E2E_SUPERADMIN_PASSWORD=", "--E2E_STOCK_PASSWORD=").close())
                .hasStackTraceContaining("E2E_SUPERADMIN_PASSWORD");
    }

    @Test
    void hors_profil_e2e_aucun_compte_d_essai() {
        String base = "migr_" + UUID.randomUUID().toString().substring(0, 8);
        MongoDatabase db = client.getDatabase(base);

        demarrer(base).close();

        assertThat(executions(db, "9101-e2e-comptes")).isZero();
        assertThat(db.getCollection("login").countDocuments(new Document("email", E2eComptesSeed.EMAIL_SUPERADMIN)))
                .isZero();
    }
}
