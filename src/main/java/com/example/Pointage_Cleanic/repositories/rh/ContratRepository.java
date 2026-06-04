package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutContrat;
import com.example.Pointage_Cleanic.entities.rh.Contrat;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ContratRepository extends MongoRepository<Contrat, String> {

    List<Contrat> findByEmployeId(String employeId);

    List<Contrat> findByStatut(StatutContrat statut);

    List<Contrat> findByStatutAndDateFinIsNotNull(StatutContrat statut);
}