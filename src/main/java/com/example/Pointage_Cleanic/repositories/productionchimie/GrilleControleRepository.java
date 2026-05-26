package com.example.Pointage_Cleanic.repositories.productionchimie;

import com.example.Pointage_Cleanic.entities.productionchimie.GrilleControle;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrilleControleRepository extends MongoRepository<GrilleControle, String> {
    List<GrilleControle> findByProduitNom(String produitNom);
    Optional<GrilleControle> findFirstByFormulationId(String formulationId);
}
