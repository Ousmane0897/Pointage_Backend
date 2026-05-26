package com.example.Pointage_Cleanic.repositories.productionchimie;

import com.example.Pointage_Cleanic.entities.productionchimie.MouvementStockChimie;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MouvementStockChimieRepository extends MongoRepository<MouvementStockChimie, String> {
}