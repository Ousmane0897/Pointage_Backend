package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class AgenceQrTokenBackfillRunnerIT extends MongoTestContainer {

    @Autowired
    private AgenceQrTokenBackfillRunner runner;

    @Autowired
    private SiteClientRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.getCollection("sites_clients").drop();
        mongoTemplate.getCollection("pointages").drop();
    }

    /** Agence insérée en base brute, comme un document antérieur au QR d'agence. */
    private void agenceLegacy(String code, String nom) {
        mongoTemplate.getCollection("sites_clients").insertOne(
                new Document("code", code).append("nom", nom).append("actif", true));
    }

    @Test
    void genere_un_jeton_pour_chaque_agence_sans_qr_et_reste_idempotent() {
        agenceLegacy("A", "SGS Mermoz");
        agenceLegacy("B", "BGFI Tann");

        runner.run();
        List<SiteClient> apresPremier = repository.findAll();
        assertThat(apresPremier).allSatisfy(s -> {
            assertThat(s.getQrToken()).hasSize(32);
            assertThat(s.getQrVersion()).isEqualTo(1);
            assertThat(s.getQrGenereLe()).isNotNull();
        });

        runner.run();
        List<SiteClient> apresSecond = repository.findAll();
        assertThat(apresSecond).extracting(SiteClient::getQrToken)
                .containsExactlyInAnyOrderElementsOf(
                        apresPremier.stream().map(SiteClient::getQrToken).toList());
        assertThat(apresSecond).extracting(SiteClient::getQrVersion).containsOnly(1);
    }

    @Test
    void ne_remplace_jamais_un_jeton_existant() {
        repository.save(SiteClient.builder().code("A").nom("SGS Mermoz").actif(true)
                .qrToken("jeton-deja-imprime").qrVersion(4).build());

        runner.run();

        SiteClient s = repository.findByCode("A").orElseThrow();
        assertThat(s.getQrToken()).isEqualTo("jeton-deja-imprime");
        assertThat(s.getQrVersion()).isEqualTo(4);
    }

    @Test
    void cree_l_index_unique_sparse_du_jeton_et_les_index_agence_des_pointages() {
        runner.run();
        runner.run(); // ensureIndex rejoué : aucune erreur

        IndexInfo index = mongoTemplate.indexOps(SiteClient.class).getIndexInfo().stream()
                .filter(i -> i.getName().equals("qrToken_unique")).findFirst().orElseThrow();
        assertThat(index.isUnique()).isTrue();
        assertThat(index.isSparse()).isTrue();

        assertThat(mongoTemplate.indexOps(Pointage.class).getIndexInfo())
                .extracting(IndexInfo::getName)
                .contains("agenceId", "agenceIdSortie");

        // L'unicité est bien appliquée par Mongo…
        repository.save(SiteClient.builder().code("A").nom("A").qrToken("meme").build());
        assertThatThrownBy(() ->
                repository.save(SiteClient.builder().code("B").nom("B").qrToken("meme").build()))
                .isInstanceOf(DuplicateKeyException.class);
        // …et l'index sparse tolère plusieurs agences sans jeton.
        agenceLegacy("C", "C");
        agenceLegacy("D", "D");
        assertThat(mongoTemplate.getCollection("sites_clients").countDocuments()).isEqualTo(3);
    }
}
