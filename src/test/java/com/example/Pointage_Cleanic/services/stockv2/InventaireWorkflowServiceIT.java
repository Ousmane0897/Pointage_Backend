package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ComptagePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.InventaireDto;
import com.example.Pointage_Cleanic.Dto.stockv2.InventairePlanifPayload;
import com.example.Pointage_Cleanic.Enum.stockv2.PerimetreInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.Inventaire;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.exception.StockOperationException;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class InventaireWorkflowServiceIT extends MongoTestContainer {

    @Autowired private InventaireService inventaireService;
    @Autowired private StockBalanceService balanceService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MouvementStockRepository mouvementRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String produitId;
    private static final String SITE_A = "siteA";

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), Inventaire.class);
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), SiteClient.class);
        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar").ville("Dakar").actif(true).build());
        produitId = produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
        // stock système = 20 sur SITE_A (les saisies directes étant consolidées, on alimente le solde du site directement)
        balanceService.appliquerDelta(produitId, SITE_A, 20);
    }

    private InventaireDto creerInventaire() {
        return inventaireService.create(InventairePlanifPayload.builder()
                .libelle("Inventaire mensuel").datePlanifiee(LocalDate.now())
                .siteId(SITE_A).perimetre(PerimetreInventaire.SELECTION)
                .produitIds(List.of(produitId)).seuilEcartJustification(2).build());
    }

    private void saisir(String invId, Double qtePhysique, String justification) {
        inventaireService.enregistrerComptage(invId, ComptagePayload.builder()
                .lignes(List.of(ComptagePayload.LigneComptage.builder()
                        .produitId(produitId).qtePhysique(qtePhysique).justification(justification).build()))
                .build());
    }

    @Test
    void comptage_fige_la_quantite_theorique_au_stock_systeme() {
        InventaireDto inv = creerInventaire();
        assertThat(inv.getStatut()).isEqualTo(StatutInventaire.BROUILLON);
        assertThat(inv.getLignes().get(0).getQteTheorique()).isZero();

        InventaireDto enComptage = inventaireService.demarrerComptage(inv.getId());
        assertThat(enComptage.getStatut()).isEqualTo(StatutInventaire.COMPTAGE);
        assertThat(enComptage.getLignes().get(0).getQteTheorique()).isEqualTo(20.0);
    }

    @Test
    void validation_refuse_ecart_non_justifie() {
        InventaireDto inv = creerInventaire();
        inventaireService.demarrerComptage(inv.getId());
        saisir(inv.getId(), 15.0, null); // écart -5, |5| > seuil 2, sans justification

        assertThatThrownBy(() -> inventaireService.valider(inv.getId()))
                .isInstanceOf(StockOperationException.class);
    }

    @Test
    void cloture_applique_les_ecarts_au_stock_via_ajustement() {
        InventaireDto inv = creerInventaire();
        inventaireService.demarrerComptage(inv.getId());
        saisir(inv.getId(), 15.0, "Casse constatée");

        InventaireDto valide = inventaireService.valider(inv.getId());
        assertThat(valide.getStatut()).isEqualTo(StatutInventaire.VALIDATION);

        long mvtsAvant = mouvementRepository.count();
        InventaireDto cloture = inventaireService.cloturer(inv.getId());

        assertThat(cloture.getStatut()).isEqualTo(StatutInventaire.CLOTURE);
        assertThat(cloture.getDateCloture()).isNotNull();
        // stock ajusté à la quantité physique
        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(15.0);
        // un mouvement d'ajustement créé
        assertThat(mouvementRepository.count()).isEqualTo(mvtsAvant + 1);
    }

    @Test
    void modification_interdite_hors_brouillon() {
        InventaireDto inv = creerInventaire();
        inventaireService.demarrerComptage(inv.getId());
        assertThatThrownBy(() -> inventaireService.update(inv.getId(), InventairePlanifPayload.builder()
                .libelle("x").perimetre(PerimetreInventaire.SELECTION).produitIds(List.of(produitId))
                .seuilEcartJustification(2).build()))
                .isInstanceOf(StockConflitException.class);
    }
}
