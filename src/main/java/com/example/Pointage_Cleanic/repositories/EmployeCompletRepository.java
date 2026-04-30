package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.EmployeComplet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeCompletRepository extends MongoRepository<EmployeComplet, String> {

    Optional<EmployeComplet> findByMatricule( String matricule);

    Optional<EmployeComplet> findByAgentId(String agentId);

    boolean existsByAgentId(String agentId);

    boolean existsByMatricule(String matricule);

    boolean existsByNomComplet(String nomComplet);


    Optional<EmployeComplet> findByPrenom(String prenom);

    Optional<EmployeComplet> findByPrenomAndNom(String prenom, String nom);

    Page<EmployeComplet> findAll(Pageable pageable);

    Page<EmployeComplet> findByPrenomContainingIgnoreCaseOrNomContainingIgnoreCaseOrMatriculeContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String prenom, String nom, String matricule, String email, Pageable pageable
    );

    // RH — filtres par statut
    Page<EmployeComplet> findByStatut(EmployeComplet.StatutEmploye statut, Pageable pageable);

    List<EmployeComplet> findByStatut(EmployeComplet.StatutEmploye statut);

    List<EmployeComplet> findByStatutAndAgenceContaining(EmployeComplet.StatutEmploye statut, String agence);
}
