package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.StatutDeclaration;
import com.example.Pointage_Cleanic.Enum.TypeDeclaration;
import com.example.Pointage_Cleanic.entities.DeclarationSociale;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DeclarationSocialeRepository extends MongoRepository<DeclarationSociale, String> {

    List<DeclarationSociale> findByType(TypeDeclaration type);

    List<DeclarationSociale> findByAnnee(int annee);

    List<DeclarationSociale> findByStatut(StatutDeclaration statut);

    List<DeclarationSociale> findByTypeAndMoisAndAnnee(TypeDeclaration type, Integer mois, int annee);

    List<DeclarationSociale> findByTypeAndAnnee(TypeDeclaration type, int annee);
}