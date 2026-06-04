package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutValidationHS;
import com.example.Pointage_Cleanic.Enum.rh.TypeMajoration;
import com.example.Pointage_Cleanic.entities.rh.HeureSupplementaire;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface HeureSupplementaireRepository extends MongoRepository<HeureSupplementaire, String> {

    List<HeureSupplementaire> findByEmployeId(String employeId);

    List<HeureSupplementaire> findByStatut(StatutValidationHS statut);

    List<HeureSupplementaire> findByTypeMajoration(TypeMajoration typeMajoration);

    List<HeureSupplementaire> findByDateBetween(LocalDate debut, LocalDate fin);

    List<HeureSupplementaire> findByEmployeIdAndDateBetween(
            String employeId, LocalDate debut, LocalDate fin);

    List<HeureSupplementaire> findByStatutAndDateBetween(
            StatutValidationHS statut, LocalDate debut, LocalDate fin);
}