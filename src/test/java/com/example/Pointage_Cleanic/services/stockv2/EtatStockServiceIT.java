package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.EtatStockDto;
import com.example.Pointage_Cleanic.Dto.stockv2.SeuilPayload;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutStock;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class EtatStockServiceIT extends MongoTestContainer {

    @Autowired private EtatStockService etatService;
    @Autowired private StockBalanceService balanceService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String produitId;
    private static final String SITE_A = "siteA";

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), SiteClient.class);
        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar").ville("Dakar").actif(true).build());
        produitId = produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
    }

    private EtatStockDto consolide() {
        PageResponse<EtatStockDto> page = etatService.list(0, 20, null, null, null, null, null, false);
        return page.content().get(0);
    }

    @Test
    void statut_rupture_sans_stock() {
        EtatStockDto ligne = consolide();
        assertThat(ligne.getStatut()).isEqualTo(StatutStock.RUPTURE);
        assertThat(ligne.getSiteId()).isNull();
        assertThat(ligne.getValeur()).isZero();
    }

    @Test
    void statut_critique_sous_seuil() {
        entrer(3);
        EtatStockDto ligne = consolide();
        assertThat(ligne.getStatut()).isEqualTo(StatutStock.CRITIQUE);
        assertThat(ligne.getQuantite()).isEqualTo(3.0);
        assertThat(ligne.getValeur()).isEqualTo(3000.0);
    }

    @Test
    void statut_ok_au_dessus_du_seuil() {
        entrer(10);
        assertThat(consolide().getStatut()).isEqualTo(StatutStock.OK);
    }

    @Test
    void par_site_renvoie_une_ligne_par_couple() {
        entrer(10);
        PageResponse<EtatStockDto> page = etatService.list(0, 20, null, null, null, null, null, true);
        assertThat(page.content()).hasSize(1);
        EtatStockDto ligne = page.content().get(0);
        assertThat(ligne.getSiteId()).isEqualTo(SITE_A);
        assertThat(ligne.getSiteNom()).isEqualTo("Dakar");
    }

    @Test
    void maj_seuil_global_recalcule_le_statut() {
        entrer(10); // OK avec seuil 5
        EtatStockDto recalc = etatService.majSeuil(
                SeuilPayload.builder().produitId(produitId).seuilAlerte(12).build());
        assertThat(recalc.getSeuilAlerte()).isEqualTo(12.0);
        assertThat(recalc.getStatut()).isEqualTo(StatutStock.CRITIQUE);
    }

    @Test
    void maj_seuil_par_site_cree_override() {
        entrer(10);
        EtatStockDto recalc = etatService.majSeuil(
                SeuilPayload.builder().produitId(produitId).siteId(SITE_A).seuilAlerte(50).build());
        assertThat(recalc.getSiteId()).isEqualTo(SITE_A);
        assertThat(recalc.getStatut()).isEqualTo(StatutStock.CRITIQUE);
    }

    // Les saisies directes étant consolidées, on alimente directement le solde de SITE_A pour tester l'état par site.
    private void entrer(double qte) {
        balanceService.appliquerDelta(produitId, SITE_A, qte);
    }
}
