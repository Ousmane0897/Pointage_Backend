package com.example.Pointage_Cleanic.repositories.terrain;

import com.example.Pointage_Cleanic.entities.terrain.Materiel;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MaterielRepository extends MongoRepository<Materiel, String> {

    boolean existsByCode(String code);
}