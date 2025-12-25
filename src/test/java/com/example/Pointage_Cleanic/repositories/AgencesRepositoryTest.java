package com.example.Pointage_Cleanic.repositories;


import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.Agence;
import com.example.Pointage_Cleanic.repositories.projections.AgenceJoursOuvertureProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class AgencesRepositoryTest extends MongoTestContainer {

    @Autowired
    private AgencesRepository agencesRepository;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
    }


    private Agence createAgence(String nom, String joursOuverture) {
        return Agence.builder()
                .nom(nom)
                .adresse("Dakar Plateau")
                .joursOuverture(joursOuverture)
                .heuresTravail("08:00-17:00")
                .nombreAgentsMaximum(10)
                .receptionEmploye(true)
                .deplacementEmploye(true)
                .deplacementInterne(false)
                .build();
    }

    @Test
    @DisplayName("Doit retourner les jours d'ouverture par nom d'agence")
    void shouldFindJoursOuvertureByNom() {

        agencesRepository.save(
                createAgence("Agence Dakar", "Lundi-Vendredi")
        );

        Optional<AgenceJoursOuvertureProjection> result =
                agencesRepository.findJoursOuvertureByNom("Agence Dakar");

        assertThat(result).isPresent();
        assertThat(result.get().getJoursOuverture())
                .isEqualTo("Lundi-Vendredi");
    }

    @Test
    @DisplayName("Ne doit rien retourner si l'agence n'existe pas")
    void shouldReturnEmptyWhenAgenceNotFound() {

        Optional<AgenceJoursOuvertureProjection> result =
                agencesRepository.findJoursOuvertureByNom("Agence Inexistante");

        assertThat(result).isEmpty();
    }
}
