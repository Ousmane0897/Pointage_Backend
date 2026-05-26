package com.example.Pointage_Cleanic.repositories.productionchimie;

import com.example.Pointage_Cleanic.Enum.StatutFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.FicheFormulation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormulationRepository extends MongoRepository<FicheFormulation, String> {

    boolean existsByCode(String code);
    List<FicheFormulation> findByStatut(StatutFormulation statut);
}