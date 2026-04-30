package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.ParametresPaie;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ParametresPaieRepository extends MongoRepository<ParametresPaie, String> {

    Optional<ParametresPaie> findFirstByOrderByIdAsc();
}