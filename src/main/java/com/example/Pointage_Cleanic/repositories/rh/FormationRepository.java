package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.entities.rh.Formation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FormationRepository extends MongoRepository<Formation, String> {

    List<Formation> findByActif(boolean actif);

    List<Formation> findByTitreContainingIgnoreCase(String titre);

    List<Formation> findByActifAndTitreContainingIgnoreCase(boolean actif, String titre);
}