package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Utilisateur;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SuperAdminRepository extends MongoRepository<Utilisateur, String> {

    Optional<Utilisateur> findByEmail(String Email);
}
