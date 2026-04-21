package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.CategorieProfessionnelle;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CategorieProfessionnelleRepository extends MongoRepository<CategorieProfessionnelle, String> {

    Optional<CategorieProfessionnelle> findByCode(String code);

    List<CategorieProfessionnelle> findByActif(boolean actif);

    boolean existsByCode(String code);
}