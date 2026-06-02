package com.example.Pointage_Cleanic.repositories.terrain;

import com.example.Pointage_Cleanic.entities.terrain.FicheIntervention;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FicheInterventionRepository extends MongoRepository<FicheIntervention, String> {
}