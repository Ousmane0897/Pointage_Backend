package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.DossierAgentIdMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Renomme la clé AgentId en agentId sur dossiers_employes (ex-runner @Order 950).
 */
@ChangeUnit(id = "0004-dossiers-agentid-renommage", order = "0004", author = "lot1-1a")
public class C0004DossiersAgentidRenommage {

    private final DossierAgentIdMigration migration;

    public C0004DossiersAgentidRenommage(DossierAgentIdMigration migration) {
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
