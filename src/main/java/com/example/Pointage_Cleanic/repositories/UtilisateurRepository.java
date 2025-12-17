package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Utilisateur;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UtilisateurRepository extends MongoRepository<Utilisateur, String> {


    Optional<Utilisateur> findByEmail(String email);
}
