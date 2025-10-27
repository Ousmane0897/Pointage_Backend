package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stock.MouvementEntreeStock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MouvementEntreeStockRepository extends MongoRepository<MouvementEntreeStock, String> {

    List<MouvementEntreeStock> findByCodeProduitOrderByDateMouvementAsc(String codeProduit);

    //List<MouvementStock> findByCodeProduitAndDestinationAndMois(String produitId, String agence, String mois);

    List<MouvementEntreeStock> findByType(TypeMouvement type);
}
