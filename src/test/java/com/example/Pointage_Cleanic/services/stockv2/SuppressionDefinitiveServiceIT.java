package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreeDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortieDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.ComptagePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.InventaireDto;
import com.example.Pointage_Cleanic.Dto.stockv2.InventairePlanifPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Enum.stockv2.PerimetreInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.Inventaire;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.stockv2.SuppressionStockLog;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.StockAccesRefuseException;
import com.example.Pointage_Cleanic.exception.StockOperationException;
import com.example.Pointage_Cleanic.repositories.stockv2.BonEntreeRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.BonSortieRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.InventaireRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.SuppressionStockLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suppression définitive (super-administrateur) : contre-passement de l'effet stock, nettoyage des
 * mouvements et journalisation.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class SuppressionDefinitiveServiceIT extends MongoTestContainer {

    @Autowired private SuppressionDefinitiveService suppressionService;
    @Autowired private InventaireService inventaireService;
    @Autowired private BonEntreeService bonEntreeService;
    @Autowired private BonSortieService bonSortieService;
    @Autowired private StockBalanceService balanceService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MouvementStockRepository mouvementRepository;
    @Autowired private InventaireRepository inventaireRepository;
    @Autowired private BonEntreeRepository bonEntreeRepository;
    @Autowired private BonSortieRepository bonSortieRepository;
    @Autowired private SuppressionStockLogRepository logRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String produitId;
    private static final String SITE_A = "siteA";
    private static final String MOTIF = "Erreur de saisie constatée par la direction";

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), Inventaire.class);
        mongoTemplate.remove(new Query(), BonEntree.class);
        mongoTemplate.remove(new Query(), BonSortie.class);
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), SiteClient.class);
        mongoTemplate.remove(new Query(), SuppressionStockLog.class);
        mongoTemplate.remove(new Query(), User.class);

        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar").ville("Dakar").actif(true).build());
        produitId = produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
        // Le rôle SUPERADMIN n'existe que dans la collection `login` (cf. CurrentUserProvider).
        seConnecter("boss@cleanic.sn", "SUPERADMIN");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void seConnecter(String email, String role) {
        User compte = new User();
        compte.setEmail(email);
        compte.setPassword("x");
        compte.setRole(role);
        mongoTemplate.save(compte);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    /** Inventaire clôturé sur un écart de -5 (20 comptés 15). */
    private InventaireDto inventaireCloture() {
        balanceService.appliquerDelta(produitId, SITE_A, 20);
        InventaireDto inv = inventaireService.create(InventairePlanifPayload.builder()
                .libelle("Inventaire mensuel").datePlanifiee(LocalDate.now()).siteId(SITE_A)
                .perimetre(PerimetreInventaire.SELECTION).produitIds(List.of(produitId))
                .seuilEcartJustification(2).build());
        inventaireService.demarrerComptage(inv.getId());
        inventaireService.enregistrerComptage(inv.getId(), ComptagePayload.builder()
                .lignes(List.of(ComptagePayload.LigneComptage.builder()
                        .produitId(produitId).qtePhysique(15.0).justification("Casse constatée").build()))
                .build());
        inventaireService.valider(inv.getId());
        return inventaireService.cloturer(inv.getId());
    }

    @Test
    void suppression_inventaire_cloture_contre_passe_les_ecarts_et_journalise() {
        InventaireDto inv = inventaireCloture();
        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(15.0);
        assertThat(mouvementRepository.findByInventaireId(inv.getId())).hasSize(1);

        suppressionService.supprimerInventaire(inv.getId(), MOTIF);

        // Stock revenu à sa valeur d'avant clôture, ajustement effacé, inventaire supprimé.
        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(20.0);
        assertThat(mouvementRepository.findByInventaireId(inv.getId())).isEmpty();
        assertThat(inventaireRepository.findById(inv.getId())).isEmpty();

        List<SuppressionStockLog> logs = logRepository.findByDocumentId(inv.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getTypeDocument()).isEqualTo(SuppressionStockLog.TypeDocument.INVENTAIRE);
        assertThat(logs.get(0).getStatutAvant()).isEqualTo("CLOTURE");
        assertThat(logs.get(0).getMotif()).isEqualTo(MOTIF);
        assertThat(logs.get(0).getAuteurNom()).isEqualTo("boss@cleanic.sn");
        assertThat(logs.get(0).getNbMouvementsContrePasses()).isEqualTo(1);
        assertThat(logs.get(0).getLignes()).singleElement()
                .satisfies(l -> assertThat(l.getDelta()).isEqualTo(5.0));
    }

    /** Repli pour les inventaires clôturés avant l'ajout d'{@code inventaireId}. */
    @Test
    void suppression_inventaire_retrouve_les_ajustements_par_commentaire_si_inventaireId_absent() {
        InventaireDto inv = inventaireCloture();
        MouvementStock ajustement = mouvementRepository.findByInventaireId(inv.getId()).get(0);
        ajustement.setInventaireId(null);
        ajustement.setInventaireReference(null);
        ajustement.setOrigine(null);
        mouvementRepository.save(ajustement);

        suppressionService.supprimerInventaire(inv.getId(), MOTIF);

        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(20.0);
        assertThat(mouvementRepository.findById(ajustement.getId())).isEmpty();
    }

    /**
     * La clôture d'un inventaire applique les écarts au stock réel : contrôleur de stock ou
     * super-administrateur, comme la suppression définitive.
     */
    @Test
    void cloture_d_inventaire_refusee_403_hors_controleur_ou_superadmin() {
        balanceService.appliquerDelta(produitId, SITE_A, 20);
        InventaireDto inv = inventaireService.create(InventairePlanifPayload.builder()
                .libelle("Inventaire mensuel").datePlanifiee(LocalDate.now()).siteId(SITE_A)
                .perimetre(PerimetreInventaire.SELECTION).produitIds(List.of(produitId))
                .seuilEcartJustification(2).build());
        inventaireService.demarrerComptage(inv.getId());
        inventaireService.enregistrerComptage(inv.getId(), ComptagePayload.builder()
                .lignes(List.of(ComptagePayload.LigneComptage.builder()
                        .produitId(produitId).qtePhysique(15.0).justification("Casse").build()))
                .build());
        inventaireService.valider(inv.getId());

        SecurityContextHolder.clearContext();
        seConnecter("agent@cleanic.sn", "MAGASINIER");

        assertThatThrownBy(() -> inventaireService.cloturer(inv.getId()))
                .isInstanceOf(StockAccesRefuseException.class);

        // Aucun écart appliqué : le stock reste au théorique.
        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(20.0);
    }

    @Test
    void suppression_refusee_403_hors_superadmin() {
        InventaireDto inv = inventaireCloture();
        SecurityContextHolder.clearContext();
        seConnecter("magasinier@cleanic.sn", "CONTROLEUR_STOCK");

        assertThatThrownBy(() -> suppressionService.supprimerInventaire(inv.getId(), MOTIF))
                .isInstanceOf(StockAccesRefuseException.class);

        assertThat(inventaireRepository.findById(inv.getId())).isPresent();
        assertThat(balanceService.quantite(produitId, SITE_A)).isEqualTo(15.0);
    }

    @Test
    void suppression_refusee_400_si_motif_absent_ou_trop_court() {
        InventaireDto inv = inventaireCloture();

        assertThatThrownBy(() -> suppressionService.supprimerInventaire(inv.getId(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> suppressionService.supprimerInventaire(inv.getId(), "erreur"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(inventaireRepository.findById(inv.getId())).isPresent();
    }
}
