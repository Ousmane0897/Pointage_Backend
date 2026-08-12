package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {


    Optional<User> findByEmail(String email);

    /**
     * Comptes de connexion portant un rôle donné (le rôle est une chaîne libre dans la
     * collection {@code login} : c'est le seul endroit où existe {@code SUPERADMIN}).
     */
    List<User> findByRoleIgnoreCase(String role);
}
