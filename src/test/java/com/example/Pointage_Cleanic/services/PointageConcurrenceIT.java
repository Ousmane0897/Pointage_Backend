package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que la clôture du pointage ouvert est ATOMIQUE : N appels concurrents
 * sur le même (codeSecret, jour) ne clôturent qu'une seule fois le pointage
 * d'origine (pas de double-clôture / TOCTOU). Pattern concurrence calqué sur
 * {@code CompteurLotServiceIT}.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class PointageConcurrenceIT extends MongoTestContainer {

    @Autowired
    private PointageServices service;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PointageRepository pointageRepository;

    @Autowired
    private DossierEmployeRepository dossierEmployeRepository;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), Pointage.class);
        mongoTemplate.remove(new Query(), DossierEmploye.class);
    }

    @Test
    void cloture_concurrente_ne_clot_quune_seule_fois_le_pointage_ouvert() throws Exception {
        // Agent référentiel
        DossierEmploye agent = new DossierEmploye();
        agent.setAgentId("1234");
        agent.setPrenom("Mamadou");
        agent.setNom("Diop");
        agent.setSiteAffecte("Ouakam");
        dossierEmployeRepository.save(agent);

        // Un pointage déjà ouvert du jour
        Pointage ouvert = Pointage.builder()
                .codeSecret("1234")
                .prenom("Mamadou")
                .nom("Diop")
                .date(LocalDate.now())
                .heureArrive("08:00")
                .status("EN COURS...")
                .deviceId("DEV-0")
                .timestamp(Instant.now())
                .build();
        String openId = pointageRepository.save(ouvert).getId();

        int n = 20;
        ExecutorService exec = Executors.newFixedThreadPool(n);
        List<CompletableFuture<Pointage>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final String device = "DEV-" + i;
            // lat/long null → aucun appel de géocodage déclenché
            futures.add(CompletableFuture.supplyAsync(
                    () -> service.enregistrerPointage("1234", device, null, null), exec));
        }

        long closuresOfOriginal = 0;
        for (CompletableFuture<Pointage> f : futures) {
            Pointage p = f.get(30, TimeUnit.SECONDS);
            if (openId.equals(p.getId()) && "TERMINÉ".equals(p.getStatus())) {
                closuresOfOriginal++;
            }
        }
        exec.shutdownNow();

        // Exactement un seul appel a « gagné » la clôture du pointage d'origine
        assertThat(closuresOfOriginal).isEqualTo(1);

        // Le pointage d'origine est bien clôturé en base (heureDepart + durée)
        Pointage reloaded = mongoTemplate.findById(openId, Pointage.class);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo("TERMINÉ");
        assertThat(reloaded.getHeureDepart()).isNotNull();
        assertThat(reloaded.getDuree()).isNotNull();
    }
}
