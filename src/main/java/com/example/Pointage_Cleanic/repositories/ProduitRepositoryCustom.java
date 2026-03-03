package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.stock.Produit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProduitRepositoryCustom {

    Page<Produit> search(
            String q,
            String category,
            String destination,
            Pageable pageable
    );
}
