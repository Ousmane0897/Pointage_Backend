package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.Enum.rh.CategorieDocument;
import com.example.Pointage_Cleanic.entities.rh.DocumentEmploye;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DocumentEmployeRepository extends MongoRepository<DocumentEmploye, String> {

    Page<DocumentEmploye> findByEmployeId(String employeId, Pageable pageable);

    Page<DocumentEmploye> findByCategorie(CategorieDocument categorie, Pageable pageable);

    Page<DocumentEmploye> findByEmployeIdAndCategorie(
            String employeId, CategorieDocument categorie, Pageable pageable);

    List<DocumentEmploye> findByEmployeId(String employeId);
}
