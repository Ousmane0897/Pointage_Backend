package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.HistoriquePointCout;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface HistoriquePointCoutRepository extends MongoRepository<HistoriquePointCout, String> {

    List<HistoriquePointCout> findByProduitIdOrderByDateAsc(String produitId);

    List<HistoriquePointCout> findByProduitIdOrderByCreatedAtDesc(String produitId);
}
