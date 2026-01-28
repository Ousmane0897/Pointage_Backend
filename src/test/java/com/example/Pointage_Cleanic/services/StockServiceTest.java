package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.MouvementStock;
import com.example.Pointage_Cleanic.Enum.MotifMouvementSortieStock;
import com.example.Pointage_Cleanic.Enum.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stock.MouvementEntreeStock;
import com.example.Pointage_Cleanic.entities.stock.MouvementSortieStock;
import com.example.Pointage_Cleanic.entities.stock.Produit;
import com.example.Pointage_Cleanic.repositories.MouvementEntreeStockRepository;
import com.example.Pointage_Cleanic.repositories.MouvementSortieStockRepository;
import com.example.Pointage_Cleanic.repositories.ProduitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private ProduitRepository produitRepository;

    @Mock
    private MouvementEntreeStockRepository entreeRepo;

    @Mock
    private MouvementSortieStockRepository sortieRepo;

    @Mock
    private MongoOperations mongoOperations;

    @InjectMocks
    private StockService stockService;

    // ---------------------------------------------------
    // 1️⃣ enregistrerMouvement() — ENTRÉE
    // ---------------------------------------------------
    @Test
    void testEnregistrerMouvement_Entree() {

        Produit produit = new Produit();
        produit.setCodeProduit("P1");
        produit.setQuantiteSnapshot(10);

        MouvementEntreeStock mvt = new MouvementEntreeStock();
        mvt.setCodeProduit("P1");
        mvt.setQuantite(5);
        mvt.setType(TypeMouvement.ENTREE);

        when(produitRepository.findByCodeProduit("P1"))
                .thenReturn(Optional.of(produit));

        when(entreeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        MouvementEntreeStock result = stockService.enregistrerMouvement(mvt);

        assertEquals(15, produit.getQuantiteSnapshot());
        verify(produitRepository).save(produit);
        verify(entreeRepo).save(mvt);
        assertNotNull(result);
    }

    // ---------------------------------------------------
    // 2️⃣ enregistrerMouvement() — AJUSTEMENT négatif
    // ---------------------------------------------------
    @Test
    void testEnregistrerMouvement_AjustementNegatif() {

        Produit produit = new Produit();
        produit.setCodeProduit("P2");
        produit.setQuantiteSnapshot(3);

        MouvementEntreeStock mvt = new MouvementEntreeStock();
        mvt.setCodeProduit("P2");
        mvt.setQuantite(-10);
        mvt.setType(TypeMouvement.AJUSTEMENT);

        when(produitRepository.findByCodeProduit("P2"))
                .thenReturn(Optional.of(produit));

        stockService.enregistrerMouvement(mvt);

        assertEquals(0, produit.getQuantiteSnapshot()); // jamais négatif
    }

    // ---------------------------------------------------
    // 3️⃣ calculerStockTheoriqueFromMovements()
    // ---------------------------------------------------
    @Test
    void testCalculerStockTheorique() {

        MouvementEntreeStock e1 = new MouvementEntreeStock();
        e1.setType(TypeMouvement.ENTREE);
        e1.setQuantite(10);

        MouvementEntreeStock e2 = new MouvementEntreeStock();
        e2.setType(TypeMouvement.AJUSTEMENT);
        e2.setQuantite(20);

        MouvementSortieStock s1 = new MouvementSortieStock();
        s1.setTypeMouvement(TypeMouvement.SORTIE);
        s1.setQuantite(5);

        when(entreeRepo.findByCodeProduitOrderByDateMouvementAsc("P3"))
                .thenReturn(List.of(e1, e2));

        when(sortieRepo.findByCodeProduitOrderByDateMouvementAsc("P3"))
                .thenReturn(List.of(s1));

        int stock = stockService.calculerStockTheoriqueFromMovements("P3");

        assertEquals(15, stock); // ajustement 20 - 5
    }

    // ---------------------------------------------------
    // 4️⃣ getStockCurrent() — snapshot prioritaire
    // ---------------------------------------------------
    @Test
    void testGetStockCurrent_Snapshot() {

        Produit produit = new Produit();
        produit.setCodeProduit("P4");
        produit.setQuantiteSnapshot(50);

        when(produitRepository.findByCodeProduit("P4"))
                .thenReturn(Optional.of(produit));

        int stock = stockService.getStockCurrent("P4");

        assertEquals(50, stock);
    }

    // ---------------------------------------------------
    // 5️⃣ sortieSimple() — OK
    // ---------------------------------------------------
    @Test
    void testSortieSimple_OK() {

        Produit produit = new Produit();
        produit.setCodeProduit("P5");
        produit.setNomProduit("Savon");
        produit.setQuantiteSnapshot(20);

        MouvementSortieStock sortie = new MouvementSortieStock();
        sortie.setCodeProduit("P5");
        sortie.setQuantite(5);

        when(produitRepository.findByCodeProduit("P5"))
                .thenReturn(Optional.of(produit));

        when(sortieRepo.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        MouvementSortieStock result = stockService.sortieSimple(sortie);

        assertEquals(15, produit.getQuantiteSnapshot());
        assertEquals(TypeMouvement.SORTIE, result.getTypeMouvement());
    }

    // ---------------------------------------------------
    // 6️⃣ sortieSimple() — stock insuffisant
    // ---------------------------------------------------
    @Test
    void testSortieSimple_StockInsuffisant() {

        Produit produit = new Produit();
        produit.setCodeProduit("P6");
        produit.setQuantiteSnapshot(2);

        MouvementSortieStock sortie = new MouvementSortieStock();
        sortie.setCodeProduit("P6");
        sortie.setQuantite(10);

        when(produitRepository.findByCodeProduit("P6"))
                .thenReturn(Optional.of(produit));

        assertThrows(IllegalArgumentException.class,
                () -> stockService.sortieSimple(sortie));
    }

    // ---------------------------------------------------
    // 7️⃣ sortieBatch() — OK
    // ---------------------------------------------------
    @Test
    void testSortieBatch_OK() {

        Produit produit = new Produit();
        produit.setCodeProduit("P7");
        produit.setNomProduit("Balai");
        produit.setQuantiteSnapshot(30);

        MouvementStock dto = new MouvementStock();
        dto.setCodeProduit("P7");
        dto.setQuantite(10);

        when(produitRepository.findByCodeProduit("P7"))
                .thenReturn(Optional.of(produit));

        when(sortieRepo.saveAll(any()))
                .thenAnswer(i -> i.getArgument(0));

        List<MouvementSortieStock> result =
                stockService.sortieBatch(
                        List.of(dto),
                        "Dakar",
                        "Admin",
                        "Ousmane",
                        TypeMouvement.SORTIE,
                        MotifMouvementSortieStock.AGENCE
                );

        assertEquals(20, produit.getQuantiteSnapshot());
        assertEquals(1, result.size());
    }


    // ---------------------------------------------------
    // 8️⃣ isUnderReorderPoint()
    // ---------------------------------------------------
    @Test
    void testIsUnderReorderPoint() {

        Produit produit = new Produit();
        produit.setId("ID1");
        produit.setCodeProduit("P8");
        produit.setQuantiteSnapshot(5);
        produit.setSeuilMinimum(10);

        when(produitRepository.findById("ID1"))
                .thenReturn(Optional.of(produit));

        when(produitRepository.findByCodeProduit("ID1"))
                .thenReturn(Optional.of(produit));

        boolean result = stockService.isUnderReorderPoint("ID1");

        assertTrue(result);
    }
}
