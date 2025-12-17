package com.example.Pointage_Cleanic.repositories;


import com.example.Pointage_Cleanic.entities.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginRepository extends MongoRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    void deleteByEmail(String email);

}
