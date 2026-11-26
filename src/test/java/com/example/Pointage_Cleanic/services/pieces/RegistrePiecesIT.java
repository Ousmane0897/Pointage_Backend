package com.example.Pointage_Cleanic.services.pieces;

import com.example.Pointage_Cleanic.Enum.audit.ActionAudit;
import com.example.Pointage_Cleanic.Enum.pieces.StatutPiece;
import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.configurations.ExecuteurTransactionnel;
import com.example.Pointage_Cleanic.entities.audit.EntreeAudit;
import com.example.Pointage_Cleanic.entities.evenements.EvenementConsomme;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.entities.pieces.Annulation;
import com.example.Pointage_Cleanic.entities.pieces.LignePieceRegistre;
import com.example.Pointage_Cleanic.entities.pieces.Piece;
import com.example.Pointage_Cleanic.entities.pieces.PieceBase;
import com.example.Pointage_Cleanic.services.evenements.DispatcherEvenements;
import com.example.Pointage_Cleanic.services.evenements.TypesEvenement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Registre des pièces (lot 1 / 1b, D4) alimenté par les événements : création, validation et
 * annulation reflétées ; rejeu sans doublon ; un événement en retard n'écrase pas un état plus
 * récent. Utilise une pièce de test : les pièces réelles (bons, inventaires) ont leurs tests.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class RegistrePiecesIT extends MongoTestContainer {

    @Autowired private TraceurPieces traceur;
    @Autowired private NumerotationService numerotation;
    @Autowired private DispatcherEvenements dispatcher;
    @Autowired private ExecuteurTransactionnel executeur;
    @Autowired private MongoTemplate mongoTemplate;

    /** Pièce de test minimale (champs publics : lus par Jackson pour l'audit). */
    public static class PieceTest implements Piece {
        public String id;
        public PieceBase piece;
        public String statut = "BROUILLON";
        public long montant;

        PieceTest(String id, long montant) {
            this.id = id;
            this.montant = montant;
            this.piece = PieceBase.builder().type(TypePiece.DEV).creeLe(LocalDateTime.of(2026, 11, 25, 9, 0)).build();
        }

        @Override public String getId() { return id; }
        @Override public PieceBase getPiece() { return piece; }
        @Override public StatutPiece statutPiece() { return StatutPiece.valueOf(statut); }
        @Override public String siteIdPiece() { return "site-1"; }
        @Override public long montantPiece() { return montant; }
    }

    @BeforeEach
    void nettoyer() {
        mongoTemplate.remove(new Query(), EvenementDomaine.class);
        mongoTemplate.remove(new Query(), EvenementConsomme.class);
        mongoTemplate.remove(new Query(), LignePieceRegistre.class);
        mongoTemplate.remove(new Query(), EntreeAudit.class);
        mongoTemplate.remove(new Query(), com.example.Pointage_Cleanic.entities.pieces.CompteurPiece.class);
    }

    @Test
    void creation_validation_annulation_refletees_et_rejeu_sans_doublon() {
        PieceTest p = new PieceTest("piece-000123", 15_000);

        executeur.executer(() -> traceur.tracer(TypesEvenement.PIECE_CREEE, ActionAudit.CREATION,
                null, p, p.statut, null, null));
        dispatcher.traiterLot();
        LignePieceRegistre cree = ligne(p);
        assertThat(cree.getStatut()).isEqualTo(StatutPiece.BROUILLON);
        assertThat(cree.getNumero()).isNull();
        assertThat(cree.getMontant()).isEqualTo(15_000);
        assertThat(cree.getSiteId()).isEqualTo("site-1");
        assertThat(cree.getCreeLe()).isEqualTo(LocalDateTime.of(2026, 11, 25, 9, 0));

        executeur.executer(() -> {
            numerotation.numeroter(p.piece);
            p.statut = "VALIDEE";
            p.piece.setValideeLe(LocalDateTime.of(2026, 11, 25, 10, 0));
            traceur.tracer(TypesEvenement.PIECE_VALIDEE, ActionAudit.VALIDATION, null, p, "EFFECTIF", null, null);
        });
        dispatcher.traiterLot();
        LignePieceRegistre validee = ligne(p);
        assertThat(validee.getStatut()).isEqualTo(StatutPiece.VALIDEE);
        assertThat(validee.getNumero()).matches("DEV-\\d{4}-0001");
        assertThat(validee.getCompteur()).isEqualTo(1L);
        assertThat(validee.getValideeLe()).isEqualTo(LocalDateTime.of(2026, 11, 25, 10, 0));

        executeur.executer(() -> {
            p.statut = "ANNULEE";
            p.piece.setAnnulation(Annulation.builder().motif("erreur de saisie du fournisseur").build());
            traceur.tracer(TypesEvenement.PIECE_ANNULEE, ActionAudit.ANNULATION, null, p, "ANNULE",
                    "erreur de saisie du fournisseur", null);
        });
        dispatcher.traiterLot();
        assertThat(ligne(p).getStatut()).isEqualTo(StatutPiece.ANNULEE);

        // Rejeu de tous les événements : toujours une seule ligne, dans l'état final.
        mongoTemplate.updateMulti(new Query(), new Update().set("statut", "A_TRAITER")
                .set("prochainEssai", java.time.Instant.EPOCH), EvenementDomaine.class);
        mongoTemplate.remove(new Query(), EvenementConsomme.class);
        dispatcher.traiterLot();
        assertThat(mongoTemplate.count(new Query(), LignePieceRegistre.class)).isEqualTo(1);
        assertThat(ligne(p).getStatut()).isEqualTo(StatutPiece.ANNULEE);

        // Audit : une ligne par transition, numéro affiché compris.
        List<EntreeAudit> audit = mongoTemplate.find(new Query(), EntreeAudit.class);
        assertThat(audit).extracting(EntreeAudit::getAction)
                .containsExactlyInAnyOrder(ActionAudit.CREATION, ActionAudit.VALIDATION, ActionAudit.ANNULATION);
        assertThat(audit).filteredOn(a -> a.getAction() == ActionAudit.CREATION)
                .singleElement().satisfies(a -> assertThat(a.getNumero()).isEqualTo("BROUILLON-000123"));
    }

    @Test
    void evenement_en_retard_n_ecrase_pas_un_etat_plus_recent() {
        PieceTest p = new PieceTest("piece-000456", 0);
        executeur.executer(() -> traceur.tracer(TypesEvenement.PIECE_CREEE, ActionAudit.CREATION,
                null, p, p.statut, null, null));
        executeur.executer(() -> {
            p.statut = "SOUMISE";
            traceur.tracer(TypesEvenement.PIECE_SOUMISE, ActionAudit.SOUMISSION, null, p, "SOUMIS", null, null);
        });
        // La création arrive après la soumission (nouvel essai en retard) : on la vieillit et on
        // traite d'abord la soumission seule.
        EvenementDomaine creation = mongoTemplate.findOne(Query.query(org.springframework.data.mongodb.core.query.Criteria
                .where("type").is(TypesEvenement.PIECE_CREEE)), EvenementDomaine.class);
        mongoTemplate.updateFirst(Query.query(org.springframework.data.mongodb.core.query.Criteria
                        .where("_id").is(creation.getId())),
                new Update().set("statut", "EN_COURS").set("bailJusquA", java.time.Instant.now().plusSeconds(600))
                        .set("survenuLe", java.time.Instant.now().minusSeconds(60)), EvenementDomaine.class);
        dispatcher.traiterLot();
        assertThat(ligne(p).getStatut()).isEqualTo(StatutPiece.SOUMISE);

        mongoTemplate.updateFirst(Query.query(org.springframework.data.mongodb.core.query.Criteria
                        .where("_id").is(creation.getId())),
                new Update().set("statut", "A_TRAITER").set("prochainEssai", java.time.Instant.EPOCH),
                EvenementDomaine.class);
        dispatcher.traiterLot();
        assertThat(ligne(p).getStatut()).isEqualTo(StatutPiece.SOUMISE);
    }

    private LignePieceRegistre ligne(PieceTest p) {
        return mongoTemplate.findOne(Query.query(org.springframework.data.mongodb.core.query.Criteria
                .where("type").is(TypePiece.DEV).and("pieceId").is(p.id)), LignePieceRegistre.class);
    }
}
