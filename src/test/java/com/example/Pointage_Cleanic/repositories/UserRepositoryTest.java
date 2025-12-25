package com.example.Pointage_Cleanic.repositories;


import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.User;
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
class UserRepositoryTest  extends MongoTestContainer {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
    }


    @Test
    @DisplayName("Doit trouver un utilisateur par email")
    void shouldFindUserByEmail() {
        // GIVEN
        User user = new User();
        user.setEmail("admin@cleanic.com");
        user.setPassword("password123");
        user.setRole("ADMIN");
        user.setMustChangePassword(true);

        userRepository.save(user);

        // WHEN
        Optional<User> result =
                userRepository.findByEmail("admin@cleanic.com");

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getEmail())
                .isEqualTo("admin@cleanic.com");
        assertThat(result.get().isMustChangePassword())
                .isTrue();
    }

    @Test
    @DisplayName("Ne doit rien retourner si email inexistant")
    void shouldReturnEmptyWhenEmailNotFound() {
        // WHEN
        Optional<User> result =
                userRepository.findByEmail("inexistant@cleanic.com");

        // THEN
        assertThat(result).isEmpty();
    }
}
