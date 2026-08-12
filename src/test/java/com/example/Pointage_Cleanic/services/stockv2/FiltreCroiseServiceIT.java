package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.ResultatCroiseDto;
import com.example.Pointage_Cleanic.Enum.stockv2.AxeAnalyse;
import com.example.Pointage_Cleanic.Enum.stockv2.MesureCroise;
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
class FiltreCroiseServiceIT extends MongoTestContainer {

    @Autowired private FiltreCroiseService croiseService;
    @Autowired private BonSortieService bonSortieService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String produit1;
    private String produit2;
    private static final String SITE_A = "siteA";
    private static final LocalDate DEBUT = LocalDate.of(2026, 6, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 7, 31);

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), BonSortie.class);
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), SiteClient.class);

        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar Plateau").ville("Dakar").actif(true).build());
        produit1 = produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
        produit2 = produitRepository.save(ProduitStock.builder()
                .code("P2").libelle("Eau").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.L).seuilAlerte(5).prixUnitaire(2000L).actif(true).build()).getId();
        mongoTemplate.save(StockParSite.builder().produitId(produit1).siteId(SITE_A).quantite(1000).build());
        mongoTemplate.save(StockParSite.builder().produitId(produit2).siteId(SITE_A).quantite(1000).build());

        // Les décisions du circuit sont réservées au super-administrateur : sans session,
        // valider/refuser renverraient 403 et ce test ne pourrait plus préparer ses données.
        AuthentificationTest.connecterSuperAdmin(mongoTemplate);
    }

    @org.junit.jupiter.api.AfterEach
    void deconnecter() {
        AuthentificationTest.deconnecter();
    }

    private void sortie(String produitId, double qte, LocalDate date) {
        BonSortiePayload payload = BonSortiePayload.builder()
                .type(TypeSortie.CONSOMMATION_INTERNE)
                .date(date)
                .siteSourceId(SITE_A)
                .destinataire(DestinatairePayload.builder().type(TypeDestinataire.CLIENT).clientNom("C").build())
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build();
        String id = bonSortieService.creer(payload).getId();
        bonSortieService.soumettre(id);
        bonSortieService.valider(id, null);
    }

    private ResultatCroiseDto.LigneCroise ligne(ResultatCroiseDto r, String libelle) {
        return r.getLignes().stream().filter(l -> l.getLibelle().equals(libelle)).findFirst().orElseThrow();
    }

    @Test
    void pivot_1d_sans_axe_colonnes() {
        sortie(produit1, 5, LocalDate.of(2026, 6, 10));   // 5000 FCFA
        sortie(produit2, 3, LocalDate.of(2026, 6, 12));   // 6000 FCFA

        ResultatCroiseDto r = croiseService.croise(
                AxeAnalyse.PRODUIT, null, MesureCroise.MONTANT, DEBUT, FIN, null, null, null, null);

        assertThat(r.getEntetesColonnes()).isEmpty();
        assertThat(r.getTotauxColonnes()).isEmpty();
        assertThat(r.getLignes()).hasSize(2);
        assertThat(r.getLignes()).allSatisfy(l -> assertThat(l.getValeurs()).isEmpty());
        assertThat(ligne(r, "Savon").getTotal()).isEqualTo(5_000.0);
        assertThat(ligne(r, "Eau").getTotal()).isEqualTo(6_000.0);
        assertThat(r.getTotalGeneral()).isEqualTo(11_000.0);
    }

    @Test
    void pivot_2d_produit_par_mois() {
        sortie(produit1, 5, LocalDate.of(2026, 6, 10));
        sortie(produit1, 2, LocalDate.of(2026, 7, 5));
        sortie(produit2, 3, LocalDate.of(2026, 6, 12));

        ResultatCroiseDto r = croiseService.croise(
                AxeAnalyse.PRODUIT, AxeAnalyse.MOIS, MesureCroise.QUANTITE, DEBUT, FIN, null, null, null, null);

        assertThat(r.getEntetesColonnes()).containsExactly("2026-06", "2026-07");
        assertThat(ligne(r, "Savon").getValeurs()).containsExactly(5.0, 2.0);
        assertThat(ligne(r, "Savon").getTotal()).isEqualTo(7.0);
        assertThat(ligne(r, "Eau").getValeurs()).containsExactly(3.0, 0.0);
        assertThat(r.getTotauxColonnes()).containsExactly(8.0, 2.0);
        assertThat(r.getTotalGeneral()).isEqualTo(10.0);
    }
}
