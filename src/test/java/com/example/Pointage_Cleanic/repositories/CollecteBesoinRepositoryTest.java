package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.StatutCommande;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.besoins.CollecteBesoins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class CollecteBesoinRepositoryTest extends MongoTestContainer {

    @Autowired
    private CollecteBesoinRepository repository;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
    }


    private CollecteBesoins createCollecte(
            String destination,
            StatutCommande statut,
            String mois
    ) {
        return CollecteBesoins.builder()
                .destination(destination)
                .responsable("Superviseur")
                .dateDemande("2025-01-10")
                .statut(statut)
                .moisActuel(mois)
                .build();
    }

    @Test
    @DisplayName("Doit trouver les collectes par destination")
    void shouldFindByDestination() {
        // GIVEN
        repository.save(createCollecte(
                "Agence Dakar",
                StatutCommande.EN_ATTENTE,
                "01-2025"
        ));

        // WHEN
        List<CollecteBesoins> result =
                repository.findByDestination("Agence Dakar");

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDestination())
                .isEqualTo("Agence Dakar");
    }

    @Test
    @DisplayName("Doit trouver les collectes par statut")
    void shouldFindByStatut() {
        // GIVEN
        repository.save(createCollecte(
                "Chantier Thiès",
                StatutCommande.LIVREE,
                "01-2025"
        ));

        // WHEN
        List<CollecteBesoins> result =
                repository.findByStatut(StatutCommande.LIVREE);

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatut())
                .isEqualTo(StatutCommande.LIVREE);
    }

    @Test
    @DisplayName("Doit trouver les collectes par mois actuel")
    void shouldFindByMoisActuel() {
        // GIVEN
        repository.save(createCollecte(
                "Agence Rufisque",
                StatutCommande.EN_ATTENTE,
                "02-2025"
        ));
        repository.save(createCollecte(
                "Agence Rufisque",
                StatutCommande.EN_ATTENTE,
                "01-2025"
        ));

        // WHEN
        List<CollecteBesoins> result =
                repository.findByMoisActuel("02-2025");

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMoisActuel())
                .isEqualTo("02-2025");
    }
}
