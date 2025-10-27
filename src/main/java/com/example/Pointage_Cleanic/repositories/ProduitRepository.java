package com.example.Pointage_Cleanic.repositories;


import com.example.Pointage_Cleanic.entities.stock.Produit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProduitRepository  extends MongoRepository<Produit,String> {


    Optional<Produit> findByCodeProduit(String codeProduit);

    @Override
    Page<Produit> findAll(Pageable pageable);

    // 🔹 Filtrage + pagination
    Page<Produit> findByCategorieContainingIgnoreCase(String category, Pageable pageable);

    // 🔹 Compter le total filtré
    long countByCategorieContainingIgnoreCase(String category);

    Page<Produit> findByDestinationContainingIgnoreCase(String destination, Pageable pageable);

    Optional<Produit> findByNomProduit(String nomProduit);


}
