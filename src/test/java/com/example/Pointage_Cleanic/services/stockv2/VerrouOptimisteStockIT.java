package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Décision D1.4 : un enregistrement fondé sur une lecture périmée est refusé (409 CONFLIT_VERSION
 * côté API) au lieu d'écraser la modification d'un autre utilisateur.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class VerrouOptimisteStockIT extends MongoTestContainer {

    @Autowired
    private ProduitStockRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void nettoyer() {
        mongoTemplate.remove(new Query(), ProduitStock.class);
    }

    @Test
    void la_seconde_ecriture_sur_une_lecture_perimee_est_refusee() {
        ProduitStock cree = repository.save(ProduitStock.builder().code("VO-1").libelle("Savon").prixUnitaire(100).build());
        assertThat(cree.getVersion()).isZero();

        ProduitStock lectureA = repository.findById(cree.getId()).orElseThrow();
        ProduitStock lectureB = repository.findById(cree.getId()).orElseThrow();

        lectureA.setPrixUnitaire(120);
        repository.save(lectureA);

        lectureB.setPrixUnitaire(90);
        assertThatThrownBy(() -> repository.save(lectureB)).isInstanceOf(OptimisticLockingFailureException.class);

        ProduitStock enBase = repository.findById(cree.getId()).orElseThrow();
        assertThat(enBase.getPrixUnitaire()).as("la première modification est conservée").isEqualTo(120);
        assertThat(enBase.getVersion()).isEqualTo(1L);
    }
}
