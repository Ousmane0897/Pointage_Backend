package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.MouvementPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.MouvementStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.exception.StockOperationException;
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
class MouvementStockServiceIT extends MongoTestContainer {

    @Autowired private MouvementStockService service;
    @Autowired private StockBalanceService balanceService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String produitId;

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);

        produitId = produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
    }

    // Les saisies directes sont consolidées (aucun site dans le payload) : impact sur le bucket siteId=null.
    private MouvementPayload payload(TypeMouvement type, MotifMouvement motif, double qte) {
        return MouvementPayload.builder()
                .produitId(produitId).type(type).motif(motif).quantite(qte).build();
    }

    @Test
    void entree_credite_le_stock_consolide_et_denormalise() {
        MouvementStockDto dto = service.create(payload(TypeMouvement.ENTREE, MotifMouvement.ACHAT, 20));

        assertThat(dto.getReference()).startsWith("MVT-");
        assertThat(dto.getProduitCode()).isEqualTo("P1");
        assertThat(balanceService.quantite(produitId, null)).isEqualTo(20.0);
        assertThat(balanceService.quantiteTotale(produitId)).isEqualTo(20.0);
    }

    @Test
    void sortie_stock_insuffisant_rejetee_422() {
        assertThatThrownBy(() -> service.create(payload(TypeMouvement.SORTIE, MotifMouvement.CONSOMMATION, 5)))
                .isInstanceOf(StockOperationException.class);
        // aucun mouvement ni solde négatif
        assertThat(balanceService.quantite(produitId, null)).isZero();
    }

    @Test
    void sortie_valide_debite_le_stock() {
        service.create(payload(TypeMouvement.ENTREE, MotifMouvement.ACHAT, 20));
        service.create(payload(TypeMouvement.SORTIE, MotifMouvement.CONSOMMATION, 8));
        assertThat(balanceService.quantite(produitId, null)).isEqualTo(12.0);
    }

    @Test
    void ajustement_valide_en_entree_comme_en_sortie() {
        service.create(payload(TypeMouvement.ENTREE, MotifMouvement.AJUSTEMENT, 10));
        service.create(payload(TypeMouvement.SORTIE, MotifMouvement.AJUSTEMENT, 3));
        assertThat(balanceService.quantite(produitId, null)).isEqualTo(7.0);
    }

    @Test
    void combinaison_entree_motif_invalide_rejetee() {
        // VENTE n'est pas un motif d'ENTREE valide.
        assertThatThrownBy(() -> service.create(payload(TypeMouvement.ENTREE, MotifMouvement.VENTE, 5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(balanceService.quantiteTotale(produitId)).isZero();
    }

    @Test
    void combinaison_sortie_motif_invalide_rejetee() {
        // ACHAT n'est pas un motif de SORTIE valide.
        assertThatThrownBy(() -> service.create(payload(TypeMouvement.SORTIE, MotifMouvement.ACHAT, 5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
