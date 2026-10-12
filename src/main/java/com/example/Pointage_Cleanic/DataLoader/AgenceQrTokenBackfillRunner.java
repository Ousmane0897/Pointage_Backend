package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import com.example.Pointage_Cleanic.services.pointage.AgenceQrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pose un QR code de pointage sur chaque agence ({@link SiteClient}) qui n'en a pas,
 * puis garantit les index du QR d'agence.
 * <p>
 * <b>Idempotent</b> : seules les agences sans {@code qrToken} sont touchées, un jeton
 * existant (donc un QR déjà imprimé) n'est jamais remplacé.
 * <p>
 * ⚠ <b>Index créés explicitement</b> : {@code spring.data.mongodb.auto-index-creation}
 * n'est pas activé (défaut {@code false} depuis Spring Boot 3), les {@code @Indexed} des
 * entités restent donc sans effet. L'index unique de {@code qrToken} est posé <b>après</b>
 * le backfill, et il est {@code sparse} pour tolérer une agence créée hors API sans jeton.
 * <p>
 * Les pointages historiques ne sont <b>pas</b> rattachés à une agence : ils restent
 * « Agence non renseignée » (décision métier, backfill éventuel à trancher plus tard).
 */
@Slf4j
@Component
@Order(1300)
@RequiredArgsConstructor
public class AgenceQrTokenBackfillRunner implements CommandLineRunner {

    private final SiteClientRepository siteClientRepository;
    private final AgenceQrService agenceQrService;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        List<SiteClient> sansJeton = siteClientRepository.findByQrTokenIsNull();
        int generes = 0;
        for (SiteClient site : sansJeton) {
            if (agenceQrService.assurerToken(site)) {
                siteClientRepository.save(site);
                generes++;
            }
        }
        if (generes > 0) {
            log.info("QR codes d'agence : {} jeton(s) généré(s)", generes);
        }

        ensureIndex(SiteClient.class, new Index().on("qrToken", Sort.Direction.ASC)
                .unique().sparse().named("qrToken_unique"));
        ensureIndex(Pointage.class, new Index().on("agenceId", Sort.Direction.ASC).named("agenceId"));
        ensureIndex(Pointage.class, new Index().on("agenceIdSortie", Sort.Direction.ASC).named("agenceIdSortie"));
    }

    /** {@code ensureIndex} est idempotent côté Mongo ; un échec ne bloque pas le démarrage. */
    private void ensureIndex(Class<?> entityClass, Index index) {
        try {
            mongoTemplate.indexOps(entityClass).ensureIndex(index);
        } catch (Exception e) {
            log.warn("Index {} non créé sur {} : {}",
                    index.getIndexOptions().get("name"), entityClass.getSimpleName(), e.getMessage());
        }
    }
}
