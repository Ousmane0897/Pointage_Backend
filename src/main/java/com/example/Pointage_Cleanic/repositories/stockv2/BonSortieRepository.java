package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BonSortieRepository extends MongoRepository<BonSortie, String> {
}
