package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.MotifMouvementEntreeStock;
import com.example.Pointage_Cleanic.Enum.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stock.MouvementEntreeStock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class MouvementEntreeStockRepositoryTest {

    @Autowired
    private MouvementEntreeStockRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private MouvementEntreeStock createMouvement(
            String codeProduit,
            String nomProduit,
            TypeMouvement type,
            Instant date
    ) {
        return MouvementEntreeStock.builder()
                .codeProduit(codeProduit)
                .nomProduit(nomProduit)
                .type(type)
                .quantite(20)
                .responsable("Admin")
                .motifMouvement(MotifMouvementEntreeStock.RECEPTION_FOURNISSEUR)
                .fournisseur("Fournisseur A")
                .numeroFacture("FACT-001")
                .dateMouvement(date)
                .dateDePeremption(date.plus(30, ChronoUnit.DAYS))
                .build();
    }

    @Test
    @DisplayName("Doit trouver les mouvements par code produit")
    void shouldFindByCodeProduit() {
        // GIVEN
        repository.save(createMouvement(
                "P-100", "Riz", TypeMouvement.ENTREE, Instant.now()
        ));

        // WHEN
        List<MouvementEntreeStock> result =
                repository.findByCodeProduit("P-100");

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNomProduit())
                .isEqualTo("Riz");
    }

    @Test
    @DisplayName("Doit trier les mouvements par date croissante")
    void shouldFindByCodeProduitOrderByDateAsc() {
        // GIVEN
        Instant oldDate = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant newDate = Instant.now();

        repository.save(createMouvement(
                "P-101", "Huile", TypeMouvement.ENTREE, newDate
        ));
        repository.save(createMouvement(
                "P-101", "Huile", TypeMouvement.ENTREE, oldDate
        ));

        // WHEN
        List<MouvementEntreeStock> result =
                repository.findByCodeProduitOrderByDateMouvementAsc("P-101");

        // THEN
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDateMouvement())
                .isBefore(result.get(1).getDateMouvement());
    }

    @Test
    @DisplayName("Doit trouver les mouvements par type")
    void shouldFindByType() {
        // GIVEN
        repository.save(createMouvement(
                "P-102", "Produit A", TypeMouvement.ENTREE, Instant.now()
        ));
        repository.save(createMouvement(
                "P-103", "Produit B", TypeMouvement.AJUSTEMENT, Instant.now()
        ));

        // WHEN
        List<MouvementEntreeStock> result =
                repository.findByType(TypeMouvement.ENTREE);

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType())
                .isEqualTo(TypeMouvement.ENTREE);
    }

    @Test
    @DisplayName("Doit trouver les mouvements par type et intervalle de dates")
    void shouldFindByTypeAndDateBetween() {
        // GIVEN
        Instant start = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant middle = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant end = Instant.now();

        repository.save(createMouvement(
                "P-104", "Produit C", TypeMouvement.ENTREE, middle
        ));
        repository.save(createMouvement(
                "P-105", "Produit D", TypeMouvement.AJUSTEMENT, middle
        ));

        // WHEN
        List<MouvementEntreeStock> result =
                repository.findByTypeAndDateMouvementBetween(
                        TypeMouvement.ENTREE, start, end
                );

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType())
                .isEqualTo(TypeMouvement.ENTREE);
    }
}
