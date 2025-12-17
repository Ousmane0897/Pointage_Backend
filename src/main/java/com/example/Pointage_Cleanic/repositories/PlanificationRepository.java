package com.example.Pointage_Cleanic.repositories;


import com.example.Pointage_Cleanic.entities.Planification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface PlanificationRepository extends MongoRepository<Planification, String> {

    List<Planification> findByStatutIn(List<String> statuts);
    // Trouver toutes les planifications dont le statut est EN_ATTENTE_VALIDATION
    List<Planification> findByStatut(Planification.Statut statut);


}
