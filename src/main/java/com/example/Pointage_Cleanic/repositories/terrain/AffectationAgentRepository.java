package com.example.Pointage_Cleanic.repositories.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AffectationAgentRepository extends MongoRepository<AffectationAgent, String> {

    List<AffectationAgent> findByEmployeId(String employeId);

    List<AffectationAgent> findByStatutIn(List<StatutAffectation> statuts);

    List<AffectationAgent> findByStatutInAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
            List<StatutAffectation> statuts, LocalDateTime borneBasse, LocalDateTime borneHaute);
}