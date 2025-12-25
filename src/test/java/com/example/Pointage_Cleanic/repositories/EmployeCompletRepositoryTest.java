package com.example.Pointage_Cleanic.repositories;


import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest
class EmployeCompletRepositoryTest extends MongoTestContainer {

    @Autowired
    private EmployeCompletRepository repository;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
    }

    private EmployeComplet buildEmploye(
            String agentId, String matricule,
            String prenom, String nom, String email
    ) {
        EmployeComplet e = new EmployeComplet();
        e.setAgentId(agentId);
        e.setMatricule(matricule);
        e.setPrenom(prenom);
        e.setNom(nom);
        e.setEmail(email);
        e.setNomComplet((prenom + " " + nom).toUpperCase());
        return e;
    }

    @Test
    void should_find_by_agentId_and_matricule() {

        EmployeComplet e = buildEmploye("AG-1", "MAT-1", "Mamadou", "Diop", "m@x.com");
        repository.save(e);

        assertTrue(repository.findByAgentId("AG-1").isPresent());
        assertTrue(repository.findByMatricule("MAT-1").isPresent());
    }

    @Test
    void should_detect_existing_fields() {

        EmployeComplet e = buildEmploye("AG-2", "MAT-2", "Awa", "Sarr", "a@x.com");
        repository.save(e);

        assertTrue(repository.existsByAgentId("AG-2"));
        assertTrue(repository.existsByMatricule("MAT-2"));
        assertTrue(repository.existsByNomComplet("AWA SARR"));
    }

    @Test
    void should_find_by_prenom_and_nom() {

        EmployeComplet e = buildEmploye("AG-3", "MAT-3", "Ibrahima", "Ndiaye", "i@x.com");
        repository.save(e);

        Optional<EmployeComplet> result =
                repository.findByPrenomAndNom("Ibrahima", "Ndiaye");

        assertTrue(result.isPresent());
    }

    @Test
    void should_search_with_ignore_case() {

        EmployeComplet e = buildEmploye("AG-4", "MAT-4", "Fatou", "Ba", "fatou@x.com");
        repository.save(e);

        Page<EmployeComplet> page =
                repository.findByPrenomContainingIgnoreCaseOrNomContainingIgnoreCaseOrMatriculeContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        "fat", "fat", "fat", "fat", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
    }

    @Test
    void should_return_pageable_results() {

        repository.save(buildEmploye("AG-5", "MAT-5", "Aliou", "Fall", "a1@x.com"));
        repository.save(buildEmploye("AG-6", "MAT-6", "Ousmane", "Diouf", "a2@x.com"));

        Page<EmployeComplet> page = repository.findAll(PageRequest.of(0, 1));

        assertEquals(1, page.getContent().size());
        assertEquals(2, page.getTotalElements());
    }
}
