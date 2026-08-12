package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.CategoriePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.CategorieStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.AuthentificationTest;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.exception.EntiteReferenceeException;
import com.example.Pointage_Cleanic.repositories.stockv2.CategorieStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class CategorieStockServiceIT extends MongoTestContainer {

    @Autowired private CategorieStockService service;
    @Autowired private CategorieStockRepository categorieRepository;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), CategorieStock.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);

        // La suppression d'une catégorie est réservée au contrôleur de stock /
        // super-administrateur depuis l'ajout des habilitations.
        AuthentificationTest.connecterSuperAdmin(mongoTemplate);
    }

    @org.junit.jupiter.api.AfterEach
    void deconnecter() {
        AuthentificationTest.deconnecter();
    }

    @Test
    void niveau_calcule_depuis_parent() {
        CategorieStockDto racine = service.create(CategoriePayload.builder().libelle("Hygiène").build());
        assertThat(racine.getNiveau()).isZero();

        CategorieStockDto fille = service.create(
                CategoriePayload.builder().libelle("Détergents").parentId(racine.getId()).build());
        assertThat(fille.getNiveau()).isEqualTo(1);

        CategorieStockDto petiteFille = service.create(
                CategoriePayload.builder().libelle("Liquides").parentId(fille.getId()).build());
        assertThat(petiteFille.getNiveau()).isEqualTo(2);
    }

    @Test
    void racines_renseignent_nbEnfants_et_nbProduits() {
        CategorieStockDto racine = service.create(CategoriePayload.builder().libelle("Hygiène").build());
        service.create(CategoriePayload.builder().libelle("Détergents").parentId(racine.getId()).build());
        produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).categorieId(racine.getId()).actif(true).build());

        CategorieStockDto rechargee = service.racines().stream()
                .filter(c -> c.getId().equals(racine.getId())).findFirst().orElseThrow();
        assertThat(rechargee.getNbEnfants()).isEqualTo(1);
        assertThat(rechargee.getNbProduits()).isEqualTo(1);
    }

    @Test
    void delete_categorie_non_vide_refuse() {
        CategorieStockDto racine = service.create(CategoriePayload.builder().libelle("Hygiène").build());
        produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).categorieId(racine.getId()).actif(true).build());

        assertThatThrownBy(() -> service.delete(racine.getId()))
                .isInstanceOf(EntiteReferenceeException.class);
    }

    @Test
    void delete_categorie_vide_ok() {
        CategorieStockDto racine = service.create(CategoriePayload.builder().libelle("Vide").build());
        service.delete(racine.getId());
        assertThat(categorieRepository.findById(racine.getId())).isEmpty();
    }
}
