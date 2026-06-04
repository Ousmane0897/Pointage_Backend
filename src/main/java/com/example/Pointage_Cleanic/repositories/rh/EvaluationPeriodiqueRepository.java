package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutEvaluation;
import com.example.Pointage_Cleanic.entities.rh.EvaluationPeriodique;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationPeriodiqueRepository extends MongoRepository<EvaluationPeriodique, String> {

    List<EvaluationPeriodique> findByEmployeId(String employeId);

    List<EvaluationPeriodique> findByPeriode(String periode);

    List<EvaluationPeriodique> findByStatut(StatutEvaluation statut);

    List<EvaluationPeriodique> findByDepartement(String departement);

    List<EvaluationPeriodique> findByDepartementAndPeriode(String departement, String periode);

    Optional<EvaluationPeriodique> findByEmployeIdAndPeriode(String employeId, String periode);

    long countByStatut(StatutEvaluation statut);

    long countByPeriodeAndStatut(String periode, StatutEvaluation statut);
}