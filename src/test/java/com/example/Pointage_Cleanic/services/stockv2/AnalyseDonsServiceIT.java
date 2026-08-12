package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.SyntheseDonsDto;
import com.example.Pointage_Cleanic.Enum.stockv2.NatureDon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.AuthentificationTest;
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
class AnalyseDonsServiceIT extends MongoTestContainer {

    @Autowired private AnalyseDonsService donsService;
    @Autowired private BonSortieService bonSortieService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String produitId;
    private static final String SITE_A = "siteA";

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), BonSortie.class);
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), SiteClient.class);

        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar Plateau").ville("Dakar").actif(true).build());
        produitId = produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
        mongoTemplate.save(StockParSite.builder().produitId(produitId).siteId(SITE_A).quantite(1000).build());

        // Les décisions du circuit sont réservées au super-administrateur : sans session,
        // valider/refuser renverraient 403 et ce test ne pourrait plus préparer ses données.
        AuthentificationTest.connecterSuperAdmin(mongoTemplate);
    }

    @org.junit.jupiter.api.AfterEach
    void deconnecter() {
        AuthentificationTest.deconnecter();
    }

    private void donValide(NatureDon nature, String beneficiaire, double qte, LocalDate date) {
        BonSortiePayload payload = BonSortiePayload.builder()
                .type(TypeSortie.DON)
                .date(date)
                .siteSourceId(SITE_A)
                .destinataire(DestinatairePayload.builder().type(TypeDestinataire.CLIENT).clientNom(beneficiaire).build())
                .natureDon(nature)
                .beneficiaireDon(beneficiaire)
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build();
        String id = bonSortieService.creer(payload).getId();
        bonSortieService.soumettre(id);
        bonSortieService.valider(id, null);
    }

    @Test
    void agrege_les_dons_par_nature_et_beneficiaire() {
        donValide(NatureDon.CADEAU_CLIENT, "Client X", 5, LocalDate.of(2026, 6, 10));   // 5000
        donValide(NatureDon.CADEAU_CLIENT, "Client Y", 3, LocalDate.of(2026, 6, 12));   // 3000
        donValide(NatureDon.ECHANTILLON, "Client X", 2, LocalDate.of(2026, 6, 15));     // 2000

        SyntheseDonsDto synthese = donsService.synthese(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, null);

        assertThat(synthese.getKpis().getMontantTotal()).isEqualTo(10_000L);
        assertThat(synthese.getKpis().getNbDons()).isEqualTo(3);
        assertThat(synthese.getKpis().getNbBeneficiaires()).isEqualTo(2);
        assertThat(synthese.getLignes()).hasSize(3);

        SyntheseDonsDto.RepartitionItem cadeau = synthese.getRepartitionNature().stream()
                .filter(r -> r.getLibelle().equals(NatureDon.CADEAU_CLIENT.name())).findFirst().orElseThrow();
        assertThat(cadeau.getMontant()).isEqualTo(8_000L);
        assertThat(cadeau.getNombre()).isEqualTo(2);

        SyntheseDonsDto.RepartitionItem echantillon = synthese.getRepartitionNature().stream()
                .filter(r -> r.getLibelle().equals(NatureDon.ECHANTILLON.name())).findFirst().orElseThrow();
        assertThat(echantillon.getMontant()).isEqualTo(2_000L);

        // topBeneficiaires trié desc : Client X (7000) avant Client Y (3000)
        assertThat(synthese.getTopBeneficiaires().get(0).getLibelle()).isEqualTo("Client X");
        assertThat(synthese.getTopBeneficiaires().get(0).getMontant()).isEqualTo(7_000L);
    }

    @Test
    void filtre_par_nature_de_don() {
        donValide(NatureDon.CADEAU_CLIENT, "Client X", 5, LocalDate.of(2026, 6, 10));
        donValide(NatureDon.ECHANTILLON, "Client X", 2, LocalDate.of(2026, 6, 15));

        SyntheseDonsDto synthese = donsService.synthese(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), NatureDon.ECHANTILLON, null, null);

        assertThat(synthese.getKpis().getNbDons()).isEqualTo(1);
        assertThat(synthese.getKpis().getMontantTotal()).isEqualTo(2_000L);
    }
}
