package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stock.MouvementEntreeStock;
import com.example.Pointage_Cleanic.entities.stock.MouvementSortieStock;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface MouvementSortieStockRepository extends MongoRepository<MouvementSortieStock, Long> {

    List<MouvementSortieStock> findByCodeProduitOrderByDateMouvementAsc(String codeProduit);

    List<MouvementSortieStock> findByTypeMouvement(TypeMouvement type);

    List<MouvementSortieStock> findByCodeProduit(String codeProduit);



    List<MouvementSortieStock> findByNomProduit(String nomProduit);
    @Query("{ 'typeMouvement': ?0, 'dateMouvement': { $gte: ?1, $lte: ?2 } }")
    List<MouvementSortieStock> findByTypeMouvementAndDateMouvementBetween(TypeMouvement type, Instant start, Instant end);
}
