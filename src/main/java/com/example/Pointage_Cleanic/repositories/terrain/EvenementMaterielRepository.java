package com.example.Pointage_Cleanic.repositories.terrain;

import com.example.Pointage_Cleanic.entities.terrain.EvenementMateriel;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EvenementMaterielRepository extends MongoRepository<EvenementMateriel, String> {

    List<EvenementMateriel> findByMaterielIdOrderByDateDesc(String materielId);
}