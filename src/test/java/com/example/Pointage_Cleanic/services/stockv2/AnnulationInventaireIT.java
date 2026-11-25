package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ComptagePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.InventaireDto;
import com.example.Pointage_Cleanic.Dto.stockv2.InventairePlanifPayload;
import com.example.Pointage_Cleanic.Enum.pieces.StatutPiece;
import com.example.Pointage_Cleanic.Enum.stockv2.PerimetreInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.AuthentificationTest;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.evenements.EvenementConsomme;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.entities.pieces.CompteurPiece;
import com.example.Pointage_Cleanic.entities.pieces.LignePieceRegistre;
import com.example.Pointage_Cleanic.entities.stockv2.Inventaire;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.StockAccesRefuseException;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import com.example.Pointage_Cleanic.services.evenements.DispatcherEvenements;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Inventaires portés sur le modèle pièce (lot 1 / 1b, D4) : numéro à la clôture, abandon d'un
 * brouillon, annulation d'un inventaire clôturé avec contre-ajustements par événement, sans
 * suppression. Remplace les tests d'inventaire de l'ancienne suppression définitive.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class AnnulationInventaireIT extends MongoTestContainer {

    private static final String SITE_A = "siteA";
    private static final String MOTIF = "Comptage refait, erreur de rayon";

    @Autowired private InventaireService inventaireService;
    @Autowired private StockBalanceService balanceService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MouvementStockRepository mouvementRepository;
    @Autowired private DispatcherEvenements dispatcher;
    @Autowired private MongoTemplate mongoTemplate;

    private String produitId;

    @BeforeEach
    void preparer() {
        for (Class<?> c : List.of(Inventaire.class, MouvementStock.class, StockParSite.class, ProduitStock.class,
                SiteClient.class, CompteurPiece.class, EvenementDomaine.class, EvenementConsomme.class,
                LignePieceRegistre.class)) {
            mongoTemplate.remove(new Query(), c);
        }
        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar").ville("Dakar").actif(true).build());
        produitId = produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
        AuthentificationTest.connecterSuperAdmin(mongoTemplate);
    }

    @AfterEach
    void deconnecter() {
        AuthentificationTest.deconnecter();
    }

    @Test
    void numero_attribue_a_la_cloture_seulement() {
        InventaireDto brouillon = creer();
        assertThat(brouillon.getReference()).isNull();
        assertThat(brouillon.getLibelleNumero()).startsWith("BROUILLON-");

        InventaireDto cloture = cloturerAvecEcart(brouillon.getId());

        assertThat(cloture.getReference()).matches("INV-\\d{4}-0001");
        assertThat(mouvementRepository.findByInventaireId(cloture.getId())).singleElement()
                .satisfies(m -> assertThat(m.getInventaireReference()).isEqualTo(cloture.getReference()));
    }

    @Test
    void abandon_d_un_brouillon_conserve_et_trace() {
        InventaireDto inv = creer();

        InventaireDto abandonne = inventaireService.abandonner(inv.getId());

        assertThat(abandonne.getStatut()).isEqualTo(StatutInventaire.ABANDONNE);
        assertThat(abandonne.getAbandon().getLe()).isNotNull();
        assertThat(mongoTemplate.count(new Query(), Inventaire.class)).isEqualTo(1);
        assertThatThrownBy(() -> inventaireService.abandonner(inv.getId())).isInstanceOf(StockConflitException.class);
        assertThatThrownBy(() -> inventaireService.demarrerComptage(inv.getId())).isInstanceOf(StockConflitException.class);
    }

    @Test
    void annulation_d_un_inventaire_cloture_contre_ajuste_par_evenement() {
        InventaireDto inv = cloturerAvecEcart(creer().getId());
        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(15.0);

        InventaireDto annule = inventaireService.annuler(inv.getId(), MOTIF);
        assertThat(annule.getStatut()).isEqualTo(StatutInventaire.ANNULE);
        assertThat(annule.getAnnulation().getMotif()).isEqualTo(MOTIF);
        assertThat(balanceService.quantite(produitId, SITE_A)).as("asynchrone").isEqualTo(15.0);

        dispatcher.traiterLot();

        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(20.0);
        List<MouvementStock> mvts = mouvementRepository.findByInventaireId(inv.getId());
        assertThat(mvts).hasSize(2);
        assertThat(mvts).filteredOn(m -> "ANNULATION".equals(m.getOrigine())).singleElement()
                .satisfies(m -> {
                    assertThat(m.getType()).isEqualTo(TypeMouvement.ENTREE);
                    assertThat(m.getQuantite()).isEqualTo(5.0);
                });
        assertThat(mvts).filteredOn(m -> "INVENTAIRE".equals(m.getOrigine())).singleElement()
                .satisfies(m -> assertThat(m.getContrePasse()).isTrue());
        assertThat(mongoTemplate.count(new Query(), Inventaire.class)).isEqualTo(1);
        assertThat(mongoTemplate.findOne(Query.query(Criteria.where("pieceId").is(inv.getId())),
                LignePieceRegistre.class).getStatut()).isEqualTo(StatutPiece.ANNULEE);

        assertThatThrownBy(() -> inventaireService.annuler(inv.getId(), MOTIF)).isInstanceOf(StockConflitException.class);
    }

    /** Repli pour les inventaires clôturés avant l'ajout d'{@code inventaireId}. */
    @Test
    void annulation_retrouve_les_ajustements_anciens_par_commentaire() {
        InventaireDto inv = cloturerAvecEcart(creer().getId());
        MouvementStock ajustement = mouvementRepository.findByInventaireId(inv.getId()).get(0);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(ajustement.getId())),
                new Update().unset("inventaireId").unset("inventaireReference").unset("origine"), MouvementStock.class);

        inventaireService.annuler(inv.getId(), MOTIF);
        dispatcher.traiterLot();

        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(20.0);
        assertThat(mongoTemplate.findById(ajustement.getId(), MouvementStock.class).getContrePasse()).isTrue();
    }

    @Test
    void annulation_refusee_hors_cloture_409_motif_400_et_hors_superadmin_403() {
        InventaireDto brouillon = creer();
        assertThatThrownBy(() -> inventaireService.annuler(brouillon.getId(), MOTIF))
                .isInstanceOf(StockConflitException.class);

        InventaireDto inv = cloturerAvecEcart(creer().getId());
        assertThatThrownBy(() -> inventaireService.annuler(inv.getId(), "erreur"))
                .isInstanceOf(IllegalArgumentException.class);

        AuthentificationTest.connecter(mongoTemplate, "magasinier@cleanic.sn", AuthentificationTest.CONTROLEUR_STOCK);
        assertThatThrownBy(() -> inventaireService.annuler(inv.getId(), MOTIF))
                .isInstanceOf(StockAccesRefuseException.class);
        assertThat(inventaireService.getById(inv.getId()).getStatut()).isEqualTo(StatutInventaire.CLOTURE);
    }

    /** Repris de l'ancienne SuppressionDefinitiveServiceIT : la clôture reste au contrôleur / super-admin. */
    @Test
    void cloture_refusee_403_hors_controleur_ou_superadmin() {
        balanceService.appliquerDelta(produitId, SITE_A, 20);
        InventaireDto inv = creer();
        inventaireService.demarrerComptage(inv.getId());
        inventaireService.enregistrerComptage(inv.getId(), ComptagePayload.builder()
                .lignes(List.of(ComptagePayload.LigneComptage.builder()
                        .produitId(produitId).qtePhysique(15.0).justification("Casse").build()))
                .build());
        inventaireService.valider(inv.getId());

        AuthentificationTest.connecter(mongoTemplate, "agent@cleanic.sn", "MAGASINIER");
        assertThatThrownBy(() -> inventaireService.cloturer(inv.getId()))
                .isInstanceOf(StockAccesRefuseException.class);
        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(20.0);
        assertThat(mongoTemplate.count(new Query(), CompteurPiece.class)).isZero();
    }

    // ---- outils ----------------------------------------------------------------------------

    private InventaireDto creer() {
        return inventaireService.create(InventairePlanifPayload.builder()
                .libelle("Inventaire mensuel").datePlanifiee(LocalDate.now()).siteId(SITE_A)
                .perimetre(PerimetreInventaire.SELECTION).produitIds(List.of(produitId))
                .seuilEcartJustification(2).build());
    }

    /** Stock 20 sur le site, compté 15 : écart -5 appliqué à la clôture. */
    private InventaireDto cloturerAvecEcart(String id) {
        balanceService.appliquerDelta(produitId, SITE_A, 20 - balanceService.quantite(produitId, SITE_A));
        inventaireService.demarrerComptage(id);
        inventaireService.enregistrerComptage(id, ComptagePayload.builder()
                .lignes(List.of(ComptagePayload.LigneComptage.builder()
                        .produitId(produitId).qtePhysique(15.0).justification("Casse constatée").build()))
                .build());
        inventaireService.valider(id);
        return inventaireService.cloturer(id);
    }
}
