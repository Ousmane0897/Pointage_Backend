package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Gab;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GabRepository extends MongoRepository<Gab,String> {
}
