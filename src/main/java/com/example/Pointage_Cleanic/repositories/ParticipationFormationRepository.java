package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.ParticipationFormation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipationFormationRepository extends MongoRepository<ParticipationFormation, String> {

    List<ParticipationFormation> findBySessionId(String sessionId);

    List<ParticipationFormation> findByEmployeId(String employeId);

    Optional<ParticipationFormation> findBySessionIdAndEmployeId(String sessionId, String employeId);

    boolean existsBySessionIdAndEmployeId(String sessionId, String employeId);

    long countBySessionId(String sessionId);

    long countBySessionIdAndPresent(String sessionId, boolean present);

    long countByPresent(boolean present);
}