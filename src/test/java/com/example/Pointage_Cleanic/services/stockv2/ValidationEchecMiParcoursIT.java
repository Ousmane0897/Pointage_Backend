package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.ComptagePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.InventairePlanifPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.example.Pointage_Cleanic.Enum.stockv2.PerimetreInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.AuthentificationTest;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.entities.pieces.CompteurPiece;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.CompteurStock;
import com.example.Pointage_Cleanic.entities.stockv2.HistoriquePointCout;
import com.example.Pointage_Cleanic.entities.stockv2.Inventaire;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.repositories.stockv2.BonEntreeRepository;
import com.example.Pointage_Cleanic.services.pieces.NumerotationService;
import com.example.Pointage_Cleanic.repositories.stockv2.BonSortieRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.Pointage_Cleanic.services.stockv2.StockBalanceService.ENTREPOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/**
 * Décision D1.6 : retrait de la compensation manuelle de {@code MouvementBonGenerator}, avec
 * « échec à mi-parcours ⇒ rien d'écrit ». L'enregistrement du 2ᵉ mouvement d'un bon de deux
 * lignes échoue : soldes, mouvements, coût courant (CUMP), historique de coût, compteur de
 * références et statut du bon restent tels qu'avant la validation.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class ValidationEchecMiParcoursIT extends MongoTestContainer {

    private static final String SITE_A = "siteA";

    @SpyBean private MouvementStockRepository mouvementRepository;
    @Autowired private BonEntreeService bonEntreeService;
    @Autowired private BonSortieService bonSortieService;
    @Autowired private InventaireService inventaireService;
    @Autowired private BonEntreeRepository bonEntreeRepository;
    @Autowired private BonSortieRepository bonSortieRepository;
    @Autowired private StockBalanceService balanceService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String produit1;
    private String produit2;

    @BeforeEach
    void preparer() {
        reset(mouvementRepository);
        mongoTemplate.remove(new Query(), BonEntree.class);
        mongoTemplate.remove(new Query(), BonSortie.class);
        mongoTemplate.remove(new Query(), Inventaire.class);
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), HistoriquePointCout.class);
        mongoTemplate.remove(new Query(), SiteClient.class);
        mongoTemplate.remove(new Query(), CompteurPiece.class);
        mongoTemplate.remove(new Query(), EvenementDomaine.class);
        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar Plateau").ville("Dakar").actif(true).build());
        produit1 = produit("EM1");
        produit2 = produit("EM2");
        AuthentificationTest.connecterSuperAdmin(mongoTemplate);
    }

    @AfterEach
    void nettoyer() {
        reset(mouvementRepository);
        AuthentificationTest.deconnecter();
    }

    private String produit(String code) {
        return produitRepository.save(ProduitStock.builder()
                .code(code).libelle("Produit " + code).typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(1).prixUnitaire(1000L)
                .methodeValorisation(MethodeValorisation.CUMP).actif(true).build()).getId();
    }

    private List<LignePayload> deuxLignes(double qte, Long prix) {
        return List.of(LignePayload.builder().produitId(produit1).quantite(qte).prixUnitaire(prix).build(),
                LignePayload.builder().produitId(produit2).quantite(qte).prixUnitaire(prix).build());
    }

    /** Le 2ᵉ enregistrement de mouvement à partir de maintenant échoue. */
    private void echouerAuDeuxiemeMouvement() {
        AtomicInteger appels = new AtomicInteger();
        doAnswer(invocation -> {
            if (appels.incrementAndGet() == 2) {
                throw new IllegalStateException("panne simulée au 2e mouvement");
            }
            // Le dépôt est une interface (proxy Spring Data) : pas de « vraie » méthode à appeler.
            // MongoTemplate fait le même enregistrement, dans la même transaction.
            return mongoTemplate.save((MouvementStock) invocation.getArgument(0));
        }).when(mouvementRepository).save(any(MouvementStock.class));
    }

    private List<Document> compteurs() {
        return mongoTemplate.findAll(Document.class, mongoTemplate.getCollectionName(CompteurStock.class));
    }

    @Test
    void entree_echec_au_deuxieme_mouvement_rien_n_est_ecrit() {
        String id = bonEntreeService.creer(BonEntreePayload.builder()
                .type(TypeEntree.ACHAT_FOURNISSEUR).siteDestinationId(SITE_A).fournisseur("F")
                .lignes(deuxLignes(10, 3000L)).build()).getId();
        bonEntreeService.soumettre(id);
        List<Document> compteursAvant = compteurs();
        echouerAuDeuxiemeMouvement();

        assertThatThrownBy(() -> bonEntreeService.valider(id, null)).hasMessageContaining("panne simulée");

        assertThat(mongoTemplate.count(new Query(), MouvementStock.class)).as("mouvements").isZero();
        assertThat(balanceService.quantite(produit1, ENTREPOT)).as("solde produit 1").isZero();
        assertThat(balanceService.quantite(produit2, ENTREPOT)).as("solde produit 2").isZero();
        assertThat(produitRepository.findById(produit1).orElseThrow().getPrixUnitaire())
                .as("coût CUMP du produit 1 inchangé").isEqualTo(1000L);
        assertThat(mongoTemplate.count(new Query(), HistoriquePointCout.class)).as("historique de coût").isZero();
        assertThat(compteurs()).as("compteur MVT inchangé : pas de trou de référence").isEqualTo(compteursAvant);
        assertThat(bonEntreeRepository.findById(id).orElseThrow().getStatut()).isEqualTo(StatutBon.SOUMIS);
        // Lot 1 / 1b : ni numéro de pièce consommé, ni événement PIECE_VALIDEE publié.
        assertThat(bonEntreeRepository.findById(id).orElseThrow().getReference()).isNull();
        assertThat(mongoTemplate.count(new Query(), CompteurPiece.class)).as("compteur BE").isZero();
        assertThat(mongoTemplate.count(Query.query(org.springframework.data.mongodb.core.query.Criteria
                .where("type").is("PIECE_VALIDEE")), EvenementDomaine.class)).isZero();

        // Sans panne, la même validation passe ensuite normalement.
        reset(mouvementRepository);
        assertThat(bonEntreeService.valider(id, null).getStatut()).isEqualTo(StatutBon.EFFECTIF);
        assertThat(balanceService.quantite(produit1, ENTREPOT)).isEqualTo(10.0);
    }

    @Test
    void sortie_echec_au_deuxieme_mouvement_rien_n_est_debite() {
        String entree = bonEntreeService.creer(BonEntreePayload.builder()
                .type(TypeEntree.ACHAT_FOURNISSEUR).siteDestinationId(SITE_A).fournisseur("F")
                .lignes(deuxLignes(10, null)).build()).getId();
        bonEntreeService.soumettre(entree);
        bonEntreeService.valider(entree, null);
        String sortie = bonSortieService.creer(BonSortiePayload.builder()
                .type(TypeSortie.CONSOMMATION_INTERNE).siteSourceId(SITE_A)
                .destinataire(DestinatairePayload.builder().type(TypeDestinataire.CLIENT).clientNom("C").build())
                .lignes(deuxLignes(4, null)).build()).getId();
        bonSortieService.soumettre(sortie);
        long mouvementsAvant = mongoTemplate.count(new Query(), MouvementStock.class);
        echouerAuDeuxiemeMouvement();

        assertThatThrownBy(() -> bonSortieService.valider(sortie, null)).hasMessageContaining("panne simulée");

        assertThat(mongoTemplate.count(new Query(), MouvementStock.class)).isEqualTo(mouvementsAvant);
        assertThat(balanceService.quantite(produit1, ENTREPOT)).as("produit 1 non débité").isEqualTo(10.0);
        assertThat(balanceService.quantite(produit2, ENTREPOT)).as("produit 2 non débité").isEqualTo(10.0);
        assertThat(bonSortieRepository.findById(sortie).orElseThrow().getStatut()).isEqualTo(StatutBon.SOUMIS);
        assertThat(bonSortieRepository.findById(sortie).orElseThrow().getReference()).isNull();
        assertThat(mongoTemplate.findById(NumerotationService.cle(TypePiece.BS,
                NumerotationService.exerciceAu(java.time.Instant.now())), CompteurPiece.class)).as("compteur BS").isNull();
    }

    /**
     * Lot 1 / 1b (D1.6) : la compensation manuelle de la clôture d'inventaire est retirée ; la
     * clôture passe en transaction. L'écriture du 2ᵉ ajustement échoue : ni solde modifié, ni
     * ajustement, ni numéro INV consommé, statut inchangé.
     */
    @Test
    void cloture_d_inventaire_echec_au_deuxieme_ajustement_rien_n_est_ecrit() {
        balanceService.appliquerDelta(produit1, SITE_A, 20);
        balanceService.appliquerDelta(produit2, SITE_A, 20);
        String id = inventaireService.create(InventairePlanifPayload.builder()
                .libelle("Inventaire").datePlanifiee(java.time.LocalDate.now()).siteId(SITE_A)
                .perimetre(PerimetreInventaire.SELECTION).produitIds(List.of(produit1, produit2))
                .seuilEcartJustification(100).build()).getId();
        inventaireService.demarrerComptage(id);
        inventaireService.enregistrerComptage(id, ComptagePayload.builder().lignes(List.of(
                ComptagePayload.LigneComptage.builder().produitId(produit1).qtePhysique(15.0).build(),
                ComptagePayload.LigneComptage.builder().produitId(produit2).qtePhysique(18.0).build())).build());
        inventaireService.valider(id);
        echouerAuDeuxiemeMouvement();

        assertThatThrownBy(() -> inventaireService.cloturer(id)).hasMessageContaining("panne simulée");

        assertThat(balanceService.quantite(produit1, SITE_A)).as("produit 1 non ajusté").isEqualTo(20.0);
        assertThat(balanceService.quantite(produit2, SITE_A)).as("produit 2 non ajusté").isEqualTo(20.0);
        assertThat(mongoTemplate.count(new Query(), MouvementStock.class)).as("ajustements").isZero();
        Inventaire inv = mongoTemplate.findById(id, Inventaire.class);
        assertThat(inv.getStatut()).isEqualTo(StatutInventaire.VALIDATION);
        assertThat(inv.getReference()).isNull();
        assertThat(mongoTemplate.count(new Query(), CompteurPiece.class)).as("compteur INV").isZero();

        reset(mouvementRepository);
        assertThat(inventaireService.cloturer(id).getStatut()).isEqualTo(StatutInventaire.CLOTURE);
        assertThat(balanceService.quantite(produit1, SITE_A)).isEqualTo(15.0);
    }
}
