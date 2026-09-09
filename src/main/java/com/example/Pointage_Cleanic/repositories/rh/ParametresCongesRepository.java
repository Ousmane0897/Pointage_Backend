package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.entities.rh.ParametresConges;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ParametresCongesRepository extends MongoRepository<ParametresConges, String> {

    /** Le document est un singleton ; le tri rend la lecture déterministe si un doublon existait. */
    Optional<ParametresConges> findFirstByOrderByIdAsc();
}
