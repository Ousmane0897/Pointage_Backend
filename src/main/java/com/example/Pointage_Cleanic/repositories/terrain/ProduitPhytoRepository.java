package com.example.Pointage_Cleanic.repositories.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.CategoriePhyto;
import com.example.Pointage_Cleanic.entities.terrain.ProduitPhytosanitaire;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProduitPhytoRepository extends MongoRepository<ProduitPhytosanitaire, String> {

    boolean existsByNumeroHomologation(String numeroHomologation);

    List<ProduitPhytosanitaire> findByCategorie(CategoriePhyto categorie);
}