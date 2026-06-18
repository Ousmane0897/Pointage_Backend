package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.MatriceComparatifDto;
import com.example.Pointage_Cleanic.Enum.stockv2.AxeComparatif;
import com.example.Pointage_Cleanic.Enum.stockv2.SensEvolution;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class ComparatifAnalyseServiceIT extends MongoTestContainer {

    @Autowired private ComparatifAnalyseService comparatifService;
    @Autowired private BonSortieService bonSortieService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String produitId;
    private static final String SITE_A = "siteA";
    private static final String SITE_B = "siteB";

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), BonSortie.class);
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), SiteClient.class);

        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar Plateau").ville("Dakar").actif(true).build());
        mongoTemplate.save(SiteClient.builder().id(SITE_B).code("B").nom("Thiès Centre").ville("Thiès").actif(true).build());
        produitId = produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
        mongoTemplate.save(StockParSite.builder().produitId(produitId).siteId(SITE_A).quantite(1000).build());
        mongoTemplate.save(StockParSite.builder().produitId(produitId).siteId(SITE_B).quantite(1000).build());
    }

    private void sortieValide(String siteId, double qte, LocalDate date) {
        BonSortiePayload payload = BonSortiePayload.builder()
                .type(TypeSortie.CONSOMMATION_INTERNE)
                .date(date)
                .siteSourceId(siteId)
                .destinataire(DestinatairePayload.builder().type(TypeDestinataire.CLIENT).clientNom("C").build())
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build();
        String id = bonSortieService.creer(payload).getId();
        bonSortieService.soumettre(id);
        bonSortieService.valider(id, null);
    }

    private MatriceComparatifDto.LigneComparatif ligne(MatriceComparatifDto matrice, String cleId) {
        return matrice.getLignes().stream().filter(l -> l.getCleId().equals(cleId)).findFirst().orElseThrow();
    }

    @Test
    void calcule_sens_evolution_alertes_et_valorisation_fcfa() {
        sortieValide(SITE_A, 5, LocalDate.of(2026, 4, 10));    // 5000
        sortieValide(SITE_A, 10, LocalDate.of(2026, 5, 10));   // 10000 -> +100% > 20 => ALERTE
        sortieValide(SITE_B, 10, LocalDate.of(2026, 4, 10));   // 10000
        sortieValide(SITE_B, 5, LocalDate.of(2026, 5, 10));    // 5000 -> -50% => BAISSE

        MatriceComparatifDto matrice = comparatifService.comparatif(
                AxeComparatif.SITE, "2026-04", "2026-05", null, null, null, 20.0);

        assertThat(matrice.getMois()).containsExactly("2026-04", "2026-05");
        assertThat(matrice.getNbAlertes()).isEqualTo(1);
        assertThat(matrice.getTotalGeneral()).isEqualTo(30_000L);
        assertThat(matrice.getTotauxParMois()).containsExactly(15_000L, 15_000L);

        MatriceComparatifDto.LigneComparatif siteA = ligne(matrice, SITE_A);
        assertThat(siteA.getCellules().get(0).getValeur()).isEqualTo(5_000L);  // valorisation : 5 × 1000
        assertThat(siteA.getCellules().get(0).getSens()).isEqualTo(SensEvolution.STABLE);
        assertThat(siteA.getCellules().get(0).getEvolutionPct()).isNull();
        assertThat(siteA.getCellules().get(1).getSens()).isEqualTo(SensEvolution.ALERTE);
        assertThat(siteA.getCellules().get(1).getEvolutionPct()).isEqualTo(100.0);
        assertThat(siteA.getTotal()).isEqualTo(15_000L);

        MatriceComparatifDto.LigneComparatif siteB = ligne(matrice, SITE_B);
        assertThat(siteB.getCellules().get(1).getSens()).isEqualTo(SensEvolution.BAISSE);
        assertThat(siteB.getCellules().get(1).getEvolutionPct()).isEqualTo(-50.0);
        assertThat(siteB.getSensGlobal()).isEqualTo(SensEvolution.BAISSE);
    }
}
