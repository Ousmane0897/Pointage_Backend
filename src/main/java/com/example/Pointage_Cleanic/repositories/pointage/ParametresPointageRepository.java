package com.example.Pointage_Cleanic.repositories.pointage;

import com.example.Pointage_Cleanic.entities.pointage.ParametresPointage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ParametresPointageRepository extends MongoRepository<ParametresPointage, String> {

    /** Singleton ; le tri rend la lecture déterministe si un doublon existait. */
    Optional<ParametresPointage> findFirstByOrderByIdAsc();
}
