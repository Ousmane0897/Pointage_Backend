package com.example.Pointage_Cleanic.migrations.index;

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
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critère 1a « IndexesAttendusIT » (décision D3) : sur une base vide, les migrations créent
 * exactement les index d'{@link IndexesAttendus} — aucun ne manque, aucun n'est en trop, chacun
 * avec sa définition (clés, unicité, filtre partiel).
 */
class IndexesAttendusIT {

    /** Collections techniques, hors périmètre de la liste. */
    private static final Set<String> HORS_LISTE = Set.of("mongockChangeLog", "mongockLock", "migration_rapports");

    private static MongoClient client;

    @BeforeAll
    static void connecter() {
        client = MongoClients.create(MongoTestContainer.conteneur().getReplicaSetUrl());
    }

    @AfterAll
    static void fermer() {
        client.close();
    }

    @Test
    void base_vide_contient_exactement_les_index_attendus() {
        String base = "index_" + UUID.randomUUID().toString().substring(0, 8);
        try (ConfigurableApplicationContext contexte = new SpringApplicationBuilder(PointageCleanicApplication.class)
                .run("--spring.data.mongodb.uri=" + MongoTestContainer.conteneur().getReplicaSetUrl(base),
                        "--server.port=0", "--spring.mail.host=localhost")) {
            assertThat(contexte.getBean(MongoTemplate.class).getDb().getName()).isEqualTo(base);
        }
        MongoDatabase db = client.getDatabase(base);

        Map<String, List<IndexAttendu>> attendusParCollection = IndexesAttendus.tous().stream()
                .collect(Collectors.groupingBy(IndexAttendu::collection, TreeMap::new, Collectors.toList()));

        List<String> ecarts = new ArrayList<>();
        // Chaque index attendu existe, avec sa définition.
        attendusParCollection.forEach((collection, attendus) -> {
            List<Document> enBase = db.getCollection(collection).listIndexes().into(new ArrayList<>());
            for (IndexAttendu attendu : attendus) {
                Document trouve = enBase.stream().filter(i -> attendu.nom().equals(i.getString("name")))
                        .findFirst().orElse(null);
                if (trouve == null) {
                    ecarts.add("manquant : " + collection + "." + attendu.nom());
                } else if (!CreateurIndex.memeDefinition(attendu, trouve)) {
                    ecarts.add("définition différente : " + collection + "." + attendu.nom() + " → " + trouve.toJson());
                }
            }
        });
        // Aucun index en trop, dans aucune collection.
        for (String collection : db.listCollectionNames()) {
            if (HORS_LISTE.contains(collection)) {
                continue;
            }
            Set<String> attendus = attendusParCollection.getOrDefault(collection, List.of()).stream()
                    .map(IndexAttendu::nom).collect(Collectors.toSet());
            db.getCollection(collection).listIndexes().forEach(i -> {
                String nom = i.getString("name");
                if (!"_id_".equals(nom) && !attendus.contains(nom)) {
                    ecarts.add("non déclaré : " + collection + "." + nom);
                }
            });
        }

        assertThat(ecarts).isEmpty();
        // 83 en 1a ; 1b : + 4 de l'outbox (0107), + 3 de l'audit (0108).
        assertThat(IndexesAttendus.tous()).hasSize(90);
    }
}
