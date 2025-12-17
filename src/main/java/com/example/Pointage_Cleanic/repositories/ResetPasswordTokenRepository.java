package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.ResetPasswordToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ResetPasswordTokenRepository extends MongoRepository<ResetPasswordToken, String> {

    Optional<ResetPasswordToken> findByEmail(String email);
    Optional<ResetPasswordToken> findByToken(String token);
}
