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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class ModulesAutorisesMigrationRunnerIT extends MongoTestContainer {

    private static final String COLLECTION = "utilisateur";
    private static final String EMAIL_LEGACY = "legacy@test.com";

    @Autowired
    private ModulesAutorisesMigrationRunner runner;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @BeforeEach
    void clean() {
        mongoTemplate.getCollection(COLLECTION).drop();
    }

    /** Document tel que stocké par l'ancienne version du modèle : sous-modules booléens. */
    private void insererDocumentLegacy() {
        Document legacy = new Document()
                .append("prenom", "Leg")
                .append("nom", "Acy")
                .append("email", EMAIL_LEGACY)
                .append("active", true)
                .append("modulesAutorises", new Document()
                        .append("Dashboard", true)
                        .append("CollecteLivraison", true)
                        .append("Absences", false)
                        .append("Pointages", true)
                        .append("Stock", false));
        mongoTemplate.getCollection(COLLECTION).insertOne(legacy);
    }

    @Test
    void lecture_document_legacy_echoue_avant_migration() {
        insererDocumentLegacy();

        assertThatThrownBy(() -> utilisateurRepository.findAllByEmail(EMAIL_LEGACY))
                .hasStackTraceContaining("No converter found")
                .hasStackTraceContaining("CollecteLivraison");
    }

    @Test
    void migration_convertit_les_booleens_legacy_en_objets() {
        insererDocumentLegacy();

        runner.run();

        List<Utilisateur> resultats = utilisateurRepository.findAllByEmail(EMAIL_LEGACY);
        assertThat(resultats).hasSize(1);

        var modules = resultats.get(0).getModulesAutorises();
        // true → tous les sous-flags à true
        assertThat(modules.getCollecteLivraison().isCollecteBesoins()).isTrue();
        assertThat(modules.getCollecteLivraison().isSuiviLivraison()).isTrue();
        assertThat(modules.getPointages().isPointagesDuJour()).isTrue();
        assertThat(modules.getPointages().isHistoriquePointages()).isTrue();
        // false → tous les sous-flags à false
        assertThat(modules.getAbsences().isTempsReel()).isFalse();
        assertThat(modules.getAbsences().isHistoriqueAbsences()).isFalse();
        assertThat(modules.getStock().isProduits()).isFalse();
        assertThat(modules.getStock().isEntrees()).isFalse();
        assertThat(modules.getStock().isSorties()).isFalse();
        assertThat(modules.getStock().isSuivis()).isFalse();
        assertThat(modules.getStock().isHistoriquesEntrees()).isFalse();
        assertThat(modules.getStock().isHistoriquesSorties()).isFalse();
        // flag top-level booléen inchangé
        assertThat(modules.isDashboard()).isTrue();
    }

    @Test
    void migration_idempotente_et_sans_effet_sur_documents_deja_au_format_objet() {
        insererDocumentLegacy();
        // Document au format actuel : sous-flags hétérogènes, à préserver tels quels
        Document moderne = new Document()
                .append("email", "moderne@test.com")
                .append("modulesAutorises", new Document()
                        .append("CollecteLivraison", new Document()
                                .append("collecteBesoins", true)
                                .append("suiviLivraison", false)));
        mongoTemplate.getCollection(COLLECTION).insertOne(moderne);

        runner.run();
        Document apresPremierPassage = mongoTemplate.getCollection(COLLECTION)
                .find(new Document("email", EMAIL_LEGACY)).first();

        runner.run();
        Document apresSecondPassage = mongoTemplate.getCollection(COLLECTION)
                .find(new Document("email", EMAIL_LEGACY)).first();

        assertThat(apresSecondPassage).isEqualTo(apresPremierPassage);

        var modulesModerne = utilisateurRepository.findAllByEmail("moderne@test.com")
                .get(0).getModulesAutorises();
        assertThat(modulesModerne.getCollecteLivraison().isCollecteBesoins()).isTrue();
        assertThat(modulesModerne.getCollecteLivraison().isSuiviLivraison()).isFalse();
    }
}
