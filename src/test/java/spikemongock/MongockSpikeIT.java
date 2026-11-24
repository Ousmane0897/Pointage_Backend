package spikemongock;

import com.example.Pointage_Cleanic.PointageCleanicApplication;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spike Mongock (lot 1, sous-branche 1a, décision D2) : Mongock 5.5.1 avec Spring Boot 3.3.6,
 * Spring Data MongoDB 4.3.6 et les transactions. Chaque scénario démarre une vraie application
 * sur une base dédiée du conteneur partagé (replica set). Compte rendu :
 * {@code docs/refonte/1a-socle-technique.md} §1.
 */
class MongockSpikeIT {

    private static MongoClient client;

    @BeforeAll
    static void connecter() {
        client = MongoClients.create(MongoTestContainer.conteneur().getReplicaSetUrl());
    }

    @AfterAll
    static void fermer() {
        client.close();
    }

    @BeforeEach
    void reinitialiser() {
        SpikeEtat.reinitialiser();
    }

    private static String nouvelleBase() {
        return "spike_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Les réglages passent en arguments de ligne de commande, la source la plus prioritaire :
     * en « propriétés par défaut », l'URI d'{@code application.yml} (localhost) l'emporterait.
     */
    private static ConfigurableApplicationContext demarrer(String base, String paquet) {
        ConfigurableApplicationContext contexte =
                new SpringApplicationBuilder(PointageCleanicApplication.class, SpikeConfiguration.class)
                        .run(
                                "--spring.data.mongodb.uri=" + MongoTestContainer.conteneur().getReplicaSetUrl(base),
                                "--server.port=0",
                                "--spring.mail.host=localhost",
                                "--mongock.enabled=true",
                                "--mongock.migration-scan-package=" + paquet,
                                "--mongock.runner-type=initializingbean",
                                "--mongock.transactional=true");
        String baseEffective = contexte.getBean(MongoTemplate.class).getDb().getName();
        if (!base.equals(baseEffective)) {
            contexte.close();
            throw new IllegalStateException("le spike vise la base " + baseEffective + " au lieu de " + base);
        }
        return contexte;
    }

    private static MongoDatabase base(String nom) {
        return client.getDatabase(nom);
    }

    @Test
    void execute_avant_le_trafic_avec_registre_et_verrou() {
        String nom = nouvelleBase();
        try (ConfigurableApplicationContext ignored = demarrer(nom, "spikemongock.ok")) {
            assertThat(SpikeEtat.executions.get()).isEqualTo(1);
            assertThat(SpikeEtat.pretePendantMigration)
                    .as("la ChangeUnit tourne avant ApplicationReadyEvent")
                    .isFalse();
            assertThat(base(nom).getCollection("spike_cible").countDocuments()).isEqualTo(2);
            List<String> collections = base(nom).listCollectionNames().into(new ArrayList<>());
            assertThat(collections).contains("mongockChangeLog", "mongockLock");
            Document entree = base(nom).getCollection("mongockChangeLog")
                    .find(new Document("changeId", "spike-s001-insertion")).first();
            assertThat(entree).isNotNull();
            assertThat(entree.getString("state")).isEqualTo("EXECUTED");
            List<String> index = base(nom).getCollection("spike_cible").listIndexes()
                    .map(d -> d.getString("name")).into(new ArrayList<>());
            assertThat(index).as("index créé par une ChangeUnit hors transaction").contains("cle_unique");
        }
    }

    @Test
    void une_change_unit_appliquee_n_est_pas_rejouee() {
        String nom = nouvelleBase();
        demarrer(nom, "spikemongock.ok").close();
        demarrer(nom, "spikemongock.ok").close();
        assertThat(SpikeEtat.executions.get()).isEqualTo(1);
        assertThat(base(nom).getCollection("spike_cible").countDocuments()).isEqualTo(2);
    }

    @Test
    void echec_a_mi_parcours_n_ecrit_rien_et_refuse_le_demarrage() {
        String nom = nouvelleBase();
        assertThatThrownBy(() -> demarrer(nom, "spikemongock.echec").close())
                .hasStackTraceContaining("échec volontaire à mi-parcours");
        assertThat(base(nom).getCollection("spike_echec").countDocuments())
                .as("transaction annulée : aucun des deux documents")
                .isZero();
        // L'entrée FAILED est écrite dans la transaction annulée : le registre ne garde aucune
        // trace de l'échec (seuls les journaux la gardent), et la ChangeUnit sera retentée.
        assertThat(base(nom).getCollection("mongockChangeLog")
                .countDocuments(new Document("changeId", "spike-e001-echec"))).isZero();
    }

    /**
     * La seconde instance démarre pendant que la première exécute sa ChangeUnit (verrou tenu).
     * Elle attend le verrou puis constate que tout est appliqué. Le départ est décalé exprès :
     * deux SpringApplication lancées au même instant dans une JVM se disputent l'initialisation
     * de logback (ConcurrentModificationException), ce qui n'a rien à voir avec Mongock.
     */
    @Test
    void deux_demarrages_simultanes_n_executent_qu_une_fois() throws Exception {
        String nom = nouvelleBase();
        SpikeEtat.pauseMs = 5_000;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> premiere =
                    CompletableFuture.runAsync(() -> demarrer(nom, "spikemongock.ok").close(), pool);
            assertThat(SpikeEtat.migrationCommencee.await(120, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Void> seconde =
                    CompletableFuture.runAsync(() -> demarrer(nom, "spikemongock.ok").close(), pool);
            CompletableFuture.allOf(premiere, seconde).get(5, TimeUnit.MINUTES);
        } finally {
            pool.shutdownNow();
        }
        assertThat(SpikeEtat.executions.get()).isEqualTo(1);
        assertThat(base(nom).getCollection("spike_cible").countDocuments()).isEqualTo(2);
    }

    /**
     * Constat : Mongock lit le registre une seule fois, avant la première ChangeUnit. Une
     * « baseline » qui y inscrit d'autres ChangeUnits comme appliquées n'empêche pas leur
     * exécution. Les ChangeUnits issues des runners gardent donc leur propre garde d'idempotence.
     */
    @Test
    void une_baseline_ecrite_dans_le_registre_n_empeche_pas_l_execution() {
        String nom = nouvelleBase();
        demarrer(nom, "spikemongock.baseline").close();
        assertThat(base(nom).getCollection("spike_baseline").countDocuments()).isEqualTo(1);
    }
}
