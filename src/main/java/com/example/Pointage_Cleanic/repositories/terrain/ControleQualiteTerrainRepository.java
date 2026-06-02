package com.example.Pointage_Cleanic.repositories.terrain;

import com.example.Pointage_Cleanic.entities.terrain.ControleQualiteTerrain;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ControleQualiteTerrainRepository extends MongoRepository<ControleQualiteTerrain, String> {

    List<ControleQualiteTerrain> findBySiteIdOrderByDateControleDesc(String siteId);
}