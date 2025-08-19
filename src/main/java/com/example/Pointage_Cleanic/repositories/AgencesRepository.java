package com.example.Pointage_Cleanic.repositories;


import com.example.Pointage_Cleanic.entities.Agence;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgencesRepository extends MongoRepository<Agence,String> {

    // Retourne directement joursOuverture pour éviter de construire un Agence partiel
    @Query(value = "{ 'nom' : ?0 }", fields = "{ 'joursOuverture' : 1, '_id' : 0 }")
    Optional<String> findJoursOuvertureByNom(String nom);


}
