package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreeDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortieDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DecisionPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Enum.audit.ActionAudit;
import com.example.Pointage_Cleanic.Enum.stockv2.ActionWorkflow;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.AuthentificationTest;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.audit.EntreeAudit;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.entities.pieces.CompteurPiece;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.StockAccesRefuseException;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.exception.StockOperationException;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
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
        mongoTemplate.remove(new Query(), CompteurPiece.class);
        mongoTemplate.remove(new Query(), EvenementDomaine.class);
        mongoTemplate.remove(new Query(), EntreeAudit.class);

        mongoTemplate.save(SiteClient.builder().id(SITE_A).code("A").nom("Dakar Plateau").ville("Dakar").actif(true).build());
        produitId = produitRepository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();

        // Les décisions du circuit sont réservées au super-administrateur : sans session,
        // valider/refuser renverraient 403 et ce test ne pourrait plus préparer ses données.
        AuthentificationTest.connecterSuperAdmin(mongoTemplate);
    }

    @org.junit.jupiter.api.AfterEach
    void deconnecter() {
        AuthentificationTest.deconnecter();
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
        // Lot 1 / 1b (D4) : pas de numéro en brouillon, un libellé provisoire.
        assertThat(cree.getReference()).isNull();
        assertThat(cree.getLibelleNumero()).isEqualTo("BROUILLON-" + cree.getId().substring(cree.getId().length() - 6));
        assertThat(cree.getStatut()).isEqualTo(StatutBon.BROUILLON);
        assertThat(cree.getMontantTotal()).isEqualTo(20_000L);
        assertThat(cree.getLignes().get(0).getProduitCode()).isEqualTo("P1");

        bonEntreeService.soumettre(cree.getId());
        BonEntreeDto valide = bonEntreeService.valider(cree.getId(), null);

        assertThat(valide.getStatut()).isEqualTo(StatutBon.EFFECTIF);
        assertThat(valide.getReference()).matches("BE-\\d{4}-0001");
        assertThat(valide.getLibelleNumero()).isEqualTo(valide.getReference());
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
        assertThat(sortie.getReference()).isNull();
        bonSortieService.soumettre(sortie.getId());
        BonSortieDto valide = bonSortieService.valider(sortie.getId(), null);

        assertThat(valide.getStatut()).isEqualTo(StatutBon.EFFECTIF);
        assertThat(valide.getReference()).matches("BS-\\d{4}-0001");
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

        // Rien validé : statut inchangé, aucun mouvement, solde nul, aucun numéro consommé.
        assertThat(bonSortieService.getById(sortie.getId()).getStatut()).isEqualTo(StatutBon.SOUMIS);
        assertThat(bonSortieService.getById(sortie.getId()).getReference()).isNull();
        assertThat(mongoTemplate.count(new Query(), CompteurPiece.class)).isZero();
        assertThat(evenements("PIECE_VALIDEE")).isZero();
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

    // --- Habilitations (bons de sortie) ---------------------------------------

    /**
     * Valider engage le stock : super-administrateur seul. Le front masque le bouton, mais l'API
     * restait ouverte à tout compte authentifié.
     */
    @Test
    void decision_par_un_profil_non_superadmin_403_sans_effet() {
        BonEntreeDto entree = bonEntreeService.creer(entreePayload(20));
        bonEntreeService.soumettre(entree.getId());
        bonEntreeService.valider(entree.getId(), null);
        BonSortieDto sortie = bonSortieService.creer(sortiePayload(5));
        bonSortieService.soumettre(sortie.getId());

        AuthentificationTest.connecter(mongoTemplate, "magasinier@cleanic.sn",
                AuthentificationTest.CONTROLEUR_STOCK);

        assertThatThrownBy(() -> bonSortieService.valider(sortie.getId(), null))
                .isInstanceOf(StockAccesRefuseException.class);
        assertThatThrownBy(() -> bonSortieService.refuser(sortie.getId(), new DecisionPayload("non conforme")))
                .isInstanceOf(StockAccesRefuseException.class);

        // Rien n'a bougé : statut inchangé, stock intact.
        assertThat(bonSortieService.getById(sortie.getId()).getStatut()).isEqualTo(StatutBon.SOUMIS);
        assertThat(balanceService.quantite(produitId, ENTREPOT)).isEqualTo(20.0);
    }

    /** Modifier, abandonner et soumettre restent au créateur (ou au contrôleur / super-admin). */
    @Test
    void brouillon_d_un_autre_utilisateur_403_en_modification_abandon_et_soumission() {
        BonSortieDto sortie = bonSortieService.creer(sortiePayload(3));

        AuthentificationTest.connecter(mongoTemplate, "tiers@cleanic.sn", "MAGASINIER");

        assertThatThrownBy(() -> bonSortieService.modifier(sortie.getId(), sortiePayload(4)))
                .isInstanceOf(StockAccesRefuseException.class);
        assertThatThrownBy(() -> bonSortieService.abandonner(sortie.getId()))
                .isInstanceOf(StockAccesRefuseException.class);
        assertThatThrownBy(() -> bonSortieService.soumettre(sortie.getId()))
                .isInstanceOf(StockAccesRefuseException.class);

        assertThat(bonSortieService.getById(sortie.getId()).getStatut()).isEqualTo(StatutBon.BROUILLON);
    }

    /** Le créateur soumet son propre bon sans détenir de rôle particulier. */
    @Test
    void createur_sans_role_particulier_peut_soumettre_son_bon() {
        AuthentificationTest.connecter(mongoTemplate, "agent@cleanic.sn", "MAGASINIER");
        BonSortieDto sortie = bonSortieService.creer(sortiePayload(3));

        BonSortieDto soumis = bonSortieService.soumettre(sortie.getId());

        assertThat(soumis.getStatut()).isEqualTo(StatutBon.SOUMIS);
        assertThat(soumis.getCreeParEmail()).isEqualTo("agent@cleanic.sn");
    }

    /** Les bons d'entrée suivent désormais les mêmes règles que les sorties. */
    @Test
    void decision_sur_un_bon_d_entree_par_un_non_superadmin_403() {
        BonEntreeDto entree = bonEntreeService.creer(entreePayload(10));
        bonEntreeService.soumettre(entree.getId());
        assertThat(bonEntreeService.getById(entree.getId()).getCreeParEmail())
                .isEqualTo(AuthentificationTest.EMAIL_SUPERADMIN);

        AuthentificationTest.connecter(mongoTemplate, "magasinier@cleanic.sn",
                AuthentificationTest.CONTROLEUR_STOCK);

        assertThatThrownBy(() -> bonEntreeService.valider(entree.getId(), null))
                .isInstanceOf(StockAccesRefuseException.class);

        assertThat(bonEntreeService.getById(entree.getId()).getStatut()).isEqualTo(StatutBon.SOUMIS);
        assertThat(balanceService.quantite(produitId, ENTREPOT)).isZero();
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
        // abandonner après soumission
        assertThatThrownBy(() -> bonEntreeService.abandonner(cree.getId()))
                .isInstanceOf(StockConflitException.class);
    }

    // --- Lot 1 / 1b : abandon, audit, événements ---------------------------------

    @Test
    void abandon_d_un_brouillon_le_conserve_trace_et_definitif() {
        BonSortieDto sortie = bonSortieService.creer(sortiePayload(3));

        BonSortieDto abandonne = bonSortieService.abandonner(sortie.getId());

        assertThat(abandonne.getStatut()).isEqualTo(StatutBon.ABANDONNE);
        assertThat(abandonne.getAbandon()).isNotNull();
        assertThat(abandonne.getAbandon().getLe()).isNotNull();
        assertThat(abandonne.getAbandon().getPar().getNom()).isNotBlank();
        assertThat(abandonne.getHistorique()).anyMatch(h -> h.getAction() == ActionWorkflow.ABANDON);
        // Conservé en base, jamais supprimé.
        assertThat(mongoTemplate.count(new Query(), BonSortie.class)).isEqualTo(1);
        // Rejouer, soumettre ou modifier un bon abandonné : 409.
        assertThatThrownBy(() -> bonSortieService.abandonner(sortie.getId())).isInstanceOf(StockConflitException.class);
        assertThatThrownBy(() -> bonSortieService.soumettre(sortie.getId())).isInstanceOf(StockConflitException.class);
        assertThatThrownBy(() -> bonSortieService.modifier(sortie.getId(), sortiePayload(2)))
                .isInstanceOf(StockConflitException.class);
    }

    @Test
    void chaque_transition_a_sa_ligne_d_audit_et_son_evenement() {
        BonEntreeDto cree = bonEntreeService.creer(entreePayload(10));
        bonEntreeService.modifier(cree.getId(), entreePayload(12));
        bonEntreeService.soumettre(cree.getId());
        BonEntreeDto valide = bonEntreeService.valider(cree.getId(), null);

        List<EntreeAudit> audit = mongoTemplate.find(Query.query(Criteria.where("objetId").is(cree.getId())),
                EntreeAudit.class);
        assertThat(audit).extracting(EntreeAudit::getAction).containsExactlyInAnyOrder(
                ActionAudit.CREATION, ActionAudit.MODIFICATION, ActionAudit.SOUMISSION, ActionAudit.VALIDATION);
        assertThat(audit).allSatisfy(a -> {
            assertThat(a.getObjetType()).isEqualTo("BonEntree");
            assertThat(a.getCompteId()).isNotBlank();
        });
        assertThat(audit).filteredOn(a -> a.getAction() == ActionAudit.VALIDATION).singleElement()
                .satisfies(a -> {
                    assertThat(a.getNumero()).isEqualTo(valide.getReference());
                    assertThat(a.getChangements()).anyMatch(c -> c.getChamp().equals("statut")
                            && "SOUMIS".equals(c.getAvant()) && "EFFECTIF".equals(c.getApres()));
                    assertThat(a.getChangements()).anyMatch(c -> c.getChamp().equals("reference"));
                });

        List<EvenementDomaine> evts = mongoTemplate.find(Query.query(Criteria.where("agregatId").is(cree.getId())),
                EvenementDomaine.class);
        assertThat(evts).extracting(EvenementDomaine::getType).containsExactlyInAnyOrder(
                "PIECE_CREEE", "PIECE_MODIFIEE", "PIECE_SOUMISE", "PIECE_VALIDEE");
        assertThat(evts).allSatisfy(e -> assertThat(e.getAgregatType()).isEqualTo("BE"));
        assertThat(evts).filteredOn(e -> e.getType().equals("PIECE_VALIDEE")).singleElement()
                .satisfies(e -> assertThat(e.getPayload()).containsEntry("numero", valide.getReference()));
    }

    @Test
    void un_ancien_brouillon_garde_sa_reference_a_la_validation() {
        // Bon antérieur à 1b : référence prise à la création, pas de bloc pièce.
        BonEntreeDto cree = bonEntreeService.creer(entreePayload(5));
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(cree.getId())),
                new org.springframework.data.mongodb.core.query.Update().set("reference", "BE-20260617-001")
                        .unset("piece"), BonEntree.class);

        bonEntreeService.soumettre(cree.getId());
        BonEntreeDto valide = bonEntreeService.valider(cree.getId(), null);

        assertThat(valide.getReference()).isEqualTo("BE-20260617-001");
        assertThat(mongoTemplate.count(new Query(), CompteurPiece.class)).isZero();
        assertThat(mouvementRepository.findByProduitIdOrderByDateDesc(produitId).get(0).getBonReference())
                .isEqualTo("BE-20260617-001");
    }

    private long evenements(String type) {
        return mongoTemplate.count(Query.query(Criteria.where("type").is(type)), EvenementDomaine.class);
    }
}
