package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.entities.rh.ParametresPaie;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ParametresPaieRepository extends MongoRepository<ParametresPaie, String> {

    Optional<ParametresPaie> findFirstByOrderByIdAsc();
}