package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.ResetPasswordToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ResetPasswordTokenRepository extends MongoRepository<ResetPasswordToken, String> {

    /** Code le plus récent d'un e-mail (une demande supprime les précédents ; repli défensif). */
    Optional<ResetPasswordToken> findFirstByEmailOrderByExpirationDesc(String email);

    void deleteByEmail(String email);

    void deleteByExpirationBefore(LocalDateTime instant);
}
