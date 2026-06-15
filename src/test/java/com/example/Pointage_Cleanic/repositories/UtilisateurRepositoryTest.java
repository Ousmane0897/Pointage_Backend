package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.GestionModules.ModulesAutorises;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.Enum.RoleAdmin;
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
class UtilisateurRepositoryTest extends MongoTestContainer {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
    }


    @Test
    @DisplayName("Doit trouver un utilisateur par email")
    void shouldFindUtilisateurByEmail() {
        // GIVEN
        ModulesAutorises modules = new ModulesAutorises();
        modules.setDashboard(true);

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setPrenom("Ousmane");
        utilisateur.setNom("Diouf");
        utilisateur.setEmail("ousmane@cleanic.com");
        utilisateur.setPassword("password123");
        utilisateur.setPoste("Superviseur");
        utilisateur.setRole(RoleAdmin.BACKOFFICE);
        utilisateur.setModulesAutorises(modules);
        utilisateur.setActive(true);
        utilisateur.setMustChangePassword(true);

        utilisateurRepository.save(utilisateur);

        // WHEN
        Optional<Utilisateur> result =
                utilisateurRepository.findByEmail("ousmane@cleanic.com");

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getEmail())
                .isEqualTo("ousmane@cleanic.com");
        assertThat(result.get().getRole())
                .isEqualTo(RoleAdmin.BACKOFFICE);
        assertThat(result.get().isActive())
                .isTrue();
    }

    @Test
    @DisplayName("Ne doit rien retourner si email inexistant")
    void shouldReturnEmptyWhenEmailNotFound() {
        // WHEN
        Optional<Utilisateur> result =
                utilisateurRepository.findByEmail("inexistant@cleanic.com");

        // THEN
        assertThat(result).isEmpty();
    }
}
