package com.example.Pointage_Cleanic.repositories.productionchimie;

import com.example.Pointage_Cleanic.entities.productionchimie.MatierePremiere;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatierePremiereRepository extends MongoRepository<MatierePremiere, String> {

    Optional<MatierePremiere> findByCode(String code);
    boolean existsByCode(String code);
    List<MatierePremiere> findByActifTrue();
}