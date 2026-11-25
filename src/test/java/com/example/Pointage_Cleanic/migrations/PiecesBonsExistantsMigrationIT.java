package com.example.Pointage_Cleanic.migrations;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration 0012 (lot 1 / 1b, D4) sur un jeu construit : bloc pièce posé avec la référence
 * historique, auteurs et dates repris, registre rempli, rejets signalés sans blocage, rejeu sans
 * effet.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class PiecesBonsExistantsMigrationIT extends MongoTestContainer {

    private static final String ENTREES = "stockv2_bons_entree";
    private static final String SORTIES = "stockv2_bons_sortie";

    @Autowired private PiecesBonsExistantsMigration migration;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void preparer() {
        for (String c : List.of(ENTREES, SORTIES, PiecesBonsExistantsMigration.REGISTRE,
                PiecesBonsExistantsMigration.RAPPORTS)) {
            mongoTemplate.remove(new Query(), c);
        }
    }

    @Test
    void bons_existants_portes_vers_le_modele_piece() {
        Date cree = new Date(1_750_000_000_000L);
        Date valide = new Date(1_750_100_000_000L);
        mongoTemplate.insert(new Document("_id", "be-eff").append("reference", "BE-20260617-001")
                .append("statut", "EFFECTIF").append("siteDestinationId", "site-1").append("montantTotal", 25_000L)
                .append("creeParId", "u-1").append("creeParNom", "Awa Diop").append("createdAt", cree)
                .append("validateurId", "u-2").append("validateurNom", "Moussa Fall").append("updatedAt", valide), ENTREES);
        mongoTemplate.insert(new Document("_id", "bs-brouillon").append("reference", "BS-20260618-004")
                .append("statut", "BROUILLON").append("siteSourceId", "site-2").append("createdAt", cree), SORTIES);
        mongoTemplate.insert(new Document("_id", "bs-sans-ref").append("statut", "EFFECTIF"), SORTIES);
        Document dejaPorte = new Document("_id", "bs-deja").append("reference", "BS-2026-0001")
                .append("statut", "EFFECTIF").append("piece", new Document("type", "BS").append("numero", "BS-2026-0001")
                        .append("exercice", 2026).append("compteur", 1L));
        mongoTemplate.insert(dejaPorte, SORTIES);

        migration.executer();

        Document be = mongoTemplate.findById("be-eff", Document.class, ENTREES).get("piece", Document.class);
        assertThat(be.getString("type")).isEqualTo("BE");
        assertThat(be.getString("numero")).isEqualTo("BE-20260617-001");
        assertThat(be.get("exercice")).isNull();
        assertThat(be.get("creePar", Document.class).getString("nom")).isEqualTo("Awa Diop");
        assertThat(be.getDate("creeLe")).isEqualTo(cree);
        assertThat(be.get("valideePar", Document.class).getString("compteId")).isEqualTo("u-2");
        assertThat(be.getDate("valideeLe")).isEqualTo(valide);

        Document brouillon = mongoTemplate.findById("bs-brouillon", Document.class, SORTIES).get("piece", Document.class);
        assertThat(brouillon.getString("numero")).isEqualTo("BS-20260618-004");
        assertThat(brouillon).doesNotContainKey("valideePar");

        assertThat(mongoTemplate.findById("bs-deja", Document.class, SORTIES).get("piece", Document.class))
                .as("bloc existant intact").isEqualTo(dejaPorte.get("piece"));

        List<Document> registre = mongoTemplate.findAll(Document.class, PiecesBonsExistantsMigration.REGISTRE);
        assertThat(registre).hasSize(3);
        assertThat(registre).filteredOn(d -> "be-eff".equals(d.getString("pieceId"))).singleElement()
                .satisfies(d -> {
                    assertThat(d.getString("statut")).isEqualTo("VALIDEE");
                    assertThat(d.getString("siteId")).isEqualTo("site-1");
                    assertThat(d.getLong("montant")).isEqualTo(25_000L);
                });

        Document rapport = mongoTemplate.findOne(new Query(), Document.class, PiecesBonsExistantsMigration.RAPPORTS);
        assertThat(rapport.getInteger("traites")).isEqualTo(3);
        assertThat(rapport.getList("rejets", Document.class)).singleElement()
                .satisfies(r -> assertThat(r.getString("motif")).contains("sans référence"));
        assertThat(rapport.get("comptages", Document.class)).containsEntry("BS.EFFECTIF", 1)
                .containsEntry("BE.EFFECTIF", 1).containsEntry("BS.BROUILLON", 1);

        // Rejeu : aucun effet, aucun nouveau rapport.
        List<Document> avant = mongoTemplate.findAll(Document.class, SORTIES);
        migration.executer();
        assertThat(mongoTemplate.findAll(Document.class, SORTIES)).isEqualTo(avant);
        assertThat(mongoTemplate.count(new Query(), PiecesBonsExistantsMigration.RAPPORTS)).isEqualTo(1);
        assertThat(mongoTemplate.count(new Query(), PiecesBonsExistantsMigration.REGISTRE)).isEqualTo(3);
    }
}
