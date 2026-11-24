package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.ModulesAutorisesCleanupMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Retire les drapeaux obsolètes de utilisateur.modulesAutorises (ex-runner @Order 900).
 */
@ChangeUnit(id = "0001-modules-autorises-nettoyage", order = "0001", author = "lot1-1a")
public class C0001ModulesAutorisesNettoyage {

    private final ModulesAutorisesCleanupMigration migration;

    public C0001ModulesAutorisesNettoyage(ModulesAutorisesCleanupMigration migration) {
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
