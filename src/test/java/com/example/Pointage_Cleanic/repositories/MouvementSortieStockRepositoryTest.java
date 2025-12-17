package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.MotifMouvementSortieStock;
import com.example.Pointage_Cleanic.Enum.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stock.MouvementSortieStock;
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
class MouvementSortieStockRepositoryTest {

    @Autowired
    private MouvementSortieStockRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private MouvementSortieStock createMouvement(
            String codeProduit,
            String nomProduit,
            TypeMouvement type,
            Instant date
    ) {
        return MouvementSortieStock.builder()
                .codeProduit(codeProduit)
                .nomProduit(nomProduit)
                .typeMouvement(type)
                .quantite(10)
                .destination("Agence Dakar")
                .motifSortieStock(MotifMouvementSortieStock.VENTE)
                .responsable("Admin")
                .mois("01-2025")
                .dateMouvement(date)
                .build();
    }

    @Test
    @DisplayName("Doit trouver les mouvements par code produit")
    void shouldFindByCodeProduit() {
        // GIVEN
        repository.save(createMouvement(
                "P-001", "Savon", TypeMouvement.SORTIE, Instant.now()
        ));

        // WHEN
        List<MouvementSortieStock> result =
                repository.findByCodeProduit("P-001");

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNomProduit())
                .isEqualTo("Savon");
    }

    @Test
    @DisplayName("Doit trouver les mouvements par nom produit")
    void shouldFindByNomProduit() {
        // GIVEN
        repository.save(createMouvement(
                "P-002", "Détergent", TypeMouvement.SORTIE, Instant.now()
        ));

        // WHEN
        List<MouvementSortieStock> result =
                repository.findByNomProduit("Détergent");

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCodeProduit())
                .isEqualTo("P-002");
    }

    @Test
    @DisplayName("Doit trouver les mouvements par type de mouvement")
    void shouldFindByTypeMouvement() {
        // GIVEN
        repository.save(createMouvement(
                "P-003", "Produit A", TypeMouvement.SORTIE, Instant.now()
        ));
        repository.save(createMouvement(
                "P-004", "Produit B", TypeMouvement.ENTREE, Instant.now()
        ));

        // WHEN
        List<MouvementSortieStock> result =
                repository.findByTypeMouvement(TypeMouvement.SORTIE);

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTypeMouvement())
                .isEqualTo(TypeMouvement.SORTIE);
    }

    @Test
    @DisplayName("Doit trier les mouvements par date croissante")
    void shouldFindByCodeProduitOrderByDateAsc() {
        // GIVEN
        Instant oldDate = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant newDate = Instant.now();

        repository.save(createMouvement(
                "P-005", "Produit C", TypeMouvement.SORTIE, newDate
        ));
        repository.save(createMouvement(
                "P-005", "Produit C", TypeMouvement.SORTIE, oldDate
        ));

        // WHEN
        List<MouvementSortieStock> result =
                repository.findByCodeProduitOrderByDateMouvementAsc("P-005");

        // THEN
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDateMouvement())
                .isBefore(result.get(1).getDateMouvement());

    }

    @Test
    @DisplayName("Doit trouver les mouvements par type et intervalle de dates")
    void shouldFindByTypeAndDateBetween() {
        // GIVEN
        Instant start = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant middle = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant end = Instant.now();

        repository.save(createMouvement(
                "P-006", "Produit D", TypeMouvement.SORTIE, middle
        ));
        repository.save(createMouvement(
                "P-007", "Produit E", TypeMouvement.ENTREE, middle
        ));

        // WHEN
        List<MouvementSortieStock> result =
                repository.findByTypeMouvementAndDateMouvementBetween(
                        TypeMouvement.SORTIE, start, end
                );

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTypeMouvement())
                .isEqualTo(TypeMouvement.SORTIE);
    }
}
