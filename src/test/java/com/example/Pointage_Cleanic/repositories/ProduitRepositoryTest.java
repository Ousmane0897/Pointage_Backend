package com.example.Pointage_Cleanic.repositories;


import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stock.Produit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class ProduitRepositoryTest extends MongoTestContainer {

    @Autowired
    private ProduitRepository produitRepository;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
    }


    private Produit createProduit(
            String code,
            String nom,
            String categorie,
            String destination
    ) {
        return Produit.builder()
                .codeProduit(code)
                .nomProduit(nom)
                .description("Description test")
                .categorie(categorie)
                .destination(destination)
                .uniteDeMesure("pièce")
                .conditionnement("carton")
                .prixDeVente(2500.0)
                .emplacement("Entrepôt A")
                .seuilMinimum(10)
                .statut("ACTIF")
                .quantiteSnapshot(100)
                .build();
    }

    @Test
    void shouldFindByCodeProduit() {
        produitRepository.save(createProduit("P-001", "Savon liquide", "Hygiène", "Agence"));

        Optional<Produit> result = produitRepository.findByCodeProduit("P-001");

        assertThat(result).isPresent();
    }

    @Test
    void shouldFindByNomProduit() {
        produitRepository.save(createProduit("P-002", "Détergent", "Nettoyage", "Entrepôt"));

        Optional<Produit> result = produitRepository.findByNomProduit("Détergent");

        assertThat(result).isPresent();
    }

    @Test
    void shouldFilterByCategorieIgnoreCase() {
        produitRepository.save(createProduit("P-005", "Produit C", "Hygiène", "Site A"));
        produitRepository.save(createProduit("P-006", "Produit D", "Hygiène", "Site B"));
        produitRepository.save(createProduit("P-007", "Produit E", "Informatique", "Site C"));

        Page<Produit> page =
                produitRepository.findByCategorieContainingIgnoreCase(
                        "hyg", PageRequest.of(0, 10)
                );

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void shouldCountByCategorieIgnoreCase() {
        produitRepository.save(createProduit("P-008", "Produit F", "Hygiène", "Site A"));
        produitRepository.save(createProduit("P-009", "Produit G", "Hygiène", "Site B"));

        long count =
                produitRepository.countByCategorieContainingIgnoreCase("HYGI");

        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldFilterByDestinationIgnoreCase() {
        produitRepository.save(createProduit("P-010", "Produit H", "Nettoyage", "Dakar"));
        produitRepository.save(createProduit("P-011", "Produit I", "Nettoyage", "Thiès"));

        Page<Produit> page =
                produitRepository.findByDestinationContainingIgnoreCase(
                        "dak", PageRequest.of(0, 10)
                );

        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}

