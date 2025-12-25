package com.example.Pointage_Cleanic.repositories;


import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.Planification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class PlanificationRepositoryTest extends MongoTestContainer {

    @Autowired
    private PlanificationRepository planificationRepository;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
    }


    private Planification createPlanification(
            String prenomNom,
            Planification.Statut statut
    ) {
        Planification p = new Planification();
        p.setPrenomNom(prenomNom);
        p.setNomSite("Site A");
        p.setStatut(statut);
        p.setDateDebut(new Date());
        p.setDateFin(new Date());
        p.setDateCreation(new Date().toString());
        return p;
    }

    @Test
    @DisplayName("Doit trouver les planifications par liste de statuts")
    void shouldFindByStatutIn() {
        // GIVEN
        planificationRepository.save(
                createPlanification("Agent 1", Planification.Statut.EN_ATTENTE)
        );
        planificationRepository.save(
                createPlanification("Agent 2", Planification.Statut.EN_COURS)
        );
        planificationRepository.save(
                createPlanification("Agent 3", Planification.Statut.EXECUTEE)
        );

        // WHEN
        List<Planification> result =
                planificationRepository.findByStatutIn(
                        List.of(
                                Planification.Statut.EN_ATTENTE.name(),
                                Planification.Statut.EN_COURS.name()
                        )
                );

        // THEN
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(Planification::getStatut)
                .containsExactlyInAnyOrder(
                        Planification.Statut.EN_ATTENTE,
                        Planification.Statut.EN_COURS
                );
    }

    @Test
    @DisplayName("Doit trouver les planifications avec statut EN_ATTENTE_VALIDATION")
    void shouldFindByStatut() {
        // GIVEN
        planificationRepository.save(
                createPlanification("Agent 4", Planification.Statut.EN_ATTENTE_VALIDATION)
        );
        planificationRepository.save(
                createPlanification("Agent 5", Planification.Statut.ANNULATION_ACCEPTEE)
        );

        // WHEN
        List<Planification> result =
                planificationRepository.findByStatut(
                        Planification.Statut.EN_ATTENTE_VALIDATION
                );

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatut())
                .isEqualTo(Planification.Statut.EN_ATTENTE_VALIDATION);
    }
}
