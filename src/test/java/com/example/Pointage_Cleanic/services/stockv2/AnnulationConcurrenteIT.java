package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Enum.evenements.StatutEvenement;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
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
import com.example.Pointage_Cleanic.exception.ConflitConcurrentException;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.example.Pointage_Cleanic.services.stockv2.StockBalanceService.ENTREPOT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Écart 5 de 1b, tranché le 25/11 : le stock d'un bon annulé est rétabli dans la transaction
 * d'annulation. Une annulation de bon d'entrée et une sortie qui consomme le même stock, lancées
 * ensemble : une seule passe, l'autre est refusée (422, ou 409 si le conflit persiste après les
 * rejeux), le solde n'est jamais négatif et aucun événement ne finit en erreur.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class AnnulationConcurrenteIT extends MongoTestContainer {

    private static final String SITE_A = "siteA";
    private static final String MOTIF = "Bon saisi deux fois par erreur";
    private static final int ESSAIS = 10;

    @Autowired private BonEntreeService bonEntreeService;
    @Autowired private BonSortieService bonSortieService;
    @Autowired private StockBalanceService balanceService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MouvementStockRepository mouvementRepository;
    @Autowired private DispatcherEvenements dispatcher;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void preparer() {
        for (Class<?> c : List.of(BonEntree.class, BonSortie.class, MouvementStock.class, StockParSite.class,
                ProduitStock.class, SiteClient.class, CompteurPiece.class, EvenementDomaine.class,
                EvenementConsomme.class, LignePieceRegistre.class)) {
            mongoTemplate.remove(new Query(), c);
        }
        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar Plateau").ville("Dakar").actif(true).build());
        AuthentificationTest.connecterSuperAdmin(mongoTemplate);
    }

    @AfterEach
    void deconnecter() {
        AuthentificationTest.deconnecter();
    }

    @Test
    void annulation_d_entree_et_sortie_concurrentes_une_seule_passe_jamais_d_erreur() throws Exception {
        int annulations = 0;
        int sorties = 0;
        for (int i = 0; i < ESSAIS; i++) {
            String produitId = produit("P" + i);
            String entreeId = entreeEffective(produitId, 10);
            String sortieId = sortieSoumise(produitId, 10);

            List<Throwable> issues = enMemeTemps(List.of(
                    () -> bonEntreeService.annuler(entreeId, MOTIF),
                    () -> bonSortieService.valider(sortieId, null)));

            assertThat(issues).as("essai %d : une seule des deux passe", i).filteredOn(t -> t == null).hasSize(1);
            assertThat(issues).filteredOn(t -> t != null).singleElement()
                    .satisfies(t -> assertThat(t).isInstanceOfAny(StockInsuffisantException.class,
                            ConflitConcurrentException.class));

            boolean annule = issues.get(0) == null;
            if (annule) {
                annulations++;
                assertThat(bonEntreeService.getById(entreeId).getStatut()).isEqualTo(StatutBon.ANNULE);
                assertThat(bonSortieService.getById(sortieId).getStatut()).isEqualTo(StatutBon.SOUMIS);
                assertThat(mouvementRepository.findByBonId(entreeId)).hasSize(2);
                assertThat(mouvementRepository.findByBonId(sortieId)).isEmpty();
            } else {
                sorties++;
                assertThat(bonEntreeService.getById(entreeId).getStatut()).isEqualTo(StatutBon.EFFECTIF);
                assertThat(bonSortieService.getById(sortieId).getStatut()).isEqualTo(StatutBon.EFFECTIF);
                assertThat(mouvementRepository.findByBonId(entreeId)).hasSize(1);
            }
            assertThat(balanceService.quantite(produitId, ENTREPOT)).as("essai %d : solde", i).isZero();
            assertThat(balanceService.quantite(produitId, SITE_A)).isZero();
        }
        assertThat(annulations + sorties).isEqualTo(ESSAIS);

        assertThat(mongoTemplate.count(Query.query(Criteria.where("quantite").lt(0)), StockParSite.class))
                .as("aucun solde négatif").isZero();
        dispatcher.traiterLot();
        dispatcher.traiterLot();
        assertThat(mongoTemplate.count(Query.query(Criteria.where("statut").ne(StatutEvenement.TRAITE)),
                EvenementDomaine.class)).as("tous les événements traités, aucun EN_ERREUR").isZero();
    }

    // ---- outils ----------------------------------------------------------------------------

    private String produit(String code) {
        return produitRepository.save(ProduitStock.builder()
                .code(code).libelle("Savon " + code).typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
    }

    private String entreeEffective(String produitId, double qte) {
        String id = bonEntreeService.creer(BonEntreePayload.builder()
                .type(TypeEntree.ACHAT_FOURNISSEUR).siteDestinationId(SITE_A).fournisseur("F")
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build()).getId();
        bonEntreeService.soumettre(id);
        bonEntreeService.valider(id, null);
        return id;
    }

    private String sortieSoumise(String produitId, double qte) {
        String id = bonSortieService.creer(BonSortiePayload.builder()
                .type(TypeSortie.CONSOMMATION_INTERNE).siteSourceId(SITE_A)
                .destinataire(DestinatairePayload.builder().type(TypeDestinataire.CLIENT).clientNom("C").build())
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build()).getId();
        bonSortieService.soumettre(id);
        return id;
    }

    /** Lance les actions ensemble (barrière), chacune dans son fil avec la session du test. */
    private List<Throwable> enMemeTemps(List<Runnable> actions) throws Exception {
        Authentication session = SecurityContextHolder.getContext().getAuthentication();
        CyclicBarrier depart = new CyclicBarrier(actions.size());
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        try {
            List<CompletableFuture<Throwable>> issues = new ArrayList<>();
            for (Runnable action : actions) {
                issues.add(CompletableFuture.supplyAsync(() -> {
                    SecurityContextHolder.getContext().setAuthentication(session);
                    try {
                        depart.await(30, TimeUnit.SECONDS);
                        action.run();
                        return null;
                    } catch (Throwable t) {
                        return t;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }, pool));
            }
            List<Throwable> resultats = new ArrayList<>();
            for (CompletableFuture<Throwable> issue : issues) {
                resultats.add(issue.get(2, TimeUnit.MINUTES));
            }
            return resultats;
        } finally {
            pool.shutdownNow();
        }
    }
}
