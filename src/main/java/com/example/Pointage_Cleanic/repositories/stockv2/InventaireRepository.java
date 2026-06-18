package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.Inventaire;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InventaireRepository extends MongoRepository<Inventaire, String> {
}
