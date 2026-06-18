package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MouvementStockRepository extends MongoRepository<MouvementStock, String> {
    List<MouvementStock> findByProduitIdOrderByDateDesc(String produitId);
}
