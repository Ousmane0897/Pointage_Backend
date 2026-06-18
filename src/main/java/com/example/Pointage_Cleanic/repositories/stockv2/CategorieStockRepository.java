package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CategorieStockRepository extends MongoRepository<CategorieStock, String> {
    List<CategorieStock> findByParentIdIsNull();
    List<CategorieStock> findByParentId(String parentId);
    long countByParentId(String parentId);
}
