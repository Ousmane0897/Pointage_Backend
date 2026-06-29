package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProduitStockRepository extends MongoRepository<ProduitStock, String> {
    boolean existsByCode(String code);
    Optional<ProduitStock> findByCode(String code);
    List<ProduitStock> findByActifTrue();
    List<ProduitStock> findByCategorieId(String categorieId);
    long countByCategorieId(String categorieId);
}
