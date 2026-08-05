package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UtilisateurRepository extends MongoRepository<Utilisateur, String> {


    Optional<Utilisateur> findByEmail(String email);

    List<Utilisateur> findAllByEmail(String email);

    // Destinataires des notifications par rôle (comptes actifs uniquement).
    List<Utilisateur> findByRoleAndActiveTrue(RoleAdmin role);
}
