package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ProduitBulkRequest;
import com.example.Pointage_Cleanic.Dto.stockv2.ProduitBulkResponse;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.repositories.stockv2.CategorieStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class ProduitStockBulkServiceIT extends MongoTestContainer {

    @Autowired private ProduitStockService service;
    @Autowired private StockBalanceService balanceService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private CategorieStockRepository categorieRepository;
    @Autowired private MouvementStockRepository mouvementRepository;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), CategorieStock.class);
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
    }

    private ProduitBulkRequest.LigneImport ligne(int n, String code, Double stockInitial) {
        return ProduitBulkRequest.LigneImport.builder()
                .numeroLigne(n).code(code).libelle("Produit " + code)
                .typeProduit(TypeProduit.CONSOMMABLE).categorieLibelle("Hygiène")
                .unite(UniteStock.PIECE).seuilAlerte(5.0).prixUnitaire(1000L)
                .stockInitial(stockInitial).actif(true).build();
    }

    @Test
    void bulk_succes_cree_produits_categorie_et_stock_initial() {
        ProduitBulkRequest request = ProduitBulkRequest.builder()
                .produits(List.of(ligne(1, "P1", 10.0), ligne(2, "P2", null)))
                .build();

        ProduitBulkResponse response = service.importBulk(request);

        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getInserted()).isEqualTo(2);
        assertThat(response.getFailed()).isZero();
        assertThat(response.getInsertedIds()).hasSize(2);
        assertThat(produitRepository.count()).isEqualTo(2);
        // catégorie créée une seule fois et partagée
        assertThat(categorieRepository.count()).isEqualTo(1);
        // stock initial appliqué pour P1
        String p1 = produitRepository.findByCode("P1").orElseThrow().getId();
        assertThat(balanceService.quantiteTotale(p1)).isEqualTo(10.0);
        assertThat(mouvementRepository.count()).isEqualTo(1);
    }

    @Test
    void bulk_une_ligne_invalide_rollback_total() {
        ProduitBulkRequest.LigneImport invalide = ligne(2, "P2", 3.0);
        invalide.setLibelle(null); // libellé manquant -> échec
        ProduitBulkRequest request = ProduitBulkRequest.builder()
                .produits(List.of(ligne(1, "P1", 10.0), invalide))
                .build();

        ProduitBulkResponse response = service.importBulk(request);

        assertThat(response.getInserted()).isZero();
        assertThat(response.getErrors()).isNotEmpty();
        // ROLLBACK total : rien n'est créé
        assertThat(produitRepository.count()).isZero();
        assertThat(categorieRepository.count()).isZero();
        assertThat(mouvementRepository.count()).isZero();
    }

    @Test
    void bulk_code_duplique_dans_le_fichier_detecte() {
        ProduitBulkRequest request = ProduitBulkRequest.builder()
                .produits(List.of(ligne(1, "DUP", null), ligne(2, "DUP", null)))
                .build();

        ProduitBulkResponse response = service.importBulk(request);

        assertThat(response.getInserted()).isZero();
        assertThat(response.getErrors()).anyMatch(e -> e.getField() != null && e.getField().equals("code"));
        assertThat(produitRepository.count()).isZero();
    }
}
