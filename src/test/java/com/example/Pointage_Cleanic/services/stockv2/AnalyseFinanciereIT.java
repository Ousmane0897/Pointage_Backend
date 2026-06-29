package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ComparatifCoutSitesDto;
import com.example.Pointage_Cleanic.Dto.stockv2.SyntheseMargesDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ValeurStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.PeriodeComparaison;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class AnalyseFinanciereIT extends MongoTestContainer {

    @Autowired private MargesService margesService;
    @Autowired private ValeurStockService valeurStockService;
    @Autowired private CoutSiteService coutSiteService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private static final LocalDate DEBUT = LocalDate.now().minusDays(10);
    private static final LocalDate FIN = LocalDate.now();

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
    }

    private String produit(String code, long cout, Long prixVente) {
        return produitRepository.save(ProduitStock.builder()
                .code(code).libelle("Produit " + code).typeProduit(TypeProduit.PRODUIT_FINI)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(cout).prixVente(prixVente)
                .actif(true).build()).getId();
    }

    private void sortieBon(String produitId, String code, String siteId, double qte, long coutSnapshot, TypeSortie type) {
        mongoTemplate.save(MouvementStock.builder()
                .reference("MVT-" + code + "-" + siteId + "-" + qte)
                .produitId(produitId).produitCode(code).produitLibelle("Produit " + code)
                .unite(UniteStock.PIECE).type(TypeMouvement.SORTIE).quantite(qte)
                .siteSourceId(siteId).date(LocalDate.now().minusDays(1)).origine("BON")
                .categorieSortie(type).coutUnitaireSnapshot(coutSnapshot)
                .valeurMouvement(Math.round(qte * coutSnapshot)).build());
    }

    @Test
    void marges_calcule_taux_rentabilite_et_totaux() {
        String p1 = produit("P1", 1000L, 1500L);   // marge 500, taux 33% → rentable
        String p2 = produit("P2", 1000L, 1050L);   // marge 50, taux 4.7% → non rentable
        produit("P3", 1000L, null);                 // pas de prix de vente → exclu

        sortieBon(p1, "P1", "siteA", 10, 1000L, TypeSortie.VENTE_PRODUIT);
        sortieBon(p2, "P2", "siteA", 5, 1000L, TypeSortie.VENTE_PRODUIT);

        SyntheseMargesDto synthese = margesService.synthese(DEBUT, FIN, null);
        assertThat(synthese.getLignes()).hasSize(2);
        assertThat(synthese.getNbProduitsNonRentables()).isEqualTo(1);
        assertThat(synthese.getChiffreAffaires()).isEqualTo(20_250L);  // 1500*10 + 1050*5
        assertThat(synthese.getCoutTotal()).isEqualTo(15_000L);
        assertThat(synthese.getMargeGlobaleTotale()).isEqualTo(5_250L);

        SyntheseMargesDto.LigneMarge ligneP1 = synthese.getLignes().stream()
                .filter(l -> l.getProduitCode().equals("P1")).findFirst().orElseThrow();
        assertThat(ligneP1.isRentable()).isTrue();
        assertThat(ligneP1.getMargeUnitaire()).isEqualTo(500L);
    }

    @Test
    void valeur_stock_courante_et_repartition() {
        String p1 = produit("V1", 1000L, null);
        String p2 = produit("V2", 2000L, null);
        mongoTemplate.save(StockParSite.builder().produitId(p1).siteId("siteA").quantite(20).build());
        mongoTemplate.save(StockParSite.builder().produitId(p2).siteId("siteA").quantite(5).build());

        ValeurStockDto valeur = valeurStockService.valeur(null, null, null);
        assertThat(valeur.getKpis().getValeurTotale()).isEqualTo(30_000L);  // 20*1000 + 5*2000
        assertThat(valeur.getKpis().getNbProduits()).isEqualTo(2);
        assertThat(valeur.getLignes()).hasSize(2);
        assertThat(valeur.getDateCalcul()).isNotBlank();
    }

    @Test
    void valeur_precedente_reconstruite_par_rejeu() {
        String p1 = produit("R1", 1000L, null);
        // Solde courant 20 = entrée 30 (il y a 20 j) − sortie 10 (hier)
        mongoTemplate.save(StockParSite.builder().produitId(p1).siteId("siteA").quantite(20).build());
        mongoTemplate.save(MouvementStock.builder().reference("E1").produitId(p1).type(TypeMouvement.ENTREE)
                .quantite(30).siteDestinationId("siteA").date(LocalDate.now().minusDays(20)).build());
        // Sortie 10 hier → avant-hier le stock valait 30, aujourd'hui 20
        mongoTemplate.save(MouvementStock.builder().reference("S1").produitId(p1).type(TypeMouvement.SORTIE)
                .quantite(10).siteSourceId("siteA").date(LocalDate.now().minusDays(1)).origine("BON").build());

        ValeurStockDto valeur = valeurStockService.valeur(null, null, PeriodeComparaison.SEMAINE);
        // refDate = today-7j : stock = 30 (la sortie d'hier n'est pas encore passée) → 30000
        assertThat(valeur.getKpis().getValeurPrecedente()).isEqualTo(30_000L);
        assertThat(valeur.getKpis().getValeurTotale()).isEqualTo(20_000L);
        assertThat(valeur.getKpis().getEcartValeur()).isEqualTo(-10_000L);
    }

    @Test
    void cout_site_classe_et_detecte_surconsommation() {
        String p = produit("C1", 1000L, null);
        sortieBon(p, "C1", "siteA", 10, 1000L, TypeSortie.CONSOMMATION_INTERNE);   // 10000
        sortieBon(p, "C1", "siteB", 1, 1000L, TypeSortie.CONSOMMATION_INTERNE);    // 1000

        ComparatifCoutSitesDto comparatif = coutSiteService.comparatif(DEBUT, FIN, null);
        assertThat(comparatif.getCoutTotalGlobal()).isEqualTo(11_000L);
        assertThat(comparatif.getLignes()).hasSize(2);
        // tri décroissant : siteA en tête
        assertThat(comparatif.getLignes().get(0).getSiteId()).isEqualTo("siteA");
        assertThat(comparatif.getLignes().get(0).isSurconsommation()).isTrue();
        assertThat(comparatif.getNbSitesSurconsommation()).isGreaterThanOrEqualTo(1);
    }
}
