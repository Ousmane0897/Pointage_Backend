package com.example.Pointage_Cleanic.migrations.index;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Décision D3 : avant un index unique, {@link CreateurIndex} cherche les doublons ; s'il en
 * trouve, il écrit le rapport et échoue, sans créer l'index ni dédoublonner.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class CreateurIndexIT extends MongoTestContainer {

    private static final String COLLECTION = "essai_index";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CreateurIndex createurIndex;

    @BeforeEach
    void preparer() {
        mongoTemplate.dropCollection(COLLECTION);
        mongoTemplate.remove(new Query(Criteria.where("migrationId").regex("^essai-")), CreateurIndex.RAPPORTS);
    }

    private MongoCollection<Document> collection() {
        return mongoTemplate.getCollection(COLLECTION);
    }

    private List<String> nomsIndex() {
        List<String> noms = new ArrayList<>();
        collection().listIndexes().forEach(i -> noms.add(i.getString("name")));
        return noms;
    }

    private static IndexAttendu uniqueCode(Document filtre) {
        return new IndexAttendu("essai", COLLECTION, "code_unique", new Document("code", 1), true, filtre);
    }

    @Test
    void doublons_rapport_et_echec_sans_index_ni_dedoublonnage() {
        collection().insertMany(List.of(new Document("code", "A"), new Document("code", "A"), new Document("code", "B")));

        assertThatThrownBy(() -> createurIndex.creer(List.of(uniqueCode(null)), "essai-doublons"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("essai-doublons");

        assertThat(nomsIndex()).containsExactly("_id_");
        assertThat(collection().countDocuments()).as("rien de supprimé").isEqualTo(3);
        Document rapport = mongoTemplate.findOne(
                Query.query(Criteria.where("migrationId").is("essai-doublons")), Document.class, CreateurIndex.RAPPORTS);
        assertThat(rapport).isNotNull();
        Document rejet = rapport.getList("rejets", Document.class).get(0);
        assertThat(rejet.getString("motif")).isEqualTo("DOUBLONS");
        Document doublon = rejet.getList("valeursEnDouble", Document.class).get(0);
        assertThat(doublon.get("valeur", Document.class).getString("code")).isEqualTo("A");
        assertThat(doublon.getInteger("nombre")).isEqualTo(2);
    }

    @Test
    void filtre_partiel_les_documents_sans_valeur_ne_sont_pas_des_doublons() {
        collection().insertMany(List.of(new Document("code", "A"), new Document("autre", 1),
                new Document("autre", 2), new Document("code", null)));
        IndexAttendu partiel = uniqueCode(new Document("code", new Document("$type", "string")));

        createurIndex.creer(List.of(partiel), "essai-partiel");

        assertThat(nomsIndex()).contains("code_unique");
    }

    @Test
    void sans_filtre_partiel_les_valeurs_absentes_comptent_comme_doublons() {
        collection().insertMany(List.of(new Document("autre", 1), new Document("autre", 2)));

        assertThatThrownBy(() -> createurIndex.creer(List.of(uniqueCode(null)), "essai-absents"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Pour un index unique, champ absent et champ null sont la même valeur. */
    @Test
    void absent_et_null_sont_un_doublon_et_sont_rapportes() {
        collection().insertMany(List.of(new Document("produitId", "p").append("siteId", null),
                new Document("produitId", "p")));
        IndexAttendu produitSite = new IndexAttendu("essai", COLLECTION, "produit_site_unique",
                new Document("produitId", 1).append("siteId", 1), true, null);

        assertThatThrownBy(() -> createurIndex.creer(List.of(produitSite), "essai-null"))
                .isInstanceOf(IllegalStateException.class);
        Document rapport = mongoTemplate.findOne(
                Query.query(Criteria.where("migrationId").is("essai-null")), Document.class, CreateurIndex.RAPPORTS);
        assertThat(rapport.getList("rejets", Document.class).get(0).getString("motif")).isEqualTo("DOUBLONS");
    }

    @Test
    void index_deja_present_a_l_identique_rien_a_faire_et_rejouable() {
        collection().createIndex(new Document("code", 1), new IndexOptions().name("code_unique").unique(true));

        createurIndex.creer(List.of(uniqueCode(null)), "essai-present");
        createurIndex.creer(List.of(uniqueCode(null)), "essai-present");

        assertThat(nomsIndex()).containsExactlyInAnyOrder("_id_", "code_unique");
    }

    @Test
    void meme_nom_autre_definition_est_un_conflit() {
        collection().createIndex(new Document("code", 1), new IndexOptions().name("code_unique"));

        assertThatThrownBy(() -> createurIndex.creer(List.of(uniqueCode(null)), "essai-conflit"))
                .isInstanceOf(IllegalStateException.class);
        Document rapport = mongoTemplate.findOne(
                Query.query(Criteria.where("migrationId").is("essai-conflit")), Document.class, CreateurIndex.RAPPORTS);
        assertThat(rapport.getList("rejets", Document.class).get(0).getString("motif"))
                .isEqualTo("DEFINITION_DIFFERENTE");
    }
}
