package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ChantierDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ChantierPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DetailChantierDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutChantier;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.Chantier;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class ChantierServiceIT extends MongoTestContainer {

    @Autowired private ChantierService chantierService;
    @Autowired private BonSortieService bonSortieService;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MouvementStockRepository mouvementRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private String produitId;
    private static final String SITE_A = "siteA";

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), Chantier.class);
        mongoTemplate.remove(new Query(), BonSortie.class);
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), SiteClient.class);

        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar Plateau").ville("Dakar").actif(true).build());
        produitId = produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
        mongoTemplate.save(StockParSite.builder().produitId(produitId).siteId(SITE_A).quantite(100).build());
    }

    private ChantierPayload chantierPayload(String reference) {
        return ChantierPayload.builder()
                .reference(reference).nom("Chantier Test").siteId(SITE_A)
                .client("Client A").dateDebut(LocalDate.of(2026, 6, 1)).build();
    }

    private String chantierBonValide(String chantierId, double qte) {
        BonSortiePayload payload = BonSortiePayload.builder()
                .type(TypeSortie.DISTRIBUTION_CHANTIER)
                .siteSourceId(SITE_A)
                .destinataire(DestinatairePayload.builder().type(TypeDestinataire.SITE).siteId(SITE_A).build())
                .chantierId(chantierId)
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(qte).build()))
                .build();
        String id = bonSortieService.creer(payload).getId();
        bonSortieService.soumettre(id);
        bonSortieService.valider(id, null);
        return id;
    }

    @Test
    void cloture_fige_le_chantier_et_refuse_double_cloture() {
        ChantierDto cree = chantierService.creer(chantierPayload("CH-001"));
        assertThat(cree.getStatut()).isEqualTo(StatutChantier.EN_COURS);

        ChantierDto cloture = chantierService.cloturer(cree.getId());
        assertThat(cloture.getStatut()).isEqualTo(StatutChantier.CLOTURE);
        assertThat(cloture.getDateFin()).isEqualTo(LocalDate.now());

        assertThatThrownBy(() -> chantierService.cloturer(cree.getId()))
                .isInstanceOf(StockConflitException.class);
    }

    @Test
    void edition_et_suppression_refusees_si_cloture() {
        ChantierDto cree = chantierService.creer(chantierPayload("CH-002"));
        chantierService.cloturer(cree.getId());

        assertThatThrownBy(() -> chantierService.modifier(cree.getId(), chantierPayload("CH-002")))
                .isInstanceOf(StockConflitException.class);
        assertThatThrownBy(() -> chantierService.supprimer(cree.getId()))
                .isInstanceOf(StockConflitException.class);
    }

    @Test
    void reference_dupliquee_rejetee_409() {
        chantierService.creer(chantierPayload("CH-003"));
        assertThatThrownBy(() -> chantierService.creer(chantierPayload("CH-003")))
                .isInstanceOf(StockConflitException.class);
    }

    @Test
    void validation_bon_chantier_propage_chantierId_et_alimente_le_detail() {
        ChantierDto chantier = chantierService.creer(chantierPayload("CH-004"));
        chantierBonValide(chantier.getId(), 8);

        List<MouvementStock> mvts = mouvementRepository.findByChantierId(chantier.getId());
        assertThat(mvts).hasSize(1);
        assertThat(mvts.get(0).getChantierId()).isEqualTo(chantier.getId());
        assertThat(mvts.get(0).getChantierReference()).isEqualTo("CH-004");

        DetailChantierDto detail = chantierService.detail(chantier.getId());
        assertThat(detail.getCoutTotal()).isEqualTo(8_000L);   // 8 × 1000 FCFA
        assertThat(detail.getNbProduits()).isEqualTo(1);
        assertThat(detail.getLignes()).hasSize(1);
        assertThat(detail.getLignes().get(0).getMontant()).isEqualTo(8_000L);
        assertThat(detail.getLignes().get(0).getQuantite()).isEqualTo(8.0);
        assertThat(detail.getChantier().getCoutTotal()).isEqualTo(8_000L);
        assertThat(detail.getChantier().getNbMouvements()).isEqualTo(1);
    }

    @Test
    void validation_refusee_si_chantier_cloture() {
        ChantierDto chantier = chantierService.creer(chantierPayload("CH-005"));
        BonSortiePayload payload = BonSortiePayload.builder()
                .type(TypeSortie.DISTRIBUTION_CHANTIER)
                .siteSourceId(SITE_A)
                .destinataire(DestinatairePayload.builder().type(TypeDestinataire.SITE).siteId(SITE_A).build())
                .chantierId(chantier.getId())
                .lignes(List.of(LignePayload.builder().produitId(produitId).quantite(5).build()))
                .build();
        String bonId = bonSortieService.creer(payload).getId();
        bonSortieService.soumettre(bonId);

        chantierService.cloturer(chantier.getId());

        assertThatThrownBy(() -> bonSortieService.valider(bonId, null))
                .isInstanceOf(StockConflitException.class);
        assertThat(mouvementRepository.findByChantierId(chantier.getId())).isEmpty();
    }
}
