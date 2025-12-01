package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.StatutCommande;
import com.example.Pointage_Cleanic.entities.besoins.CollecteBesoins;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CollecteBesoinRepository extends MongoRepository<CollecteBesoins, String> {
    List<CollecteBesoins> findByDestination(String destination);
    List<CollecteBesoins> findByStatut(StatutCommande statut);

    List<CollecteBesoins> findByMoisActuel(String moisAnnee);
}
