package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.entities.rh.GrilleEvaluation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GrilleEvaluationRepository extends MongoRepository<GrilleEvaluation, String> {

    List<GrilleEvaluation> findByActif(boolean actif);

    Optional<GrilleEvaluation> findFirstByActifOrderByDateCreationDesc(boolean actif);
}