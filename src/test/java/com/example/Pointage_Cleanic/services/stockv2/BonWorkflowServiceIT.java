package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreeDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortieDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DecisionPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Enum.stockv2.ActionWorkflow;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
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

import java.util.List;

import static com.example.Pointage_Cleanic.services.stockv2.StockBalanceService.ENTREPOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class BonWorkflowServiceIT extends MongoTestContainer {

    @Autowired private BonEntreeService bonEntreeService;
    @Autowired private BonSortieService bonSortieService;
    @Autowired private StockBalanceService balanceService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MouvementStockRepository mouvementRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String produitId;
    private static final String SITE_A = "siteA";

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), BonEntree.class);
        mongoTemplate.remove(new Query(), BonSortie.class);
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), SiteClient.class);

        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar Plateau").ville("Dakar").actif(true).build());
        produitId = produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
    }

    private BonEntreePayload entreePayload(double qte) {
        return BonEntreePayload.builder()
                .type(TypeEntree.ACHAT_FOURNISSEUR)
                .siteDestinationId(SITE_A)
                .fournisseur("Fournisseur X")
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build();
    }

    private BonSortiePayload sortiePayload(double qte) {
        return BonSortiePayload.builder()
                .type(TypeSortie.CONSOMMATION_INTERNE)
                .siteSourceId(SITE_A)
                .destinataire(DestinatairePayload.builder().type(TypeDestinataire.CLIENT).clientNom("Client A").build())
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build();
    }

    @Test
    void cycle_complet_bon_entree_genere_mouvement_et_credite_stock() {
        BonEntreeDto cree = bonEntreeService.creer(entreePayload(20));
        assertThat(cree.getReference()).startsWith("BE-");
        assertThat(cree.getStatut()).isEqualTo(StatutBon.BROUILLON);
        assertThat(cree.getMontantTotal()).isEqualTo(20_000L);
        assertThat(cree.getLignes().get(0).getProduitCode()).isEqualTo("P1");

        bonEntreeService.soumettre(cree.getId());
        BonEntreeDto valide = bonEntreeService.valider(cree.getId(), null);

        assertThat(valide.getStatut()).isEqualTo(StatutBon.EFFECTIF);
        assertThat(balanceService.quantite(produitId, ENTREPOT)).isEqualTo(20.0);

        List<MouvementStock> mvts = mouvementRepository.findByProduitIdOrderByDateDesc(produitId);
        assertThat(mvts).hasSize(1);
        assertThat(mvts.get(0).getOrigine()).isEqualTo("BON");
        assertThat(mvts.get(0).getBonReference()).isEqualTo(valide.getReference());
        assertThat(mvts.get(0).getType()).isEqualTo(TypeMouvement.ENTREE);
        assertThat(mvts.get(0).getCategorieEntree()).isEqualTo(TypeEntree.ACHAT_FOURNISSEUR);
    }

    @Test
    void cycle_complet_bon_sortie_debite_stock() {
        BonEntreeDto entree = bonEntreeService.creer(entreePayload(20));
        bonEntreeService.soumettre(entree.getId());
        bonEntreeService.valider(entree.getId(), null);

        BonSortieDto sortie = bonSortieService.creer(sortiePayload(8));
        assertThat(sortie.getReference()).startsWith("BS-");
        bonSortieService.soumettre(sortie.getId());
        BonSortieDto valide = bonSortieService.valider(sortie.getId(), null);

        assertThat(valide.getStatut()).isEqualTo(StatutBon.EFFECTIF);
        assertThat(balanceService.quantite(produitId, ENTREPOT)).isEqualTo(12.0);
    }

    @Test
    void bon_entree_credite_l_entrepot_et_pas_le_site_destination() {
        BonEntreeDto cree = bonEntreeService.creer(entreePayload(20));
        bonEntreeService.soumettre(cree.getId());
        bonEntreeService.valider(cree.getId(), null);

        // Le stock entre dans l'entrepôt unique...
        assertThat(balanceService.quantite(produitId, ENTREPOT)).isEqualTo(20.0);
        // ...et non sur le site de destination du bon, qui reste purement documentaire.
        assertThat(balanceService.quantite(produitId, SITE_A)).isZero();
        assertThat(balanceService.soldesDuProduit(produitId)).hasSize(1);

        // Le site reste tracé sur le mouvement (analyses, historique).
        MouvementStock mvt = mouvementRepository.findByProduitIdOrderByDateDesc(produitId).get(0);
        assertThat(mvt.getSiteDestinationId()).isEqualTo(SITE_A);
        assertThat(mvt.getSiteDestinationNom()).isEqualTo("Dakar Plateau");
    }

    @Test
    void validation_sortie_stock_insuffisant_rejetee_422_sans_effet() {
        BonSortieDto sortie = bonSortieService.creer(sortiePayload(5));
        bonSortieService.soumettre(sortie.getId());

        assertThatThrownBy(() -> bonSortieService.valider(sortie.getId(), null))
                .isInstanceOf(StockOperationException.class);

        // Rien validé : statut inchangé, aucun mouvement, solde nul.
        assertThat(bonSortieService.getById(sortie.getId()).getStatut()).isEqualTo(StatutBon.SOUMIS);
        assertThat(mouvementRepository.findByProduitIdOrderByDateDesc(produitId)).isEmpty();
        assertThat(balanceService.quantite(produitId, ENTREPOT)).isZero();
    }

    @Test
    void refus_sans_commentaire_400() {
        BonEntreeDto cree = bonEntreeService.creer(entreePayload(10));
        bonEntreeService.soumettre(cree.getId());
        assertThatThrownBy(() -> bonEntreeService.refuser(cree.getId(), new DecisionPayload(" ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Reprise après refus (bons de sortie uniquement) -----------------------

    /**
     * REFUSE n'est pas un cul-de-sac : le bon revient en BROUILLON, l'historique du cycle refusé
     * est <b>conservé</b> et enrichi d'une entrée REPRISE, et le motif de refus subsiste — c'est en
     * corrigeant que le créateur en a besoin.
     */
    @Test
    void reprise_d_un_bon_refuse_le_ramene_en_brouillon_en_conservant_historique_et_motif() {
        BonSortieDto sortie = bonSortieService.creer(sortiePayload(3));
        bonSortieService.soumettre(sortie.getId());
        bonSortieService.refuser(sortie.getId(), new DecisionPayload("Quantité erronée"));
        int historiqueAvant = bonSortieService.getById(sortie.getId()).getHistorique().size();

        BonSortieDto repris = bonSortieService.reprendre(sortie.getId());

        assertThat(repris.getStatut()).isEqualTo(StatutBon.BROUILLON);
        assertThat(repris.getMotifRefus()).isEqualTo("Quantité erronée");
        assertThat(repris.getHistorique()).hasSize(historiqueAvant + 1);
        assertThat(repris.getHistorique().get(historiqueAvant).getAction()).isEqualTo(ActionWorkflow.REPRISE);
        // Le cycle refusé reste lisible.
        assertThat(repris.getHistorique()).anyMatch(h -> h.getAction() == ActionWorkflow.REFUS);
    }

    @Test
    void bon_repris_peut_etre_modifie_puis_resoumis() {
        BonSortieDto sortie = bonSortieService.creer(sortiePayload(3));
        bonSortieService.soumettre(sortie.getId());
        bonSortieService.refuser(sortie.getId(), new DecisionPayload("Quantité erronée"));
        bonSortieService.reprendre(sortie.getId());

        bonSortieService.modifier(sortie.getId(), sortiePayload(2));
        BonSortieDto resoumis = bonSortieService.soumettre(sortie.getId());

        assertThat(resoumis.getStatut()).isEqualTo(StatutBon.SOUMIS);
        assertThat(resoumis.getLignes().get(0).getQuantite()).isEqualTo(2.0);
    }

    @Test
    void reprise_hors_statut_refuse_409() {
        BonSortieDto sortie = bonSortieService.creer(sortiePayload(3));

        // BROUILLON
        assertThatThrownBy(() -> bonSortieService.reprendre(sortie.getId()))
                .isInstanceOf(StockConflitException.class);

        // SOUMIS
        bonSortieService.soumettre(sortie.getId());
        assertThatThrownBy(() -> bonSortieService.reprendre(sortie.getId()))
                .isInstanceOf(StockConflitException.class);
    }

    @Test
    void transitions_invalides_409() {
        BonEntreeDto cree = bonEntreeService.creer(entreePayload(10));
        // valider un BROUILLON (pas SOUMIS)
        assertThatThrownBy(() -> bonEntreeService.valider(cree.getId(), null))
                .isInstanceOf(StockConflitException.class);
        // modifier après soumission
        bonEntreeService.soumettre(cree.getId());
        assertThatThrownBy(() -> bonEntreeService.modifier(cree.getId(), entreePayload(5)))
                .isInstanceOf(StockConflitException.class);
        // supprimer après soumission
        assertThatThrownBy(() -> bonEntreeService.supprimer(cree.getId()))
                .isInstanceOf(StockConflitException.class);
    }
}
