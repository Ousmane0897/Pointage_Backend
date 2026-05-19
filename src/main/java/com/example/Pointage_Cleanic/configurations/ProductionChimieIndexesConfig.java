package com.example.Pointage_Cleanic.configurations;

import com.example.Pointage_Cleanic.entities.productionchimie.ControleQualite;
import com.example.Pointage_Cleanic.entities.productionchimie.FicheFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.FormatConditionnement;
import com.example.Pointage_Cleanic.entities.productionchimie.GrilleControle;
import com.example.Pointage_Cleanic.entities.productionchimie.Lot;
import com.example.Pointage_Cleanic.entities.productionchimie.MatierePremiere;
import com.example.Pointage_Cleanic.entities.productionchimie.MouvementStockChimie;
import com.example.Pointage_Cleanic.entities.productionchimie.OrdreFabrication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ProductionChimieIndexesConfig {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void initIndexes() {
        ensureIndex(MatierePremiere.class, new Index().on("code", Sort.Direction.ASC).unique().named("code_unique"));
        ensureIndex(MatierePremiere.class, new Index().on("actif", Sort.Direction.ASC).named("actif"));

        ensureIndex(MouvementStockChimie.class, new Index()
                .on("matierePremiereId", Sort.Direction.ASC)
                .on("date", Sort.Direction.DESC)
                .named("matiere_date"));
        ensureIndex(MouvementStockChimie.class, new Index().on("ordreFabricationId", Sort.Direction.ASC).named("of_id"));
        ensureIndex(MouvementStockChimie.class, new Index()
                .on("type", Sort.Direction.ASC)
                .on("date", Sort.Direction.DESC)
                .named("type_date"));

        ensureIndex(FicheFormulation.class, new Index().on("code", Sort.Direction.ASC).unique().named("code_unique"));
        ensureIndex(FicheFormulation.class, new Index().on("statut", Sort.Direction.ASC).named("statut"));

        ensureIndex(OrdreFabrication.class, new Index().on("numero", Sort.Direction.ASC).unique().named("numero_unique"));
        ensureIndex(OrdreFabrication.class, new Index().on("statut", Sort.Direction.ASC).named("statut"));
        ensureIndex(OrdreFabrication.class, new Index().on("dateLancementPrevue", Sort.Direction.DESC).named("date_lancement"));
        ensureIndex(OrdreFabrication.class, new Index().on("operateurResponsableId", Sort.Direction.ASC).named("operateur"));

        ensureIndex(Lot.class, new Index().on("numero", Sort.Direction.ASC).unique().named("numero_unique"));
        ensureIndex(Lot.class, new Index()
                .on("produitNom", Sort.Direction.ASC)
                .on("dateFabrication", Sort.Direction.DESC)
                .named("produit_date"));
        ensureIndex(Lot.class, new Index().on("statutControle", Sort.Direction.ASC).named("statut_controle"));
        ensureIndex(Lot.class, new Index().on("statutStock", Sort.Direction.ASC).named("statut_stock"));
        ensureIndex(Lot.class, new Index().on("formulationId", Sort.Direction.ASC).named("formulation_id"));

        ensureIndex(GrilleControle.class, new Index().on("produitNom", Sort.Direction.ASC).named("produit_nom"));
        ensureIndex(GrilleControle.class, new Index().on("formulationId", Sort.Direction.ASC).named("formulation_id"));

        ensureIndex(ControleQualite.class, new Index().on("lotId", Sort.Direction.ASC).named("lot_id"));
        ensureIndex(ControleQualite.class, new Index()
                .on("produitNom", Sort.Direction.ASC)
                .on("dateControle", Sort.Direction.DESC)
                .named("produit_date"));
        ensureIndex(ControleQualite.class, new Index().on("decision", Sort.Direction.ASC).named("decision"));

        ensureIndex(FormatConditionnement.class, new Index().on("code", Sort.Direction.ASC).unique().named("code_unique"));
        ensureIndex(FormatConditionnement.class, new Index().on("actif", Sort.Direction.ASC).named("actif"));

        log.info("Indexes Production Chimie initialisés");
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
