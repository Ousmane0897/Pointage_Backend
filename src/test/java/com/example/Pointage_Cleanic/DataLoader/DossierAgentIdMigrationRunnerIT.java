package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeDto;
import com.example.Pointage_Cleanic.Mapper.rh.DossierEmployeMapper;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class DossierAgentIdMigrationRunnerIT extends MongoTestContainer {

    private static final String COLLECTION = "dossiers_employes";

    @Autowired
    private DossierAgentIdMigrationRunner runner;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private DossierEmployeRepository dossierEmployeRepository;

    @Autowired
    private DossierEmployeMapper mapper;

    @BeforeEach
    void clean() {
        mongoTemplate.getCollection(COLLECTION).drop();
    }

    /** Document tel que stocké par l'ancienne entité : clé Mongo "AgentId" (majuscule). */
    private void insererDocumentLegacy() {
        Document legacy = new Document()
                .append("matricule", "MAT-LEG")
                .append("AgentId", "1234")
                .append("nom", "Leg")
                .append("prenom", "Acy");
        mongoTemplate.getCollection(COLLECTION).insertOne(legacy);
    }

    @Test
    void migration_renomme_la_cle_AgentId_en_agentId() {
        insererDocumentLegacy();

        // Avant migration : la clé legacy n'est plus lue par l'entité renommée
        assertThat(dossierEmployeRepository.findByAgentId("1234")).isEmpty();

        runner.run();

        Optional<DossierEmploye> migre = dossierEmployeRepository.findByAgentId("1234");
        assertThat(migre).isPresent();
        assertThat(migre.get().getMatricule()).isEqualTo("MAT-LEG");

        Document brut = mongoTemplate.getCollection(COLLECTION)
                .find(new Document("matricule", "MAT-LEG")).first();
        assertThat(brut).isNotNull();
        assertThat(brut.containsKey("AgentId")).isFalse();
        assertThat(brut.getString("agentId")).isEqualTo("1234");
    }

    @Test
    void migration_idempotente() {
        insererDocumentLegacy();

        runner.run();
        Document apresPremierPassage = mongoTemplate.getCollection(COLLECTION)
                .find(new Document("matricule", "MAT-LEG")).first();

        runner.run();
        Document apresSecondPassage = mongoTemplate.getCollection(COLLECTION)
                .find(new Document("matricule", "MAT-LEG")).first();

        assertThat(apresSecondPassage).isEqualTo(apresPremierPassage);
    }

    /**
     * Régression du bug racine : avec l'ancien champ "AgentId", MapStruct ne
     * trouvait pas la méthode builder correspondante et toEntity perdait
     * silencieusement l'agentId (chemin de création POST).
     */
    @Test
    void toEntity_mappe_agentId() {
        DossierEmployeDto dto = DossierEmployeDto.builder()
                .matricule("MAT-1").agentId("5678").nom("X").prenom("Y")
                .build();

        DossierEmploye entity = mapper.toEntity(dto);

        assertThat(entity.getAgentId()).isEqualTo("5678");
    }
}