package com.example.Pointage_Cleanic.repositories;


import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.ResetPasswordToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class ResetPasswordTokenRepositoryTest extends MongoTestContainer {

    @Autowired
    private ResetPasswordTokenRepository tokenRepository;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
    }

    private ResetPasswordToken jeton(String email, LocalDateTime expiration) {
        return tokenRepository.save(ResetPasswordToken.builder()
                .email(email).token("HASH").expiration(expiration).build());
    }

    @Test
    @DisplayName("Renvoie le code le plus récent de l'e-mail")
    void shouldFindLatestByEmail() {
        jeton("user@cleanic.com", LocalDateTime.now().plusMinutes(1));
        ResetPasswordToken recent = jeton("user@cleanic.com", LocalDateTime.now().plusMinutes(10));

        Optional<ResetPasswordToken> result = tokenRepository.findFirstByEmailOrderByExpirationDesc("user@cleanic.com");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(recent.getId());
    }

    @Test
    @DisplayName("Ne doit rien retourner si email inexistant")
    void shouldReturnEmptyWhenEmailNotFound() {
        assertThat(tokenRepository.findFirstByEmailOrderByExpirationDesc("absent@cleanic.com")).isEmpty();
    }

    @Test
    @DisplayName("Supprime tous les codes d'un e-mail")
    void shouldDeleteByEmail() {
        jeton("user@cleanic.com", LocalDateTime.now().plusMinutes(5));
        jeton("user@cleanic.com", LocalDateTime.now().plusMinutes(6));
        jeton("autre@cleanic.com", LocalDateTime.now().plusMinutes(6));

        tokenRepository.deleteByEmail("user@cleanic.com");

        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Purge les codes expirés")
    void shouldDeleteExpired() {
        jeton("a@cleanic.com", LocalDateTime.now().minusMinutes(1));
        jeton("b@cleanic.com", LocalDateTime.now().plusMinutes(5));

        tokenRepository.deleteByExpirationBefore(LocalDateTime.now());

        assertThat(tokenRepository.findAll()).extracting(ResetPasswordToken::getEmail).containsExactly("b@cleanic.com");
    }
}
