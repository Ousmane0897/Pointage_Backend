package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.AffectationAgentDto;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.TerrainConflitException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Annulation motivée d'une affectation (statut ANNULEE + traçabilité) et compteurs par statut.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class PlanningServiceAnnulationIT extends MongoTestContainer {

    @Autowired private PlanningService service;
    @Autowired private MongoTemplate mongoTemplate;

    private static final String EMAIL = "awa.diop@cleanicsenegal.com";
    private static final String NOM_COMPLET = "Awa Diop";
    private static final String MOTIF = "Client a reporté l'intervention";

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), AffectationAgent.class);
        mongoTemplate.remove(new Query(), Utilisateur.class);

        Utilisateur u = new Utilisateur();
        u.setId("u1");
        u.setPrenom("Awa");
        u.setNom("Diop");
        u.setEmail(EMAIL);
        mongoTemplate.save(u);

        authentifier(EMAIL);
    }

    @AfterEach
    void tearDown() {
        // Le SecurityContext est statique : sans nettoyage il fuiterait vers les tests suivants.
        SecurityContextHolder.clearContext();
    }

    private void authentifier(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private AffectationAgent affectation(String id, StatutAffectation statut) {
        return affectation(id, statut, LocalDateTime.of(2026, 7, 20, 8, 0), LocalDateTime.of(2026, 7, 20, 17, 0));
    }

    private AffectationAgent affectation(String id, StatutAffectation statut,
                                         LocalDateTime debut, LocalDateTime fin) {
        return mongoTemplate.save(AffectationAgent.builder()
                .id(id)
                .employeId("emp-1").employeNom("Moussa Fall")
                .siteId("site-1").siteNom("Point E")
                .dateDebut(debut).dateFin(fin)
                .statut(statut)
                .build());
    }

    private AffectationAgent relire(String id) {
        return mongoTemplate.findById(id, AffectationAgent.class);
    }

    // ───────────────────────── Cas nominaux ─────────────────────────

    @Test
    void annuler_une_planifiee_renseigne_statut_et_tracabilite() {
        affectation("a1", StatutAffectation.PLANIFIEE);

        AffectationAgentDto dto = service.annuler("a1", "  " + MOTIF + "  ");

        assertThat(dto.getStatut()).isEqualTo(StatutAffectation.ANNULEE);
        assertThat(dto.getMotifAnnulation()).isEqualTo(MOTIF);          // trimé
        assertThat(dto.getDateAnnulation()).isNotNull();
        assertThat(dto.getAnnuleParNom()).isEqualTo(NOM_COMPLET);       // déduit du JWT, pas du corps

        AffectationAgent enBase = relire("a1");
        assertThat(enBase.getStatut()).isEqualTo(StatutAffectation.ANNULEE);
        assertThat(enBase.getMotifAnnulation()).isEqualTo(MOTIF);
        assertThat(enBase.getDateAnnulation()).isNotNull();
        assertThat(enBase.getAnnuleParNom()).isEqualTo(NOM_COMPLET);
        assertThat(enBase.getUpdatedAt()).isNotNull();
    }

    @Test
    void annuler_une_en_cours_est_autorise() {
        affectation("a1", StatutAffectation.EN_COURS);

        assertThat(service.annuler("a1", MOTIF).getStatut()).isEqualTo(StatutAffectation.ANNULEE);
    }

    @Test
    void motif_de_cinq_caracteres_est_accepte() {
        affectation("a1", StatutAffectation.PLANIFIEE);

        assertThat(service.annuler("a1", "abcde").getMotifAnnulation()).isEqualTo("abcde");
    }

    @Test
    void getById_apres_annulation_expose_les_trois_champs() {
        affectation("a1", StatutAffectation.PLANIFIEE);
        service.annuler("a1", MOTIF);

        AffectationAgentDto relu = service.getById("a1");

        assertThat(relu.getMotifAnnulation()).isEqualTo(MOTIF);
        assertThat(relu.getDateAnnulation()).isNotNull();
        assertThat(relu.getAnnuleParNom()).isEqualTo(NOM_COMPLET);
    }

    // ───────────────────────── Garde de statut (409) ─────────────────────────

    @Test
    void annuler_une_effectuee_leve_un_conflit_sans_modifier_la_base() {
        affectation("a1", StatutAffectation.EFFECTUEE);

        assertThatThrownBy(() -> service.annuler("a1", MOTIF))
                .isInstanceOf(TerrainConflitException.class);

        AffectationAgent enBase = relire("a1");
        assertThat(enBase.getStatut()).isEqualTo(StatutAffectation.EFFECTUEE);
        assertThat(enBase.getMotifAnnulation()).isNull();
        assertThat(enBase.getDateAnnulation()).isNull();
        assertThat(enBase.getAnnuleParNom()).isNull();
    }

    @Test
    void annuler_une_remplacee_leve_un_conflit() {
        affectation("a1", StatutAffectation.REMPLACEE);

        assertThatThrownBy(() -> service.annuler("a1", MOTIF))
                .isInstanceOf(TerrainConflitException.class);
    }

    @Test
    void rejouer_l_annulation_retombe_sur_la_garde_de_statut() {
        affectation("a1", StatutAffectation.PLANIFIEE);
        service.annuler("a1", MOTIF);

        assertThatThrownBy(() -> service.annuler("a1", "Autre motif de test"))
                .isInstanceOf(TerrainConflitException.class);

        // Le premier motif n'a pas été écrasé.
        assertThat(relire("a1").getMotifAnnulation()).isEqualTo(MOTIF);
    }

    // ───────────────────────── Validation du motif (400) ─────────────────────────

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "abc", "  abc  ", "1234"})
    void motif_absent_ou_trop_court_est_refuse_sans_modifier_la_base(String motif) {
        affectation("a1", StatutAffectation.PLANIFIEE);

        assertThatThrownBy(() -> service.annuler("a1", motif))
                .isInstanceOf(IllegalArgumentException.class);

        AffectationAgent enBase = relire("a1");
        assertThat(enBase.getStatut()).isEqualTo(StatutAffectation.PLANIFIEE);
        assertThat(enBase.getMotifAnnulation()).isNull();
    }

    // ───────────────────────── Introuvable (404) ─────────────────────────

    @Test
    void annuler_un_id_inconnu_leve_resource_not_found() {
        assertThatThrownBy(() -> service.annuler("inconnu", MOTIF))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ───────────────────────── Traçabilité sans authentification ─────────────────────────

    @Test
    void sans_authentification_annuleParNom_reste_null() {
        SecurityContextHolder.clearContext();
        affectation("a1", StatutAffectation.PLANIFIEE);

        assertThat(service.annuler("a1", MOTIF).getAnnuleParNom()).isNull();
    }

    // ───────────────────────── Compteurs par statut ─────────────────────────

    @Test
    void stats_renvoie_les_cinq_cles_meme_a_zero() {
        affectation("a1", StatutAffectation.PLANIFIEE);
        affectation("a2", StatutAffectation.PLANIFIEE);
        affectation("a3", StatutAffectation.EN_COURS);
        affectation("a4", StatutAffectation.EFFECTUEE);
        service.annuler("a1", MOTIF);   // a1 : PLANIFIEE → ANNULEE

        Map<StatutAffectation, Long> stats = service.stats(null, null);

        assertThat(stats).containsExactly(
                Map.entry(StatutAffectation.PLANIFIEE, 1L),
                Map.entry(StatutAffectation.EN_COURS, 1L),
                Map.entry(StatutAffectation.EFFECTUEE, 1L),
                Map.entry(StatutAffectation.ANNULEE, 1L),
                Map.entry(StatutAffectation.REMPLACEE, 0L));
    }

    @Test
    void stats_applique_le_filtre_de_periode_comme_list() {
        affectation("a1", StatutAffectation.PLANIFIEE,
                LocalDateTime.of(2026, 7, 20, 8, 0), LocalDateTime.of(2026, 7, 20, 17, 0));
        affectation("a2", StatutAffectation.PLANIFIEE,
                LocalDateTime.of(2026, 8, 15, 8, 0), LocalDateTime.of(2026, 8, 15, 17, 0));

        Map<StatutAffectation, Long> juillet =
                service.stats(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(juillet).containsEntry(StatutAffectation.PLANIFIEE, 1L);
        assertThat(juillet).hasSize(StatutAffectation.values().length);
    }
}
