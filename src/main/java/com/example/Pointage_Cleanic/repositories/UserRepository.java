package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {


    Optional<User> findByEmail(String email);
}
