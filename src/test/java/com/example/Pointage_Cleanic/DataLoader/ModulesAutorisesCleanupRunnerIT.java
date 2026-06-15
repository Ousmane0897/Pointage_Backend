package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class ModulesAutorisesCleanupRunnerIT extends MongoTestContainer {

    private static final String COLLECTION = "utilisateur";
    private static final String EMAIL_LEGACY = "legacy@test.com";

    @Autowired
    private ModulesAutorisesCleanupRunner runner;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @BeforeEach
    void clean() {
        mongoTemplate.getCollection(COLLECTION).drop();
    }

    /** Document tel que stocké par l'ancienne version du modèle : flags morts (racine + sous-objets). */
    private void insererDocumentLegacy() {
        Document legacy = new Document()
                .append("prenom", "Leg")
                .append("nom", "Acy")
                .append("email", EMAIL_LEGACY)
                .append("active", true)
                .append("modulesAutorises", new Document()
                        .append("dashboard", true)
                        .append("StatistiquesAgences", true)
                        .append("Employes", false)
                        .append("CollecteLivraison", true)              // ancien booléen
                        .append("Absences", new Document("tempsReel", true))  // ancien objet
                        .append("Pointages", true)
                        .append("Stock", false));
        mongoTemplate.getCollection(COLLECTION).insertOne(legacy);
    }

    @Test
    void lecture_document_legacy_ne_plante_plus() {
        insererDocumentLegacy();

        // Les champs inconnus sont ignorés (FAIL_ON_UNKNOWN_PROPERTIES désactivé + converter Mongo tolérant)
        List<Utilisateur> resultats = utilisateurRepository.findAllByEmail(EMAIL_LEGACY);

        assertThat(resultats).hasSize(1);
        assertThat(resultats.get(0).getModulesAutorises().isDashboard()).isTrue();
    }

    @Test
    void cleanup_retire_les_flags_morts_et_preserve_les_flags_valides() {
        insererDocumentLegacy();

        runner.run();

        Document modules = (Document) mongoTemplate.getCollection(COLLECTION)
                .find(new Document("email", EMAIL_LEGACY)).first()
                .get("modulesAutorises");

        // flags valides conservés
        assertThat(modules.getBoolean("dashboard")).isTrue();
        // flags morts supprimés
        assertThat(modules)
                .doesNotContainKeys("StatistiquesAgences", "Planifications", "Calendrier",
                        "JourFeries", "Employes", "Agences",
                        "CollecteLivraison", "Absences", "Pointages", "Stock");
    }

    @Test
    void cleanup_idempotent_sur_document_deja_propre() {
        Document propre = new Document()
                .append("email", "moderne@test.com")
                .append("modulesAutorises", new Document()
                        .append("dashboard", true)
                        .append("admin", false)
                        .append("rh", true));
        mongoTemplate.getCollection(COLLECTION).insertOne(propre);

        runner.run();
        runner.run();

        Document modules = (Document) mongoTemplate.getCollection(COLLECTION)
                .find(new Document("email", "moderne@test.com")).first()
                .get("modulesAutorises");

        assertThat(modules.getBoolean("dashboard")).isTrue();
        assertThat(modules.getBoolean("rh")).isTrue();
        assertThat(modules.getBoolean("admin")).isFalse();
    }
}
