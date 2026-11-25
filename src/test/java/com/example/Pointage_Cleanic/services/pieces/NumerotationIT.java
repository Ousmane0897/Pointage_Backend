package com.example.Pointage_Cleanic.services.pieces;

import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.configurations.ExecuteurTransactionnel;
import com.example.Pointage_Cleanic.entities.pieces.CompteurPiece;
import com.example.Pointage_Cleanic.entities.pieces.PieceBase;
import com.example.Pointage_Cleanic.exception.ConflitConcurrentException;
import com.example.Pointage_Cleanic.services.pieces.NumerotationService.NumeroPiece;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Critère 1b « NumerotationIT » (décision D4) : 100 validations concurrentes donnent 100 numéros
 * consécutifs sans trou ; une validation en échec ne consomme pas de numéro ; changement
 * d'exercice au 1er janvier (heure de Dakar), la série repart à 0001 ; passage de 9999 à 10000
 * sans erreur ni troncature.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class NumerotationIT extends MongoTestContainer {

    private static final String PIECES_TEST = "numerotation_it_pieces";

    @Autowired private NumerotationService numerotation;
    @Autowired private ExecuteurTransactionnel executeur;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private Clock clock;

    private int exerciceCourant;

    @BeforeEach
    void nettoyer() {
        mongoTemplate.remove(new Query(), CompteurPiece.class);
        if (!mongoTemplate.collectionExists(PIECES_TEST)) {
            mongoTemplate.createCollection(PIECES_TEST);
        }
        mongoTemplate.remove(new Query(), PIECES_TEST);
        exerciceCourant = NumerotationService.exerciceAu(clock.instant());
    }

    /**
     * Chaque « validation » numérote une pièce et l'enregistre dans la même transaction. Un
     * conflit épuisé (409 CONFLIT_CONCURRENT, rien d'écrit) est relancé, comme le ferait
     * l'utilisateur : à la fin, les 100 validations ont abouti.
     */
    @Test
    void cent_validations_concurrentes_donnent_cent_numeros_consecutifs() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<CompletableFuture<NumeroPiece>> taches = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                taches.add(CompletableFuture.supplyAsync(this::validerAvecRelance, pool));
            }
            List<NumeroPiece> numeros = taches.stream().map(CompletableFuture::join).toList();

            assertThat(numeros).extracting(NumeroPiece::compteur)
                    .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, 100).boxed().toList());
            assertThat(numeros).extracting(NumeroPiece::numero).doesNotHaveDuplicates();
            assertThat(mongoTemplate.count(new Query(), PIECES_TEST)).isEqualTo(100);
            assertThat(compteur(TypePiece.DEV, exerciceCourant)).isEqualTo(100);
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private NumeroPiece validerAvecRelance() {
        while (true) {
            try {
                return executeur.executer(() -> {
                    NumeroPiece n = numerotation.attribuer(TypePiece.DEV);
                    mongoTemplate.insert(new Document("numero", n.numero()), PIECES_TEST);
                    return n;
                });
            } catch (ConflitConcurrentException relancer) {
                // Rien n'a été écrit : on recommence.
            }
        }
    }

    @Test
    void une_validation_en_echec_ne_consomme_pas_de_numero() {
        assertThatThrownBy(() -> executeur.executer(() -> {
            numerotation.attribuer(TypePiece.BE);
            throw new IllegalStateException("panne après l'attribution");
        })).isInstanceOf(IllegalStateException.class);

        String numero = executeur.executer(() -> numerotation.attribuer(TypePiece.BE).numero());
        assertThat(numero).isEqualTo("BE-" + exerciceCourant + "-0001");
    }

    @Test
    void format_par_type_et_numero_existant_conserve() {
        String be = executeur.executer(() -> numerotation.attribuer(TypePiece.BE).numero());
        String bs = executeur.executer(() -> numerotation.attribuer(TypePiece.BS).numero());
        String be2 = executeur.executer(() -> numerotation.attribuer(TypePiece.BE).numero());
        assertThat(be).isEqualTo("BE-" + exerciceCourant + "-0001");
        assertThat(bs).isEqualTo("BS-" + exerciceCourant + "-0001");
        assertThat(be2).isEqualTo("BE-" + exerciceCourant + "-0002");

        // Pièce antérieure à 1b : sa référence d'origine est gardée, aucun numéro consommé.
        PieceBase ancienne = PieceBase.builder().type(TypePiece.BE).numero("BE-20260617-001").build();
        assertThat(executeur.executer(() -> numerotation.numeroter(ancienne))).isEqualTo("BE-20260617-001");
        assertThat(ancienne.getExercice()).isNull();

        PieceBase neuve = PieceBase.builder().type(TypePiece.BE).build();
        executeur.executer(() -> numerotation.numeroter(neuve));
        assertThat(neuve.getNumero()).isEqualTo("BE-" + exerciceCourant + "-0003");
        assertThat(neuve.getExercice()).isEqualTo(exerciceCourant);
        assertThat(neuve.getCompteur()).isEqualTo(3L);
    }

    @Test
    void changement_d_exercice_au_premier_janvier_heure_de_dakar() {
        NumerotationService au31decembre = avecHorloge("2026-12-31T23:59:59Z");
        NumerotationService au1erJanvier = avecHorloge("2027-01-01T00:00:00Z");

        String dernier2026a = executeur.executer(() -> au31decembre.attribuer(TypePiece.BS).numero());
        String dernier2026b = executeur.executer(() -> au31decembre.attribuer(TypePiece.BS).numero());
        String premier2027 = executeur.executer(() -> au1erJanvier.attribuer(TypePiece.BS).numero());

        assertThat(dernier2026a).isEqualTo("BS-2026-0001");
        assertThat(dernier2026b).isEqualTo("BS-2026-0002");
        assertThat(premier2027).isEqualTo("BS-2027-0001");
        // Dakar est en UTC+0 toute l'année : le changement tombe à minuit UTC.
        assertThat(NumerotationService.exerciceAu(Instant.parse("2026-12-31T23:59:59.999Z"))).isEqualTo(2026);
    }

    @Test
    void passage_de_9999_a_10000_sans_troncature() {
        mongoTemplate.save(new CompteurPiece(NumerotationService.cle(TypePiece.BS, exerciceCourant), 9998L));

        String n9999 = executeur.executer(() -> numerotation.attribuer(TypePiece.BS).numero());
        String n10000 = executeur.executer(() -> numerotation.attribuer(TypePiece.BS).numero());

        assertThat(n9999).isEqualTo("BS-" + exerciceCourant + "-9999");
        assertThat(n10000).isEqualTo("BS-" + exerciceCourant + "-10000");
        assertThat(NumerotationService.formater(TypePiece.BS, 2027, 12345)).isEqualTo("BS-2027-12345");
    }

    @Test
    void attribution_hors_transaction_refusee() {
        assertThatThrownBy(() -> numerotation.attribuer(TypePiece.BE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hors transaction");
        assertThat(mongoTemplate.count(new Query(), CompteurPiece.class)).isZero();
    }

    private NumerotationService avecHorloge(String instant) {
        return new NumerotationService(mongoTemplate, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private long compteur(TypePiece type, int exercice) {
        CompteurPiece c = mongoTemplate.findById(NumerotationService.cle(type, exercice), CompteurPiece.class);
        return c == null ? 0 : c.getCompteur();
    }
}
