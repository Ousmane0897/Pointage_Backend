package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.AgentIdDateBackfillMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Date d'attribution du code agent estimée depuis l'ObjectId (ex-runner @Order 960). Ponctuelle :
 * le service date déjà tout nouveau code.
 */
@ChangeUnit(id = "0005-dossiers-date-code-agent", order = "0005", author = "lot1-1a")
public class C0005DossiersDateCodeAgent {

    private final AgentIdDateBackfillMigration migration;

    public C0005DossiersDateCodeAgent(AgentIdDateBackfillMigration migration) {
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
