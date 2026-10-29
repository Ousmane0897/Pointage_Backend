package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonSortieDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.ComparatifCoutSitesDto;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LigneRapportDto;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeRapportConsommation;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.List;

import static com.example.Pointage_Cleanic.services.stockv2.StockBalanceService.ENTREPOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Bon de sortie dont le site source est saisi hors référentiel (chantier, prestation ponctuelle). */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class BonSortieSiteHorsReferentielIT extends MongoTestContainer {

    @Autowired private BonSortieService bonSortieService;
    @Autowired private CoutSiteService coutSiteService;
    @Autowired private ConsommationStockService consommationService;
    @Autowired private StockBalanceService balanceService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private static final String SITE_A = "siteA";
    private static final LocalDate JOUR = LocalDate.of(2026, 5, 10);
    private String produitId;

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
        mongoTemplate.save(StockParSite.builder().produitId(produitId).siteId(ENTREPOT).quantite(100).build());
        AuthentificationTest.connecterSuperAdmin(mongoTemplate);
    }

    @AfterEach
    void deconnecter() {
        AuthentificationTest.deconnecter();
    }

    private BonSortiePayload payload(String siteId, String siteNom, double qte) {
        return BonSortiePayload.builder()
                .type(TypeSortie.CONSOMMATION_INTERNE)
                .date(JOUR)
                .siteSourceId(siteId)
                .siteSourceNom(siteNom)
                .destinataire(DestinatairePayload.builder().type(TypeDestinataire.CLIENT).clientNom("C").build())
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build();
    }

    private void sortieValide(String siteId, String siteNom, double qte) {
        String id = bonSortieService.creer(payload(siteId, siteNom, qte)).getId();
        bonSortieService.soumettre(id);
        bonSortieService.valider(id, null);
    }

    @Test
    void nom_libre_sans_site_cree_un_bon_hors_referentiel() {
        BonSortieDto bon = bonSortieService.creer(payload(null, "  Chantier   Diamniadio ", 2));

        assertThat(bon.getSiteSourceId()).isNull();
        assertThat(bon.getSiteSourceNom()).isEqualTo("Chantier Diamniadio");
    }

    @Test
    void site_du_referentiel_prime_sur_le_nom_envoye() {
        BonSortieDto bon = bonSortieService.creer(payload(SITE_A, "Nom forgé", 2));

        assertThat(bon.getSiteSourceId()).isEqualTo(SITE_A);
        assertThat(bon.getSiteSourceNom()).isEqualTo("Dakar Plateau");
    }

    @Test
    void ni_site_ni_nom_est_refuse() {
        assertThatThrownBy(() -> bonSortieService.creer(payload(null, "   ", 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Site source obligatoire");
    }

    @Test
    void nom_trop_long_est_refuse() {
        String nom = "x".repeat(BonSortieService.LONGUEUR_MAX_SITE_LIBRE + 1);
        assertThatThrownBy(() -> bonSortieService.creer(payload(null, nom, 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modifier_bascule_entre_referentiel_et_site_libre() {
        String id = bonSortieService.creer(payload(SITE_A, null, 2)).getId();

        BonSortieDto libre = bonSortieService.modifier(id, payload(null, "Chantier X", 2));
        assertThat(libre.getSiteSourceId()).isNull();
        assertThat(libre.getSiteSourceNom()).isEqualTo("Chantier X");

        BonSortieDto ref = bonSortieService.modifier(id, payload(SITE_A, null, 2));
        assertThat(ref.getSiteSourceId()).isEqualTo(SITE_A);
        assertThat(ref.getSiteSourceNom()).isEqualTo("Dakar Plateau");
    }

    @Test
    void validation_d_un_site_libre_debite_l_entrepot() {
        sortieValide(null, "Chantier X", 30);

        assertThat(balanceService.quantite(produitId, ENTREPOT)).isEqualTo(70.0);
    }

    @Test
    void rapports_par_site_une_ligne_par_nom_libre() {
        sortieValide(null, "Chantier X", 10);
        sortieValide(null, "chantier  x", 5);   // même site, saisi autrement
        sortieValide(null, "Chantier Y", 3);
        sortieValide(SITE_A, null, 1);

        ComparatifCoutSitesDto cout = coutSiteService.comparatif(JOUR, JOUR, null);
        assertThat(cout.getLignes()).hasSize(3);
        ComparatifCoutSitesDto.LigneSite x = cout.getLignes().stream()
                .filter(l -> "Chantier X".equals(l.getSiteNom())).findFirst().orElseThrow();
        assertThat(x.getSiteId()).isNull();
        assertThat(x.getQuantiteTotale()).isEqualTo(15.0);

        List<LigneRapportDto> rapport = consommationService
                .rapport(TypeRapportConsommation.PAR_SITE, JOUR, JOUR, null, null, null).getLignes();
        assertThat(rapport).hasSize(3);
        assertThat(rapport).anySatisfy(l -> {
            assertThat(l.getCle()).isEqualTo("libre:chantier x");
            assertThat(l.getQuantite()).isEqualTo(15.0);
        });
    }
}
