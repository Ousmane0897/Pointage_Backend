package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.ResetPasswordToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class ResetPasswordTokenRepositoryTest {

    @Autowired
    private ResetPasswordTokenRepository tokenRepository;

    @BeforeEach
    void cleanDatabase() {
        // 🔥 Nettoie la collection AVANT chaque test
        tokenRepository.deleteAll();
    }

    @Test
    @DisplayName("Doit trouver un token par email")
    void shouldFindByEmail() {
        // GIVEN
        ResetPasswordToken token = ResetPasswordToken.builder()
                .email("user@cleanic.com")
                .token("123456")
                .expiration(LocalDateTime.now().plusMinutes(15))
                .build();

        tokenRepository.save(token);

        // WHEN
        Optional<ResetPasswordToken> result =
                tokenRepository.findByEmail("user@cleanic.com");

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getToken())
                .isEqualTo("123456");
    }

    @Test
    @DisplayName("Doit trouver un token par valeur du token")
    void shouldFindByToken() {
        // GIVEN
        ResetPasswordToken token = ResetPasswordToken.builder()
                .email("user@cleanic.com")
                .token("654321")
                .expiration(LocalDateTime.now().plusMinutes(15))
                .build();

        tokenRepository.save(token);

        // WHEN
        Optional<ResetPasswordToken> result =
                tokenRepository.findByToken("654321");

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getEmail())
                .isEqualTo("user@cleanic.com");
    }

    @Test
    @DisplayName("Ne doit rien retourner si email inexistant")
    void shouldReturnEmptyWhenEmailNotFound() {
        // WHEN
        Optional<ResetPasswordToken> result =
                tokenRepository.findByEmail("absent@cleanic.com");

        // THEN
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Ne doit rien retourner si token inexistant")
    void shouldReturnEmptyWhenTokenNotFound() {
        // WHEN
        Optional<ResetPasswordToken> result =
                tokenRepository.findByToken("000000");

        // THEN
        assertThat(result).isEmpty();
    }
}
