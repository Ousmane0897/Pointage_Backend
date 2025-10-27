package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.stock.MouvementSortieStock;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MouvementSortieStockRepository extends MongoRepository<MouvementSortieStock, Long> {
}
