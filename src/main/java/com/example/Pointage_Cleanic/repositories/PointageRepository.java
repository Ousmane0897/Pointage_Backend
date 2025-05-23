package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Pointage;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PointageRepository extends MongoRepository<Pointage,Integer> {
}
