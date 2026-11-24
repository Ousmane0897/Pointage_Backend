package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.ContratAlternanceMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * typeContrat ALTERNANCE → PRESTATION (ex-runner @Order 1000).
 */
@ChangeUnit(id = "0006-contrats-alternance-vers-prestation", order = "0006", author = "lot1-1a")
public class C0006ContratsAlternanceVersPrestation {

    private final ContratAlternanceMigration migration;

    public C0006ContratsAlternanceVersPrestation(ContratAlternanceMigration migration) {
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
