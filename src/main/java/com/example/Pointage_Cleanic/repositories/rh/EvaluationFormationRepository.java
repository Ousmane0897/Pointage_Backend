package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.entities.rh.EvaluationFormation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationFormationRepository extends MongoRepository<EvaluationFormation, String> {

    List<EvaluationFormation> findBySessionId(String sessionId);

    List<EvaluationFormation> findByEmployeId(String employeId);

    Optional<EvaluationFormation> findByParticipationId(String participationId);
}