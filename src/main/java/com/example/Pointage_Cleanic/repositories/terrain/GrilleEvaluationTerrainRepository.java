package com.example.Pointage_Cleanic.repositories.terrain;

import com.example.Pointage_Cleanic.entities.terrain.GrilleEvaluationTerrain;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface GrilleEvaluationTerrainRepository extends MongoRepository<GrilleEvaluationTerrain, String> {

    List<GrilleEvaluationTerrain> findBySiteIdAndActifTrue(String siteId);

    List<GrilleEvaluationTerrain> findBySiteIdIsNullAndActifTrue();
}