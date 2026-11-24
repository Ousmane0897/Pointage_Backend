package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.RhDemoSeed;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.context.annotation.Profile;

/**
 * Démonstration : grille d'évaluation générique et formations d'exemple (ex-DataLoader). Profil
 * demo seulement.
 */
@ChangeUnit(id = "9001-demo-rh", order = "9001", author = "lot1-1a")
@Profile("demo")
public class C9001DemoRh {

    private final RhDemoSeed migration;

    public C9001DemoRh(RhDemoSeed migration) {
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
