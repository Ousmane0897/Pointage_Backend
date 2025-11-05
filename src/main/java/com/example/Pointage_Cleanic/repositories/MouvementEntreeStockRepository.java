package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stock.MouvementEntreeStock;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface MouvementEntreeStockRepository extends MongoRepository<MouvementEntreeStock, String> {

    List<MouvementEntreeStock> findByCodeProduitOrderByDateMouvementAsc(String codeProduit);

    //List<MouvementStock> findByCodeProduitAndDestinationAndMois(String produitId, String agence, String mois);

    List<MouvementEntreeStock> findByType(TypeMouvement type);

    List<MouvementEntreeStock> findByCodeProduit(String codeProduit);

    @Query("{ 'type': ?0, 'dateMouvement': { $gte: ?1, $lte: ?2 } }")
    List<MouvementEntreeStock> findByTypeAndDateMouvementBetween(TypeMouvement type, Instant start, Instant end);
}
