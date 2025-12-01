package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Superviseur;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SuperviseursRepository  extends MongoRepository<Superviseur, String> {

    Optional<Superviseur> findByEmail(String email);
}
