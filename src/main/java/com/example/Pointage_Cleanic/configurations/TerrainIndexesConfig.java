package com.example.Pointage_Cleanic.configurations;

import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * Indexes du module Terrain, créés programmatiquement au démarrage.
 *
 * <p>Les annotations {@code @Indexed} / {@code @CompoundIndex} sont <b>inopérantes</b> dans ce
 * projet : {@code spring.data.mongodb.auto-index-creation} n'est pas activé et
 * {@code MongoConfigurations} ne le surcharge pas. D'où ce pattern, calqué sur
 * {@link ProductionChimieIndexesConfig}.
 *
 * <p>{@code terrain_affectations} n'avait jusqu'ici <b>aucun index</b>, alors que trois usages la
 * balaient intégralement : {@code PlanningService.list} / {@code stats} (filtres statut + fenêtre
 * de dates), et surtout {@code AffectationStatutScheduler} qui recharge les affectations non
 * terminales <b>chaque minute</b>.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TerrainIndexesConfig {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void initIndexes() {
        // Couvre list/stats (statut + chevauchement de dates) et le scheduler (findByStatutIn).
        ensureIndex(AffectationAgent.class, new Index()
                .on("statut", Sort.Direction.ASC)
                .on("dateDebut", Sort.Direction.ASC)
                .on("dateFin", Sort.Direction.ASC)
                .named("statut_debut_fin"));

        // verifierConflit charge toutes les affectations d'un agent à chaque create/update.
        ensureIndex(AffectationAgent.class, new Index()
                .on("employeId", Sort.Direction.ASC)
                .named("employe_id"));

        // EffectifSiteService.compterEffectifTerrain : countBySiteIdAndStatutNot.
        ensureIndex(AffectationAgent.class, new Index()
                .on("siteId", Sort.Direction.ASC)
                .on("statut", Sort.Direction.ASC)
                .named("site_statut"));

        log.info("Indexes Terrain initialisés");
    }

    private void ensureIndex(Class<?> entityClass, Index index) {
        try {
            IndexOperations ops = mongoTemplate.indexOps(entityClass);
            ops.ensureIndex(index);
        } catch (Exception e) {
            log.warn("Échec création index {} sur {}: {}", index, entityClass.getSimpleName(), e.getMessage());
        }
    }
}
