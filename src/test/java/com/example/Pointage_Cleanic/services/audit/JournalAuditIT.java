package com.example.Pointage_Cleanic.services.audit;

import com.example.Pointage_Cleanic.Enum.audit.ActionAudit;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.configurations.CorrelationId;
import com.example.Pointage_Cleanic.configurations.ExecuteurTransactionnel;
import com.example.Pointage_Cleanic.entities.audit.EntreeAudit;
import com.example.Pointage_Cleanic.entities.audit.EntreeAudit.Changement;
import com.example.Pointage_Cleanic.security.Sensible;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Critère 1b « JournalAuditIT » (décision D6) : la ligne d'audit est écrite avec la transaction
 * et annulée avec elle ; elle porte le différentiel, l'auteur, l'IP et la corrélation ; les champs
 * {@link Sensible} sont masqués. Les transitions des pièces portées (bons, inventaires) sont
 * vérifiées dans leurs propres tests.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class JournalAuditIT extends MongoTestContainer {

    private static final String CORRELATION = "6f1c2a52-7d1e-4f5b-9a77-3a3b2c1d0e9f";

    @Autowired private JournalAudit journal;
    @Autowired private ExecuteurTransactionnel executeur;
    @Autowired private MongoTemplate mongoTemplate;

    /** Objet de test : un salaire et un RIB sensibles, imbriqués compris (champs publics : lus par Jackson). */
    public static class Fiche {
        public String nom;
        @Sensible public Long salaire;
        public Banque banque;
        public Integer version;

        Fiche(String nom, Long salaire, Banque banque, Integer version) {
            this.nom = nom;
            this.salaire = salaire;
            this.banque = banque;
            this.version = version;
        }
    }

    public static class Banque {
        public String nom;
        @Sensible public String rib;

        Banque(String nom, String rib) {
            this.nom = nom;
            this.rib = rib;
        }
    }

    @BeforeEach
    void preparer() {
        mongoTemplate.remove(new Query(), EntreeAudit.class);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("auditeur.test@cleanic.sn", null, List.of()));
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.setRemoteAddr("10.1.2.3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requete));
        MDC.put(CorrelationId.CLE, CORRELATION);
    }

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        MDC.remove(CorrelationId.CLE);
    }

    @Test
    void ligne_ecrite_avec_la_transaction_et_annulee_avec_elle() {
        assertThatThrownBy(() -> executeur.executer(() -> {
            journal.enregistrer(ActionAudit.VALIDATION, "Test", "obj-1", "X-2026-0001", null, null, null);
            throw new IllegalStateException("panne après l'audit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(mongoTemplate.count(new Query(), EntreeAudit.class)).isZero();

        executeur.executer(() ->
                journal.enregistrer(ActionAudit.VALIDATION, "Test", "obj-1", "X-2026-0001", null, null, null));
        assertThat(mongoTemplate.count(new Query(), EntreeAudit.class)).isEqualTo(1);
    }

    @Test
    void audit_hors_transaction_refuse() {
        assertThatThrownBy(() -> journal.enregistrer(ActionAudit.CREATION, "Test", "x", null, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hors transaction");
    }

    @Test
    void differentiel_auteur_ip_correlation_et_champs_sensibles_masques() {
        Fiche avant = new Fiche("Diop", 250_000L, new Banque("CBAO", "SN012-0001"), 3);
        Fiche apres = new Fiche("Diop Fall", 300_000L, new Banque("CBAO", "SN012-9999"), 4);

        executeur.executer(() ->
                journal.enregistrer(ActionAudit.MODIFICATION, "Fiche", "fiche-1", null, avant, apres, "correction"));

        EntreeAudit e = mongoTemplate.findOne(new Query(), EntreeAudit.class);
        assertThat(e).isNotNull();
        assertThat(e.getAction()).isEqualTo(ActionAudit.MODIFICATION);
        assertThat(e.getObjetType()).isEqualTo("Fiche");
        assertThat(e.getObjetId()).isEqualTo("fiche-1");
        assertThat(e.getMotif()).isEqualTo("correction");
        assertThat(e.getCompteId()).isEqualTo("auditeur.test@cleanic.sn");
        assertThat(e.getNomInstantane()).isEqualTo("auditeur.test@cleanic.sn");
        assertThat(e.getIp()).isEqualTo("10.1.2.3");
        assertThat(e.getCorrelationId()).isEqualTo(CORRELATION);
        assertThat(e.getLe()).isNotNull();

        assertThat(e.getChangements()).extracting(Changement::getChamp)
                .containsExactly("banque.rib", "nom", "salaire"); // version ignorée, banque.nom inchangé
        assertThat(e.getChangements()).filteredOn(c -> c.getChamp().equals("nom"))
                .singleElement().satisfies(c -> {
                    assertThat(c.getAvant()).isEqualTo("Diop");
                    assertThat(c.getApres()).isEqualTo("Diop Fall");
                });
        assertThat(e.getChangements()).filteredOn(c -> Set.of("salaire", "banque.rib").contains(c.getChamp()))
                .allSatisfy(c -> {
                    assertThat(c.getAvant()).isEqualTo(JournalAudit.MASQUE);
                    assertThat(c.getApres()).isEqualTo(JournalAudit.MASQUE);
                });
        // Aucune valeur sensible n'est écrite, nulle part dans la ligne.
        String brut = mongoTemplate.getCollection(JournalAudit.COLLECTION).find().first().toJson();
        assertThat(brut).doesNotContain("250000", "300000", "SN012-0001", "SN012-9999");
    }

    @Test
    void creation_trace_tous_les_champs_renseignes() {
        Fiche cree = new Fiche("Ndiaye", 100_000L, null, 0);

        executeur.executer(() ->
                journal.enregistrer(ActionAudit.CREATION, "Fiche", "fiche-2", null, null, cree, null));

        EntreeAudit e = mongoTemplate.findOne(new Query(), EntreeAudit.class);
        assertThat(e.getChangements()).extracting(Changement::getChamp).containsExactly("nom", "salaire");
        assertThat(e.getChangements()).allSatisfy(c -> assertThat(c.getAvant()).isNull());
        assertThat(e.getChangements()).filteredOn(c -> c.getChamp().equals("salaire"))
                .singleElement().satisfies(c -> assertThat(c.getApres()).isEqualTo(JournalAudit.MASQUE));
    }
}
