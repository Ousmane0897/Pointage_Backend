package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.TerrainDemoSeed;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.context.annotation.Profile;

/**
 * Démonstration : sites fictifs, grille terrain, produit phyto, escalade (ex-TerrainDataLoader).
 * Profil demo seulement.
 */
@ChangeUnit(id = "9002-demo-terrain", order = "9002", author = "lot1-1a")
@Profile("demo")
public class C9002DemoTerrain {

    private final TerrainDemoSeed migration;

    public C9002DemoTerrain(TerrainDemoSeed migration) {
        this.migration = migration;
    }

    @Execution
    public void executer() {
        migration.executer();
    }

    @RollbackExecution
    public void annuler() {
        // Transactionnelle : en cas d'échec, la base annule tout ; rien à défaire à la main.
    }
}
