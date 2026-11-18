package com.example.Pointage_Cleanic.configurations;

import com.example.Pointage_Cleanic.entities.Pointage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * Index de la garde anti-triche du pointage kiosque ({@code PointageServices.verifierTelephone}),
 * interrogée à <b>chaque</b> pointage.
 *
 * <p>⚠ {@code auto-index-creation} est inactif : les {@code @Indexed} / {@code @CompoundIndex} de
 * l'entité ne sont pas créés d'eux-mêmes, d'où cette création explicite (idempotente, même
 * patron que {@link ProductionChimieIndexesConfig}). Un échec n'empêche pas le démarrage.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PointageIndexesConfig {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void initIndexes() {
        ensureIndex(new Index()
                .on("deviceId", Sort.Direction.ASC)
                .on("timestamp", Sort.Direction.DESC)
                .named("device_arrivee_idx"));
        ensureIndex(new Index()
                .on("deviceIdSortie", Sort.Direction.ASC)
                .on("timestampSortie", Sort.Direction.DESC)
                .sparse()
                .named("device_sortie_idx"));
        ensureIndex(new Index()
                .on("codeSecret", Sort.Direction.ASC)
                .on("date", Sort.Direction.ASC)
                .on("heureDepart", Sort.Direction.ASC)
                .named("pointage_flux_idx"));
    }

    private void ensureIndex(Index index) {
        try {
            mongoTemplate.indexOps(Pointage.class).ensureIndex(index);
        } catch (Exception e) {
            log.warn("Échec création index {} sur pointages : {}", index, e.getMessage());
        }
    }
}
