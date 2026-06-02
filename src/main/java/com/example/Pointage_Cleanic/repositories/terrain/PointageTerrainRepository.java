package com.example.Pointage_Cleanic.repositories.terrain;

import com.example.Pointage_Cleanic.entities.terrain.PointageTerrain;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PointageTerrainRepository extends MongoRepository<PointageTerrain, String> {

    List<PointageTerrain> findByEmployeIdAndDatePointageBetween(
            String employeId, LocalDateTime debut, LocalDateTime fin);

    List<PointageTerrain> findByDatePointageBetween(LocalDateTime debut, LocalDateTime fin);

    List<PointageTerrain> findByEmployeIdAndAffectationId(String employeId, String affectationId);
}