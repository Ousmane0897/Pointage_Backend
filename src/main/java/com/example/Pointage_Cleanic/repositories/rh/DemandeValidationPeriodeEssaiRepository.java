package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutValidation;
import com.example.Pointage_Cleanic.entities.rh.DemandeValidationPeriodeEssai;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DemandeValidationPeriodeEssaiRepository
        extends MongoRepository<DemandeValidationPeriodeEssai, String> {

    List<DemandeValidationPeriodeEssai> findByStatut(StatutValidation statut);

    List<DemandeValidationPeriodeEssai> findByPeriodeEssaiIdAndStatutNotIn(
            String periodeEssaiId, List<StatutValidation> statuts);
}