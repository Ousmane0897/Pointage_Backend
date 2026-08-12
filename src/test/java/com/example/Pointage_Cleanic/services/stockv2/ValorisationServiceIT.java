package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.CoutProduitDto;
import com.example.Pointage_Cleanic.Dto.stockv2.HistoriqueCoutProduitDto;
import com.example.Pointage_Cleanic.Dto.stockv2.LigneCoutMouvementDto;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.ParametrageValorisationDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ParametrageValorisationPayload;
import com.example.Pointage_Cleanic.Enum.stockv2.AlerteCout;
import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.AuthentificationTest;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.HistoriquePointCout;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ParametrageValorisation;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
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
class ValorisationServiceIT extends MongoTestContainer {

    @Autowired private BonEntreeService bonEntreeService;
    @Autowired private ParametrageValorisationService parametrageService;
    @Autowired private CoutProduitService coutProduitService;
    @Autowired private CoutMouvementService coutMouvementService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private static final String SITE_A = "siteA";

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), HistoriquePointCout.class);
        mongoTemplate.remove(new Query(), ParametrageValorisation.class);
        mongoTemplate.remove(new Query(), SiteClient.class);
        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar").ville("Dakar").actif(true).build());

        // Clôture d'inventaire, suppression de référentiel et décision sur un bon sont
        // réservées au contrôleur de stock / super-administrateur depuis l'ajout des
        // habilitations : sans session, ce test ne pourrait plus préparer ses données.
        AuthentificationTest.connecterSuperAdmin(mongoTemplate);
    }

    @org.junit.jupiter.api.AfterEach
    void deconnecter() {
        AuthentificationTest.deconnecter();
    }

    private String produit(String code, long prix, MethodeValorisation methode) {
        return produitRepository.save(ProduitStock.builder()
                .code(code).libelle("Produit " + code).typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(prix)
                .methodeValorisation(methode).actif(true).build()).getId();
    }

    private void entree(String produitId, double quantite, Long prixAchat) {
        BonEntreePayload payload = BonEntreePayload.builder()
                .type(TypeEntree.ACHAT_FOURNISSEUR)
                .siteDestinationId(SITE_A)
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(quantite).prixUnitaire(prixAchat).build()))
                .build();
        String id = bonEntreeService.creer(payload).getId();
        bonEntreeService.soumettre(id);
        bonEntreeService.valider(id, null);
    }

    @Test
    void cump_recalcule_le_cout_courant_et_historise() {
        String id = produit("P-CUMP", 1000L, MethodeValorisation.CUMP);
        // Stock initial 100 @ 1000 (entrée au même prix → coût inchangé)
        entree(id, 100, 1000L);
        // 50 @ 1300 → CUMP = (100*1000 + 50*1300)/150 = 1100
        entree(id, 50, 1300L);

        ProduitStock p = produitRepository.findById(id).orElseThrow();
        assertThat(p.getPrixUnitaire()).isEqualTo(1100L);

        List<HistoriquePointCout> points = mongoTemplate.find(new Query(), HistoriquePointCout.class);
        assertThat(points).extracting(HistoriquePointCout::getCout).contains(1100L);
    }

    @Test
    void dernier_prix_prend_le_dernier_prix_achat() {
        String id = produit("P-DP", 1000L, MethodeValorisation.DERNIER_PRIX);
        entree(id, 10, 1700L);
        assertThat(produitRepository.findById(id).orElseThrow().getPrixUnitaire()).isEqualTo(1700L);
    }

    @Test
    void fixe_ne_recalcule_pas_le_cout() {
        String id = produit("P-FIXE", 1000L, MethodeValorisation.FIXE);
        entree(id, 10, 9999L);
        assertThat(produitRepository.findById(id).orElseThrow().getPrixUnitaire()).isEqualTo(1000L);
        assertThat(mongoTemplate.find(new Query(), HistoriquePointCout.class)).isEmpty();
    }

    @Test
    void mouvement_porte_le_snapshot_au_prix_achat() {
        String id = produit("P-SNAP", 1000L, MethodeValorisation.CUMP);
        entree(id, 10, 1200L);
        MouvementStock m = mongoTemplate.find(new Query(), MouvementStock.class).get(0);
        assertThat(m.getCoutUnitaireSnapshot()).isEqualTo(1200L);
        assertThat(m.getValeurMouvement()).isEqualTo(12_000L);
    }

    @Test
    void mouvement_sans_snapshot_est_marque_estime() {
        String id = produit("P-LEGACY", 800L, MethodeValorisation.FIXE);
        // Mouvement « pré-7.6 » sans snapshot
        mongoTemplate.save(MouvementStock.builder()
                .reference("MVT-LEGACY").produitId(id).produitCode("P-LEGACY").produitLibelle("Produit P-LEGACY")
                .type(TypeMouvement.SORTIE).quantite(4).siteSourceId(SITE_A).origine("BON")
                .date(LocalDate.now()).build());

        LigneCoutMouvementDto ligne = coutMouvementService
                .list(0, 20, null, id, null, null, null, null).content().get(0);
        assertThat(ligne.isEstEstime()).isTrue();
        assertThat(ligne.getCoutUnitaire()).isEqualTo(800L);
        assertThat(ligne.getValeur()).isEqualTo(3_200L);
    }

    @Test
    void parametrage_defaut_fixe_puis_mise_a_jour() {
        ParametrageValorisationDto defaut = parametrageService.get();
        assertThat(defaut.getMethodeDefaut()).isEqualTo(MethodeValorisation.FIXE);

        ParametrageValorisationDto maj = parametrageService.update(
                ParametrageValorisationPayload.builder().methodeDefaut(MethodeValorisation.CUMP).build());
        assertThat(maj.getMethodeDefaut()).isEqualTo(MethodeValorisation.CUMP);
        assertThat(parametrageService.methodeDefaut()).isEqualTo(MethodeValorisation.CUMP);
    }

    @Test
    void couts_produits_alerte_cout_zero_et_methode_non_definie() {
        produit("P-ZERO", 0L, null);   // ni override, ni global → METHODE_NON_DEFINIE + COUT_ZERO
        CoutProduitDto dto = coutProduitService.list(0, 20, null, null, null, null, null)
                .content().get(0);
        assertThat(dto.getAlertes()).contains(AlerteCout.COUT_ZERO, AlerteCout.METHODE_NON_DEFINIE);
        assertThat(dto.getMethodeEffective()).isEqualTo(MethodeValorisation.FIXE);
    }

    @Test
    void couts_produits_filtre_avec_alerte() {
        produit("P-OK", 1000L, MethodeValorisation.FIXE);   // pas d'alerte
        produit("P-KO", 0L, MethodeValorisation.FIXE);      // COUT_ZERO
        var page = coutProduitService.list(0, 20, null, null, null, null, true);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).getProduitCode()).isEqualTo("P-KO");
    }
}
