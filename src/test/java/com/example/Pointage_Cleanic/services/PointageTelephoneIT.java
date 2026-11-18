package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.exception.PointageTropFrequentException;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Garde anti-triche du kiosque ({@link PointageServices#verifierTelephone}) contre une vraie
 * base : le téléphone n'est refusé qu'à un AUTRE agent, à l'arrivée comme à la sortie.
 *
 * <p>Les pointages d'amorce sont datés relativement à {@code Instant.now()} (le service lit
 * l'horloge système) : à 10 min dans le délai, à 30 min hors délai, à 2 h hors anti-rebond.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class PointageTelephoneIT extends MongoTestContainer {

    private static final String AGENT_A = "1111";
    private static final String AGENT_B = "2222";
    private static final String TEL_A = "TEL-A";

    @Autowired private PointageServices service;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private PointageRepository pointageRepository;
    @Autowired private DossierEmployeRepository dossierEmployeRepository;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), Pointage.class);
        mongoTemplate.remove(new Query(), DossierEmploye.class);
        agent(AGENT_A);
        agent(AGENT_B);
    }

    // ── Le cas signalé : agent multi-sites, sur son propre téléphone ────────────

    @Test
    void agent_multi_sites_peut_arriver_sur_son_2e_site_peu_apres_sa_sortie() {
        // Arrivée site 1 il y a 3 h, sortie il y a 10 min, sur SON téléphone.
        pointageClos(AGENT_A, TEL_A, ago(Duration.ofHours(3)), TEL_A, ago(Duration.ofMinutes(10)));

        Pointage arrivee = service.enregistrerPointage(AGENT_A, TEL_A, null, null);

        assertThat(arrivee.getStatus()).isEqualTo("EN COURS...");
        assertThat(pointageRepository.count()).isEqualTo(2);
    }

    @Test
    void agent_peut_sortir_d_un_passage_court_sur_son_telephone() {
        pointageOuvert(AGENT_A, TEL_A, ago(Duration.ofMinutes(5)));

        Pointage sortie = service.enregistrerPointage(AGENT_A, TEL_A, null, null);

        assertThat(sortie.getStatus()).isEqualTo("TERMINÉ");
        Pointage relu = pointageRepository.findAll().get(0);
        assertThat(relu.getDeviceIdSortie()).isEqualTo(TEL_A);
        assertThat(relu.getTimestampSortie()).isNotNull();
    }

    // ── Téléphone d'un autre agent ──────────────────────────────────────────────

    @Test
    void arrivee_d_un_collegue_sur_le_telephone_refusee_dans_le_delai() {
        pointageOuvert(AGENT_A, TEL_A, ago(Duration.ofMinutes(10)));

        assertThatThrownBy(() -> service.enregistrerPointage(AGENT_B, TEL_A, null, null))
                .isInstanceOf(PointageTropFrequentException.class)
                .hasMessage(PointageServices.MESSAGE_TELEPHONE_PARTAGE);
        assertThat(pointageRepository.count()).isEqualTo(1);
    }

    @Test
    void sortie_recente_sur_le_telephone_bloque_aussi_un_collegue() {
        // Arrivée de A ancienne (hors délai), mais SORTIE il y a 10 min sur TEL-A :
        // l'ancienne règle, qui ne regardait que l'arrivée, l'aurait laissé passer.
        pointageClos(AGENT_A, TEL_A, ago(Duration.ofHours(4)), TEL_A, ago(Duration.ofMinutes(10)));

        assertThatThrownBy(() -> service.enregistrerPointage(AGENT_B, TEL_A, null, null))
                .isInstanceOf(PointageTropFrequentException.class);
    }

    @Test
    void collegue_ne_peut_pas_pointer_sa_sortie_sur_le_telephone_d_un_autre() {
        pointageOuvert(AGENT_A, TEL_A, ago(Duration.ofMinutes(10)));
        pointageOuvert(AGENT_B, "TEL-B", ago(Duration.ofHours(3)));

        assertThatThrownBy(() -> service.enregistrerPointage(AGENT_B, TEL_A, null, null))
                .isInstanceOf(PointageTropFrequentException.class);
        // Le pointage ouvert de B n'a PAS été clôturé : refus avant toute écriture.
        Pointage pointageB = pointageRepository.findAll().stream()
                .filter(p -> AGENT_B.equals(p.getCodeSecret())).findFirst().orElseThrow();
        assertThat(pointageB.getHeureDepart()).isNull();
    }

    @Test
    void collegue_accepte_une_fois_le_delai_ecoule() {
        pointageClos(AGENT_A, TEL_A, ago(Duration.ofHours(4)), TEL_A, ago(Duration.ofMinutes(30)));

        Pointage arrivee = service.enregistrerPointage(AGENT_B, TEL_A, null, null);

        assertThat(arrivee.getCodeSecret()).isEqualTo(AGENT_B);
    }

    @Test
    void pointage_anterieur_sans_telephone_de_sortie_ne_compte_que_par_son_arrivee() {
        // Document antérieur au lot : clos, mais sans deviceIdSortie / timestampSortie.
        Pointage ancien = Pointage.builder()
                .codeSecret(AGENT_A).date(LocalDate.now())
                .heureArrive("06:00").heureDepart("08:00").status("TERMINÉ")
                .deviceId(TEL_A).timestamp(ago(Duration.ofHours(3)))
                .build();
        sauver(ancien);

        Pointage arrivee = service.enregistrerPointage(AGENT_B, TEL_A, null, null);

        assertThat(arrivee.getCodeSecret()).isEqualTo(AGENT_B);
    }

    // ── Anti-rebond ─────────────────────────────────────────────────────────────

    @Test
    void double_envoi_du_meme_agent_refuse() {
        pointageOuvert(AGENT_A, TEL_A, ago(Duration.ofSeconds(10)));

        assertThatThrownBy(() -> service.enregistrerPointage(AGENT_A, TEL_A, null, null))
                .isInstanceOf(PointageTropFrequentException.class)
                .hasMessage(PointageServices.MESSAGE_DOUBLE_ENVOI);
        assertThat(pointageRepository.findAll().get(0).getHeureDepart()).isNull();
    }

    // ── Amorces ─────────────────────────────────────────────────────────────────

    private void agent(String agentId) {
        DossierEmploye d = new DossierEmploye();
        d.setAgentId(agentId);
        d.setPrenom("Prenom" + agentId);
        d.setNom("Nom" + agentId);
        d.setSiteAffecte("Site 1 / Site 2");
        dossierEmployeRepository.save(d);
    }

    private void pointageOuvert(String agent, String device, Instant arrivee) {
        sauver(Pointage.builder()
                .codeSecret(agent).date(LocalDate.now())
                .heureArrive("06:00").status("EN COURS...")
                .deviceId(device).timestamp(arrivee)
                .build());
    }

    private void pointageClos(String agent, String device, Instant arrivee,
                              String deviceSortie, Instant sortie) {
        sauver(Pointage.builder()
                .codeSecret(agent).date(LocalDate.now())
                .heureArrive("06:00").heureDepart("08:00").status("TERMINÉ")
                .deviceId(device).timestamp(arrivee)
                .deviceIdSortie(deviceSortie).timestampSortie(sortie)
                .build());
    }

    /**
     * ⚠ {@code timestamp} est un {@code @CreatedDate} : l'auditing Mongo l'écrase par « maintenant »
     * à la création. On le repose après coup, sinon toute amorce serait « récente ».
     */
    private void sauver(Pointage p) {
        Instant arrivee = p.getTimestamp();
        String id = pointageRepository.save(p).getId();
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id)),
                Update.update("timestamp", arrivee), Pointage.class);
    }

    private static Instant ago(Duration d) {
        return Instant.now().minus(d);
    }
}
