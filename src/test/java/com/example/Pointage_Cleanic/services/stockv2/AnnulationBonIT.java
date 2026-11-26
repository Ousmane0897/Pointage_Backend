package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreeDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortieDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Enum.evenements.StatutEvenement;
import com.example.Pointage_Cleanic.Enum.pieces.StatutPiece;
import com.example.Pointage_Cleanic.Enum.stockv2.ActionWorkflow;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.AuthentificationTest;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.evenements.EvenementConsomme;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.entities.pieces.CompteurPiece;
import com.example.Pointage_Cleanic.entities.pieces.LignePieceRegistre;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.StockAccesRefuseException;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.exception.StockInsuffisantException;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.example.Pointage_Cleanic.services.stockv2.StockBalanceService.ENTREPOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Critère 1b « annulation d'un bon EFFECTIF » (décision D4, D5) : l'annulation trace le motif,
 * produit les mouvements inverses et rétablit le stock <b>dans sa transaction</b> (écart 5 tranché
 * le 25/11), puis publie {@code PIECE_ANNULEE} pour les effets secondaires ; rien n'est supprimé ;
 * les consommations n'en comptent plus. Remplace les tests de bons de l'ancienne suppression
 * définitive.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class AnnulationBonIT extends MongoTestContainer {

    private static final String SITE_A = "siteA";
    private static final String MOTIF = "Erreur de saisie de la quantité";

    @Autowired private BonEntreeService bonEntreeService;
    @Autowired private BonSortieService bonSortieService;
    @Autowired private StockBalanceService balanceService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MouvementStockRepository mouvementRepository;
    @Autowired private DispatcherEvenements dispatcher;
    @Autowired private AnalyseSupport analyseSupport;
    @Autowired private MongoTemplate mongoTemplate;

    private String produitId;

    @BeforeEach
    void preparer() {
        for (Class<?> c : List.of(BonEntree.class, BonSortie.class, MouvementStock.class, StockParSite.class,
                ProduitStock.class, SiteClient.class, CompteurPiece.class, EvenementDomaine.class,
                EvenementConsomme.class, LignePieceRegistre.class)) {
            mongoTemplate.remove(new Query(), c);
        }
        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar Plateau").ville("Dakar").actif(true).build());
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
    void bon_de_sortie_annule_mouvements_inverses_et_stock_recredite_dans_la_transaction() {
        entreeEffective(20);
        BonSortieDto sortie = sortieEffective(8);
        assertThat(balanceService.quantite(produitId, ENTREPOT)).isEqualTo(12.0);
        assertThat(sortiesComptees()).isEqualTo(1);

        BonSortieDto annule = bonSortieService.annuler(sortie.getId(), MOTIF);

        assertThat(annule.getStatut()).isEqualTo(StatutBon.ANNULE);
        assertThat(annule.getAnnulation().getMotif()).isEqualTo(MOTIF);
        assertThat(annule.getAnnulation().getPar().getNom()).isNotBlank();
        assertThat(annule.getHistorique()).anyMatch(h -> h.getAction() == ActionWorkflow.ANNULATION);
        // Les contre-effets de stock sont dans la transaction d'annulation : aucun délai.
        assertThat(balanceService.quantite(produitId, ENTREPOT)).isEqualTo(20.0);
        assertThat(balanceService.quantite(produitId, SITE_A)).as("pas de stock fantôme sur le site").isZero();
        List<MouvementStock> mvts = mouvementRepository.findByBonId(sortie.getId());
        assertThat(mvts).hasSize(2);
        MouvementStock origine = mvts.stream().filter(m -> "BON".equals(m.getOrigine())).findFirst().orElseThrow();
        MouvementStock inverse = mvts.stream().filter(m -> "ANNULATION".equals(m.getOrigine())).findFirst().orElseThrow();
        assertThat(origine.getContrePasse()).isTrue();
        assertThat(inverse.getType()).isEqualTo(TypeMouvement.ENTREE);
        assertThat(inverse.getQuantite()).isEqualTo(8.0);
        assertThat(inverse.getMouvementAnnuleId()).isEqualTo(origine.getId());
        assertThat(inverse.getBonReference()).isEqualTo(sortie.getReference());
        assertThat(inverse.getValeurMouvement()).isEqualTo(origine.getValeurMouvement());
        // Rien n'est supprimé, et les consommations n'en comptent plus.
        assertThat(mongoTemplate.count(new Query(), BonSortie.class)).isEqualTo(1);
        assertThat(sortiesComptees()).isZero();
        dispatcher.traiterLot();
        assertThat(registre(sortie.getId()).getStatut()).isEqualTo(StatutPiece.ANNULEE);
        assertThat(mongoTemplate.count(Query.query(Criteria.where("statut").ne(StatutEvenement.TRAITE)),
                EvenementDomaine.class)).as("tous les événements traités").isZero();
    }

    @Test
    void bon_d_entree_annule_le_stock_recu_est_retire_de_l_entrepot() {
        BonEntreeDto entree = entreeEffective(20);

        bonEntreeService.annuler(entree.getId(), MOTIF);

        assertThat(balanceService.quantite(produitId, ENTREPOT)).isZero();
        List<MouvementStock> mvts = mouvementRepository.findByBonId(entree.getId());
        assertThat(mvts).hasSize(2);
        assertThat(mvts).filteredOn(m -> "ANNULATION".equals(m.getOrigine())).singleElement()
                .satisfies(m -> assertThat(m.getType()).isEqualTo(TypeMouvement.SORTIE));
        assertThat(mongoTemplate.count(new Query(), BonEntree.class)).isEqualTo(1);
    }

    @Test
    void bon_d_entree_deja_consomme_422_rien_n_est_ecrit() {
        BonEntreeDto entree = entreeEffective(20);
        sortieEffective(15); // il ne reste que 5 des 20 reçus
        long evenementsAvant = mongoTemplate.count(new Query(), EvenementDomaine.class);

        assertThatThrownBy(() -> bonEntreeService.annuler(entree.getId(), MOTIF))
                .isInstanceOf(StockInsuffisantException.class);

        assertThat(bonEntreeService.getById(entree.getId()).getStatut()).isEqualTo(StatutBon.EFFECTIF);
        assertThat(mongoTemplate.count(new Query(), EvenementDomaine.class)).isEqualTo(evenementsAvant);
        assertThat(mouvementRepository.findByBonId(entree.getId())).hasSize(1);
        assertThat(bonEntreeService.getById(entree.getId()).getAnnulation()).isNull();
        assertThat(balanceService.quantite(produitId, ENTREPOT)).isEqualTo(5.0);
    }

    @Test
    void double_annulation_et_statut_non_effectif_409() {
        BonSortieDto brouillon = bonSortieService.creer(sortiePayload(1));
        assertThatThrownBy(() -> bonSortieService.annuler(brouillon.getId(), MOTIF))
                .isInstanceOf(StockConflitException.class);

        entreeEffective(10);
        BonSortieDto sortie = sortieEffective(4);
        bonSortieService.annuler(sortie.getId(), MOTIF);
        assertThatThrownBy(() -> bonSortieService.annuler(sortie.getId(), MOTIF))
                .isInstanceOf(StockConflitException.class);
    }

    @Test
    void motif_trop_court_400_et_hors_superadmin_403() {
        entreeEffective(10);
        BonSortieDto sortie = sortieEffective(4);

        assertThatThrownBy(() -> bonSortieService.annuler(sortie.getId(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bonSortieService.annuler(sortie.getId(), "erreur"))
                .isInstanceOf(IllegalArgumentException.class);

        AuthentificationTest.connecter(mongoTemplate, "magasinier@cleanic.sn", AuthentificationTest.CONTROLEUR_STOCK);
        assertThatThrownBy(() -> bonSortieService.annuler(sortie.getId(), MOTIF))
                .isInstanceOf(StockAccesRefuseException.class);
        assertThat(bonSortieService.getById(sortie.getId()).getStatut()).isEqualTo(StatutBon.EFFECTIF);
    }

    @Test
    void evenements_rejoues_sans_second_contre_passement() {
        entreeEffective(20);
        BonSortieDto sortie = sortieEffective(8);
        bonSortieService.annuler(sortie.getId(), MOTIF);
        assertThatThrownBy(() -> bonSortieService.annuler(sortie.getId(), MOTIF))
                .isInstanceOf(StockConflitException.class);
        dispatcher.traiterLot();

        // Redistribution de tous les événements, traces d'idempotence comprises effacées : aucun
        // consommateur ne touche plus au stock.
        mongoTemplate.updateMulti(new Query(), new Update().set("statut", StatutEvenement.A_TRAITER)
                .set("prochainEssai", Instant.EPOCH), EvenementDomaine.class);
        dispatcher.traiterLot();
        mongoTemplate.remove(new Query(), EvenementConsomme.class);
        mongoTemplate.updateMulti(new Query(), new Update().set("statut", StatutEvenement.A_TRAITER)
                .set("prochainEssai", Instant.EPOCH), EvenementDomaine.class);
        dispatcher.traiterLot();

        assertThat(mouvementRepository.findByBonId(sortie.getId())).hasSize(2);
        assertThat(balanceService.quantite(produitId, ENTREPOT)).isEqualTo(20.0);
    }

    // ---- outils ----------------------------------------------------------------------------

    private BonEntreeDto entreeEffective(double qte) {
        BonEntreeDto bon = bonEntreeService.creer(BonEntreePayload.builder()
                .type(TypeEntree.ACHAT_FOURNISSEUR).siteDestinationId(SITE_A).fournisseur("Fournisseur X")
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build());
        bonEntreeService.soumettre(bon.getId());
        return bonEntreeService.valider(bon.getId(), null);
    }

    private BonSortiePayload sortiePayload(double qte) {
        return BonSortiePayload.builder()
                .type(TypeSortie.CONSOMMATION_INTERNE).siteSourceId(SITE_A)
                .destinataire(DestinatairePayload.builder().type(TypeDestinataire.CLIENT).clientNom("Client A").build())
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build();
    }

    private BonSortieDto sortieEffective(double qte) {
        BonSortieDto bon = bonSortieService.creer(sortiePayload(qte));
        bonSortieService.soumettre(bon.getId());
        return bonSortieService.valider(bon.getId(), null);
    }

    private int sortiesComptees() {
        return analyseSupport.sortiesEffectives(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1),
                null, null, null, null).mouvements().size();
    }

    private LignePieceRegistre registre(String pieceId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("pieceId").is(pieceId)), LignePieceRegistre.class);
    }
}
