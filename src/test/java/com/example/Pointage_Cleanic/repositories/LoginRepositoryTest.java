package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class LoginRepositoryTest {

    @Autowired
    private LoginRepository loginRepository;

    @BeforeEach
    void cleanDatabase() {
        loginRepository.deleteAll();
    }

    @Test
    @DisplayName("Doit trouver un user par email")
    void shouldFindByEmail() {
        // GIVEN
        User user = new User();
        user.setEmail("login@cleanic.com");
        user.setPassword("password123");
        user.setRole("ADMIN");
        user.setMustChangePassword(true);

        loginRepository.save(user);

        // WHEN
        Optional<User> result =
                loginRepository.findByEmail("login@cleanic.com");

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getEmail())
                .isEqualTo("login@cleanic.com");
    }

    @Test
    @DisplayName("Doit supprimer un user par email")
    void shouldDeleteByEmail() {
        // GIVEN
        User user = new User();
        user.setEmail("delete@cleanic.com");
        user.setPassword("password123");
        user.setRole("USER");

        loginRepository.save(user);

        // WHEN
        loginRepository.deleteByEmail("delete@cleanic.com");

        // THEN
        Optional<User> result =
                loginRepository.findByEmail("delete@cleanic.com");

        assertThat(result).isEmpty();
    }
}
