package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.StatutAbsence;
import com.example.Pointage_Cleanic.Enum.TypeAbsence;
import com.example.Pointage_Cleanic.entities.RhAbsence;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface RhAbsenceRepository extends MongoRepository<RhAbsence, String> {

    List<RhAbsence> findByEmployeId(String employeId);

    List<RhAbsence> findByStatut(StatutAbsence statut);

    List<RhAbsence> findByType(TypeAbsence type);

    List<RhAbsence> findByDateDebutGreaterThanEqualAndDateFinLessThanEqual(
            LocalDate dateDebut, LocalDate dateFin);

    List<RhAbsence> findByEmployeIdAndDateDebutGreaterThanEqualAndDateFinLessThanEqual(
            String employeId, LocalDate dateDebut, LocalDate dateFin);
}