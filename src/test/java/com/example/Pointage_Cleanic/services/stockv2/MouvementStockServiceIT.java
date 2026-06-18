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
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
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
    private static final String SITE_A = "siteA";
    private static final String SITE_B = "siteB";

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), SiteClient.class);

        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar Plateau").ville("Dakar").actif(true).build());
        mongoTemplate.save(SiteClient.builder().id(SITE_B).code("B").nom("Thiès Centre").ville("Thiès").actif(true).build());

        produitId = produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
    }

    private MouvementPayload payload(TypeMouvement type, MotifMouvement motif, double qte, String source, String dest) {
        return MouvementPayload.builder()
                .produitId(produitId).type(type).motif(motif).quantite(qte)
                .siteSourceId(source).siteDestinationId(dest).build();
    }

    @Test
    void entree_credite_le_site_destination_et_denormalise() {
        MouvementStockDto dto = service.create(payload(TypeMouvement.ENTREE, MotifMouvement.ACHAT, 20, null, SITE_A));

        assertThat(dto.getReference()).startsWith("MVT-");
        assertThat(dto.getSiteDestinationNom()).isEqualTo("Dakar Plateau");
        assertThat(dto.getProduitCode()).isEqualTo("P1");
        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(20.0);
    }

    @Test
    void sortie_stock_insuffisant_rejetee_422() {
        assertThatThrownBy(() -> service.create(payload(TypeMouvement.SORTIE, MotifMouvement.CONSOMMATION, 5, SITE_A, null)))
                .isInstanceOf(StockOperationException.class);
        // aucun mouvement ni solde négatif
        assertThat(balanceService.quantite(produitId, SITE_A)).isZero();
    }

    @Test
    void sortie_valide_debite_le_site_source() {
        service.create(payload(TypeMouvement.ENTREE, MotifMouvement.ACHAT, 20, null, SITE_A));
        service.create(payload(TypeMouvement.SORTIE, MotifMouvement.CONSOMMATION, 8, SITE_A, null));
        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(12.0);
    }

    @Test
    void transfert_deplace_entre_sites_solde_total_conserve() {
        service.create(payload(TypeMouvement.ENTREE, MotifMouvement.ACHAT, 20, null, SITE_A));
        service.create(payload(TypeMouvement.TRANSFERT, MotifMouvement.TRANSFERT, 5, SITE_A, SITE_B));

        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(15.0);
        assertThat(balanceService.quantite(produitId, SITE_B)).isEqualTo(5.0);
        assertThat(balanceService.quantiteTotale(produitId)).isEqualTo(20.0);
    }

    @Test
    void entree_sans_destination_invalide() {
        assertThatThrownBy(() -> service.create(payload(TypeMouvement.ENTREE, MotifMouvement.ACHAT, 5, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transfert_meme_site_invalide() {
        assertThatThrownBy(() -> service.create(payload(TypeMouvement.TRANSFERT, MotifMouvement.TRANSFERT, 5, SITE_A, SITE_A)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
