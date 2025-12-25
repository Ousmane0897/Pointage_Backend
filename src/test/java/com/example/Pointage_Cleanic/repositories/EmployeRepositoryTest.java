package com.example.Pointage_Cleanic.repositories;


import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.Employe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class EmployeRepositoryTest  extends MongoTestContainer {

    @Autowired
    private EmployeRepository employeRepository;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
    }


    private Employe createEmploye(
            String codeSecret,
            String prenom,
            String[] sites
    ) {
        return Employe.builder()
                .codeSecret(codeSecret)
                .prenom(prenom)
                .nom("Test")
                .numero("770000000")
                .intervention("agent bureau")
                .statut("employe")
                .site(sites)
                .matin(true)
                .apresMidi(false)
                .build();
    }

    @Test
    @DisplayName("Doit trouver un employé par code secret")
    void shouldFindByCodeSecret() {
        // GIVEN
        employeRepository.save(
                createEmploye("ABC123", "Ousmane", new String[]{"Dakar"})
        );

        // WHEN
        Optional<Employe> result =
                employeRepository.findByCodeSecret("ABC123");

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getPrenom())
                .isEqualTo("Ousmane");
    }

    @Test
    @DisplayName("Doit retourner la liste des sites distincts")
    void shouldReturnDistinctSites() {
        // GIVEN
        employeRepository.save(
                createEmploye("EMP1", "Ali", new String[]{"Dakar", "Thiès"})
        );
        employeRepository.save(
                createEmploye("EMP2", "Moussa", new String[]{"Dakar", "Saint-Louis"})
        );

        // WHEN
        List<String> sites = employeRepository.findAllDistinctSites();

        // THEN
        assertThat(sites)
                .containsExactlyInAnyOrder("Dakar", "Thiès", "Saint-Louis");
    }

    @Test
    @DisplayName("Doit retourner les IDs des employés par site")
    void shouldFindEmployeIdsBySite() {
        Employe emp1 = employeRepository.save(
                createEmploye("EMP3", "Fatou", new String[]{"Dakar"})
        );
        Employe emp2 = employeRepository.save(
                createEmploye("EMP4", "Awa", new String[]{"Thiès"})
        );

        List<String> ids = employeRepository.findEmployeIdsBySite("Dakar")
                .stream()
                .map(p -> p.getId())
                .toList();

        assertThat(ids)
                .contains(emp1.getId())
                .doesNotContain(emp2.getId());
    }


}
