package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.pointage.PointageLectureDto;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Historique des pointages PIN : filtre par agence (arrivée OU départ) et noms
 * d'agence résolus à la lecture, contre un vrai Mongo.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class PointageHistoriqueAgenceIT extends MongoTestContainer {

    /** Jour passé : l'historique exclut le jour courant. */
    private static final LocalDate JOUR = LocalDate.of(2026, 9, 1);

    @Autowired private PointageServices service;
    @Autowired private PointageRepository pointageRepository;
    @Autowired private SiteClientRepository siteClientRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String idA;
    private String idB;

    @BeforeEach
    void setUp() {
        mongoTemplate.getCollection("pointages").drop();
        mongoTemplate.getCollection("sites_clients").drop();
        idA = siteClientRepository.save(SiteClient.builder().code("A").nom("SGS Mermoz").actif(true).build()).getId();
        idB = siteClientRepository.save(SiteClient.builder().code("B").nom("BGFI Tann").actif(true).build()).getId();

        pointage("entre-A-sorti-B", idA, idB);
        pointage("A-seul", idA, null);
        pointage("legacy", null, null);
    }

    private void pointage(String nom, String arrivee, String depart) {
        pointageRepository.save(Pointage.builder()
                .codeSecret("1234").nom(nom).prenom("Awa").date(JOUR)
                .heureArrive("08:00").timestamp(Instant.now())
                .agenceId(arrivee).agenceIdSortie(depart).build());
    }

    private Page<PointageLectureDto> historique(String agenceId) {
        return service.lecture(service.searchHistorique(null, null, null, agenceId, 0, 20));
    }

    @Test
    void filtre_par_agence_d_arrivee_ou_de_depart() {
        assertThat(historique(idA).getContent()).extracting(d -> d.getPointage().getNom())
                .containsExactlyInAnyOrder("entre-A-sorti-B", "A-seul");
        assertThat(historique(idB).getContent()).extracting(d -> d.getPointage().getNom())
                .containsExactly("entre-A-sorti-B");
        assertThat(historique(PointageServices.AGENCE_AUCUNE).getContent())
                .extracting(d -> d.getPointage().getNom())
                .containsExactly("legacy");
        assertThat(historique(null).getTotalElements()).isEqualTo(3);
    }

    @Test
    void les_noms_d_agence_sont_resolus_a_la_lecture() {
        PointageLectureDto ab = historique(idB).getContent().get(0);
        assertThat(ab.getAgenceArriveeNom()).isEqualTo("SGS Mermoz");
        assertThat(ab.getAgenceDepartNom()).isEqualTo("BGFI Tann");

        // Renommage : reflété immédiatement, le pointage ne stockant que l'id.
        SiteClient b = siteClientRepository.findById(idB).orElseThrow();
        b.setNom("BGFI Tann (agence)");
        siteClientRepository.save(b);
        assertThat(historique(idB).getContent().get(0).getAgenceDepartNom()).isEqualTo("BGFI Tann (agence)");

        PointageLectureDto legacy = historique(PointageServices.AGENCE_AUCUNE).getContent().get(0);
        assertThat(legacy.getAgenceArriveeNom()).isNull();
        assertThat(legacy.getAgenceDepartNom()).isNull();
    }
}
