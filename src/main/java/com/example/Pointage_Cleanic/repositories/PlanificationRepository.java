package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Planification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface PlanificationRepository extends MongoRepository<Planification, String> {
}
