package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Admins;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SuperAdminRepository extends MongoRepository<Admins, String> {

    Optional<Admins> findByEmail(String Email);
}
