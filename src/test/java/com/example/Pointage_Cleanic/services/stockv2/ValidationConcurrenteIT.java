package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.AuthentificationTest;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.ConflitConcurrentException;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.exception.StockInsuffisantException;
import com.example.Pointage_Cleanic.repositories.stockv2.BonEntreeRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.BonSortieRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
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
import java.util.function.Consumer;

import static com.example.Pointage_Cleanic.services.stockv2.StockBalanceService.ENTREPOT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critère 1a « ValidationConcurrenteIT » (décisions D1.3, D1.5) : deux validations simultanées
 * d'un même bon SOUMIS donnent une réussite et un 409, et les mouvements ne sont générés qu'une
 * fois. Deux bons concurrents sur un stock qui n'en couvre qu'un : un seul passe, l'autre 422,
 * le solde ne devient jamais négatif.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class ValidationConcurrenteIT extends MongoTestContainer {

    private static final String SITE_A = "siteA";

    @Autowired private BonEntreeService bonEntreeService;
    @Autowired private BonSortieService bonSortieService;
    @Autowired private BonEntreeRepository bonEntreeRepository;
    @Autowired private BonSortieRepository bonSortieRepository;
    @Autowired private StockBalanceService balanceService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MouvementStockRepository mouvementRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String produitId;

    @BeforeEach
    void preparer() {
        mongoTemplate.remove(new Query(), BonEntree.class);
        mongoTemplate.remove(new Query(), BonSortie.class);
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), SiteClient.class);
        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar Plateau").ville("Dakar").actif(true).build());
        produitId = produitRepository.save(ProduitStock.builder()
                .code("PC1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
        AuthentificationTest.connecterSuperAdmin(mongoTemplate);
    }

    @AfterEach
    void deconnecter() {
        AuthentificationTest.deconnecter();
    }

    private String bonEntreeSoumis(double qte) {
        String id = bonEntreeService.creer(BonEntreePayload.builder()
                .type(TypeEntree.ACHAT_FOURNISSEUR).siteDestinationId(SITE_A).fournisseur("F")
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build()).getId();
        bonEntreeService.soumettre(id);
        return id;
    }

    private String bonSortieSoumis(double qte) {
        String id = bonSortieService.creer(BonSortiePayload.builder()
                .type(TypeSortie.CONSOMMATION_INTERNE).siteSourceId(SITE_A)
                .destinataire(DestinatairePayload.builder().type(TypeDestinataire.CLIENT).clientNom("C").build())
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build()).getId();
        bonSortieService.soumettre(id);
        return id;
    }

    /**
     * Lance chaque action dans son propre fil, toutes ensemble (barrière), avec la session du
     * test (le contexte de sécurité est propre à chaque fil). Renvoie l'issue de chacune :
     * {@code null} si elle a réussi, sinon l'exception.
     */
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

    private static void unSuccesEtUnConflit(List<Throwable> issues) {
        assertThat(issues).filteredOn(t -> t == null).as("une seule validation réussit").hasSize(1);
        assertThat(issues).filteredOn(t -> t != null)
                .singleElement()
                .satisfies(t -> assertThat(t).as("409 : transition invalide, ou conflit rejoué sans succès")
                        .isInstanceOfAny(StockConflitException.class, ConflitConcurrentException.class));
    }

    @Test
    void deux_validations_simultanees_d_un_bon_d_entree() throws Exception {
        String id = bonEntreeSoumis(20);
        Consumer<String> valider = bon -> bonEntreeService.valider(bon, null);

        unSuccesEtUnConflit(enMemeTemps(List.of(() -> valider.accept(id), () -> valider.accept(id))));

        assertThat(mouvementRepository.findByBonId(id)).as("mouvements générés une seule fois").hasSize(1);
        assertThat(balanceService.quantite(produitId, ENTREPOT)).isEqualTo(20.0);
        assertThat(bonEntreeRepository.findById(id).orElseThrow().getStatut()).isEqualTo(StatutBon.EFFECTIF);
    }

    @Test
    void deux_validations_simultanees_d_un_bon_de_sortie() throws Exception {
        bonEntreeService.valider(bonEntreeSoumis(20), null);
        String id = bonSortieSoumis(8);

        unSuccesEtUnConflit(enMemeTemps(List.of(
                () -> bonSortieService.valider(id, null), () -> bonSortieService.valider(id, null))));

        assertThat(mouvementRepository.findByBonId(id)).as("mouvements générés une seule fois").hasSize(1);
        assertThat(balanceService.quantite(produitId, ENTREPOT)).as("débité une seule fois").isEqualTo(12.0);
        assertThat(bonSortieRepository.findById(id).orElseThrow().getStatut()).isEqualTo(StatutBon.EFFECTIF);
    }

    @Test
    void deux_sorties_concurrentes_sur_un_stock_qui_n_en_couvre_qu_une() throws Exception {
        bonEntreeService.valider(bonEntreeSoumis(10), null);
        String bonA = bonSortieSoumis(8);
        String bonB = bonSortieSoumis(8);

        List<Throwable> issues = enMemeTemps(List.of(
                () -> bonSortieService.valider(bonA, null), () -> bonSortieService.valider(bonB, null)));

        assertThat(issues).filteredOn(t -> t == null).hasSize(1);
        assertThat(issues).filteredOn(t -> t != null).singleElement()
                .satisfies(t -> assertThat(t).isInstanceOfAny(StockInsuffisantException.class, ConflitConcurrentException.class));
        assertThat(balanceService.quantite(produitId, ENTREPOT)).as("jamais négatif").isEqualTo(2.0);
        long effectifs = bonSortieRepository.findAll().stream().filter(b -> b.getStatut() == StatutBon.EFFECTIF).count();
        assertThat(effectifs).isEqualTo(1);
        assertThat(mouvementRepository.findAll()).filteredOn(m -> bonA.equals(m.getBonId()) || bonB.equals(m.getBonId()))
                .hasSize(1);
    }
}
