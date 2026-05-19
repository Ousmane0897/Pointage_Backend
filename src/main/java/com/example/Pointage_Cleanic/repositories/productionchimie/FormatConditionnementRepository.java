package com.example.Pointage_Cleanic.repositories.productionchimie;

import com.example.Pointage_Cleanic.entities.productionchimie.FormatConditionnement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormatConditionnementRepository extends MongoRepository<FormatConditionnement, String> {

    boolean existsByCode(String code);
    List<FormatConditionnement> findByActifTrue();
}