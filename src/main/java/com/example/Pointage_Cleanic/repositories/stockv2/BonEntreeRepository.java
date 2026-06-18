package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BonEntreeRepository extends MongoRepository<BonEntree, String> {
}
