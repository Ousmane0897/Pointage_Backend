package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.entities.stock.Produit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmployeCompletRepository extends MongoRepository<EmployeComplet, String> {

    Optional<EmployeComplet> findByMatricule( String matricule);

    Optional<EmployeComplet> findByAgentId(String agentId);

    Optional<EmployeComplet> findByPrenom(String prenom);

    Optional<EmployeComplet> findByPrenomAndNom(String prenom, String nom);

    Page<EmployeComplet> findAll(Pageable pageable);

    Page<EmployeComplet> findByPrenomContainingIgnoreCaseOrNomContainingIgnoreCaseOrMatriculeContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String prenom, String nom, String matricule, String email, Pageable pageable
    );
}
