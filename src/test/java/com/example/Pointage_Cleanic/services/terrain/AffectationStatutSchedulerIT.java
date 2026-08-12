package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bascule automatique des statuts d'affectation.
 * <p>
 * Test d'intégration (et non unitaire à repository mocké) : depuis le passage aux
 * mises à jour ensemblistes, la règle métier <b>vit dans la requête Mongo</b>
 * ({@code statut = source} + condition de date). Seul un vrai Mongo prouve que les
 * bons documents sont sélectionnés.
 * <p>
 * Les fixtures sont datées <b>relativement à {@code now}</b> plutôt qu'avec une
 * horloge figée : le bean {@code Clock} est un {@code Clock.system}, et des bornes à
 * ±1 min / ±1 h sont hors de portée du temps d'exécution du test.
 * <p>
 * Le {@code @Scheduled} du job reste actif pendant ces tests ; c'est sans effet, une
 * exécution surnuméraire étant exactement ce que l'idempotence garantit inoffensif.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class AffectationStatutSchedulerIT extends MongoTestContainer {

    @Autowired private AffectationStatutScheduler scheduler;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), AffectationAgent.class);
    }

    // --- Les deux transitions nominales ---------------------------------------

    @Test
    void planifiee_dont_le_debut_vient_d_etre_franchi_passe_en_cours() {
        LocalDateTime now = LocalDateTime.now();
        String id = creer(StatutAffectation.PLANIFIEE, now.minusMinutes(1), now.plusHours(4));

        scheduler.rafraichirStatuts();

        assertThat(relire(id).getStatut()).isEqualTo(StatutAffectation.EN_COURS);
    }

    @Test
    void en_cours_dont_la_fin_est_franchie_passe_effectuee() {
        LocalDateTime now = LocalDateTime.now();
        String id = creer(StatutAffectation.EN_COURS, now.minusHours(4), now.minusMinutes(1));

        scheduler.rafraichirStatuts();

        assertThat(relire(id).getStatut()).isEqualTo(StatutAffectation.EFFECTUEE);
    }

    /** Le cœur du livrable : l'ordre des deux updates évite de rester bloqué un tour sur EN_COURS. */
    @Test
    void planifiee_entierement_passee_atteint_effectuee_en_une_seule_execution() {
        LocalDateTime now = LocalDateTime.now();
        String id = creer(StatutAffectation.PLANIFIEE, now.minusHours(5), now.minusHours(1));

        scheduler.rafraichirStatuts();

        assertThat(relire(id).getStatut()).isEqualTo(StatutAffectation.EFFECTUEE);
    }

    // --- Ce que le job ne doit jamais toucher ---------------------------------

    @Test
    void statuts_terminaux_au_creneau_passe_restent_inchanges() {
        LocalDateTime now = LocalDateTime.now();
        String annulee = creer(StatutAffectation.ANNULEE, now.minusHours(5), now.minusHours(1));
        String remplacee = creer(StatutAffectation.REMPLACEE, now.minusHours(5), now.minusHours(1));
        String effectuee = creer(StatutAffectation.EFFECTUEE, now.minusHours(5), now.minusHours(1));

        LocalDateTime updatedAtAvant = relire(annulee).getUpdatedAt();

        scheduler.rafraichirStatuts();

        assertThat(relire(annulee).getStatut()).isEqualTo(StatutAffectation.ANNULEE);
        assertThat(relire(remplacee).getStatut()).isEqualTo(StatutAffectation.REMPLACEE);
        assertThat(relire(effectuee).getStatut()).isEqualTo(StatutAffectation.EFFECTUEE);
        // Non seulement le statut, mais le document entier n'a pas été réécrit.
        assertThat(relire(annulee).getUpdatedAt()).isEqualTo(updatedAtAvant);
    }

    @Test
    void affectation_future_reste_planifiee() {
        LocalDateTime now = LocalDateTime.now();
        String id = creer(StatutAffectation.PLANIFIEE, now.plusHours(1), now.plusHours(3));

        scheduler.rafraichirStatuts();

        assertThat(relire(id).getStatut()).isEqualTo(StatutAffectation.PLANIFIEE);
    }

    /** La mise à jour est un $set ciblé, pas une réécriture du document (cf. lost update du saveAll). */
    @Test
    void la_bascule_ne_touche_que_statut_et_updatedAt() {
        // Tronqué à la milliseconde : Mongo stocke les dates en BSON date (précision ms),
        // les nanos de now() ne survivraient pas à l'aller-retour.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        AffectationAgent aff = AffectationAgent.builder()
                .statut(StatutAffectation.EN_COURS)
                .dateDebut(now.minusHours(4))
                .dateFin(now.minusMinutes(1))
                .employeId("emp-1").employeNom("Awa Diop").employeMatricule("M-001")
                .siteId("site-1").siteNom("Siège").siteCode("S-01")
                .commentaire("Prestation renforcée")
                .createdAt(now.minusDays(1))
                .build();
        String id = mongoTemplate.save(aff).getId();

        scheduler.rafraichirStatuts();

        AffectationAgent apres = relire(id);
        assertThat(apres.getStatut()).isEqualTo(StatutAffectation.EFFECTUEE);
        assertThat(apres.getCommentaire()).isEqualTo("Prestation renforcée");
        assertThat(apres.getEmployeNom()).isEqualTo("Awa Diop");
        assertThat(apres.getSiteNom()).isEqualTo("Siège");
        assertThat(apres.getCreatedAt()).isEqualTo(now.minusDays(1));
    }

    // --- Idempotence et concurrence -------------------------------------------

    @Test
    void seconde_execution_consecutive_n_affecte_aucune_ligne() {
        LocalDateTime now = LocalDateTime.now();
        String id = creer(StatutAffectation.PLANIFIEE, now.minusHours(5), now.minusHours(1));

        scheduler.rafraichirStatuts();
        LocalDateTime updatedAtApresPremiere = relire(id).getUpdatedAt();
        assertThat(updatedAtApresPremiere).isNotNull();

        scheduler.rafraichirStatuts();

        // updatedAt figé ⇒ la seconde passe n'a matché aucun document.
        assertThat(relire(id).getUpdatedAt()).isEqualTo(updatedAtApresPremiere);
        assertThat(relire(id).getStatut()).isEqualTo(StatutAffectation.EFFECTUEE);
    }

    /** Deux instances simulées : les updates conditionnés sur le statut source excluent le double traitement. */
    @Test
    void deux_executions_concurrentes_ne_produisent_aucun_double_traitement() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 30; i++) {
            creer(StatutAffectation.PLANIFIEE, now.minusHours(5), now.minusHours(1));
        }
        for (int i = 0; i < 10; i++) {
            creer(StatutAffectation.ANNULEE, now.minusHours(5), now.minusHours(1));
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch topDepart = new CountDownLatch(1);
        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    topDepart.await();
                    scheduler.rafraichirStatuts();
                    return null;
                });
            }
            topDepart.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        List<AffectationAgent> toutes = mongoTemplate.findAll(AffectationAgent.class);
        assertThat(toutes).hasSize(40);
        assertThat(toutes).filteredOn(a -> a.getStatut() == StatutAffectation.EFFECTUEE).hasSize(30);
        assertThat(toutes).filteredOn(a -> a.getStatut() == StatutAffectation.ANNULEE).hasSize(10);
        // Aucune affectation restée coincée en position intermédiaire.
        assertThat(toutes).noneMatch(a -> a.getStatut() == StatutAffectation.EN_COURS
                || a.getStatut() == StatutAffectation.PLANIFIEE);
    }

    // --- Helpers ---------------------------------------------------------------

    private String creer(StatutAffectation statut, LocalDateTime debut, LocalDateTime fin) {
        return mongoTemplate.save(AffectationAgent.builder()
                .statut(statut)
                .dateDebut(debut)
                .dateFin(fin)
                .employeId("emp-1")
                .siteId("site-1")
                .createdAt(debut)
                .updatedAt(debut)
                .build()).getId();
    }

    private AffectationAgent relire(String id) {
        return mongoTemplate.findById(id, AffectationAgent.class);
    }
}
