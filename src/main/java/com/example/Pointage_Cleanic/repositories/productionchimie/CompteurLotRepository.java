package com.example.Pointage_Cleanic.repositories.productionchimie;

import com.example.Pointage_Cleanic.entities.productionchimie.CompteurLot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompteurLotRepository extends MongoRepository<CompteurLot, String> {
}