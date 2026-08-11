package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.SuppressionStockLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SuppressionStockLogRepository extends MongoRepository<SuppressionStockLog, String> {

    List<SuppressionStockLog> findByDocumentId(String documentId);
}
