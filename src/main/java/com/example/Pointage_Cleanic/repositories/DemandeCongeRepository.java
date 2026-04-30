package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.StatutDemande;
import com.example.Pointage_Cleanic.Enum.TypeConge;
import com.example.Pointage_Cleanic.entities.DemandeConge;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface DemandeCongeRepository extends MongoRepository<DemandeConge, String> {

    List<DemandeConge> findByEmployeId(String employeId);

    List<DemandeConge> findByStatut(StatutDemande statut);

    List<DemandeConge> findByEmployeIdAndStatut(String employeId, StatutDemande statut);

    List<DemandeConge> findByType(TypeConge type);

    // Congés qui couvrent une date donnée (dateDebut <= date <= dateFin)
    List<DemandeConge> findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
            StatutDemande statut, LocalDate date, LocalDate dateSame);

    // Congés d'un employé pour une année (pour calcul solde)
    List<DemandeConge> findByEmployeIdAndDateDebutBetween(
            String employeId, LocalDate debut, LocalDate fin);
}