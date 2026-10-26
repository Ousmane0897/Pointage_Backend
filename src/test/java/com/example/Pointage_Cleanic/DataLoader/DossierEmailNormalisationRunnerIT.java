package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
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
class DossierEmailNormalisationRunnerIT extends MongoTestContainer {

    private static final String COLLECTION = "dossiers_employes";

    @Autowired private DossierEmailNormalisationRunner runner;
    @Autowired private DossierEmployeRepository repository;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.getCollection(COLLECTION).drop();
    }

    @Test
    void normalise_les_emails_et_rend_le_dossier_trouvable_par_le_rattachement() {
        repository.save(dossier("M1", " Awa.Diop@Cleanic.SN  "));
        repository.save(dossier("M2", "deja@cleanic.sn"));
        repository.save(dossier("M3", "   "));
        repository.save(dossier("M4", null));

        runner.run();
        runner.run(); // idempotent

        assertThat(repository.findByMatricule("M1").orElseThrow().getEmail()).isEqualTo("awa.diop@cleanic.sn");
        assertThat(repository.findByMatricule("M2").orElseThrow().getEmail()).isEqualTo("deja@cleanic.sn");
        assertThat(repository.findByMatricule("M3").orElseThrow().getEmail()).isNull();
        assertThat(repository.findByMatricule("M4").orElseThrow().getEmail()).isNull();
        // C'est la requête du contrôle de rattachement : elle compare à l'identique.
        assertThat(repository.findByEmailIn(List.of("awa.diop@cleanic.sn")))
                .extracting(DossierEmploye::getMatricule).containsExactly("M1");
    }

    private static DossierEmploye dossier(String matricule, String email) {
        return DossierEmploye.builder()
                .matricule(matricule).nom("Diop").prenom("Awa").poste("Agent").email(email)
                .build();
    }
}
