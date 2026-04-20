package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.StatutContrat;
import com.example.Pointage_Cleanic.entities.Contrat;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ContratRepository extends MongoRepository<Contrat, String> {

    List<Contrat> findByEmployeId(String employeId);

    List<Contrat> findByStatut(StatutContrat statut);

    List<Contrat> findByStatutAndDateFinIsNotNull(StatutContrat statut);
}