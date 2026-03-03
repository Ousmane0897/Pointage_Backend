package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.stock.Produit;
import com.example.Pointage_Cleanic.repositories.ProduitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProduitService {

    private final ProduitRepository produitRepository;

    Produit saveProduit(Produit produit) {
        return produitRepository.save(produit);
    }

    Optional<Produit> getProduitById(String id) {
        return produitRepository.findById(id);
    }

    public Map<String, Object> getProductsByCategory(String category, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Produit> produitsPage;

        if (category != null && !category.isEmpty()) {
            produitsPage = produitRepository.findByCategorieContainingIgnoreCase(category, pageable);
        } else {
            produitsPage = produitRepository.findAll(pageable);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("content", produitsPage.getContent());
        response.put("total", produitsPage.getTotalElements()); // ✅ total auto
        return response;
    }

    public Map<String, Object> getProductsByDestination(String category, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Produit> produitsPage;

        if (category != null && !category.isEmpty()) {
            produitsPage = produitRepository.findByDestinationContainingIgnoreCase(category, pageable);
        } else {
            produitsPage = produitRepository.findAll(pageable);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("content", produitsPage.getContent());
        response.put("total", produitsPage.getTotalElements()); // ✅ total auto
        return response;
    }

    public Optional<Produit> findByCodeProduit(String codeProduit) {
        return produitRepository.findByCodeProduit(codeProduit);
    }

    public Page<Produit> search(
            String q,
            String category,
            String destination,
            Pageable pageable
    ) {
        return produitRepository.search(
                q,
                category,
                destination,
                pageable
        );
    }

    public Optional<Produit> findByNomProduit(String nomProduit) {

        return produitRepository.findByNomProduit(nomProduit);
    }


}
