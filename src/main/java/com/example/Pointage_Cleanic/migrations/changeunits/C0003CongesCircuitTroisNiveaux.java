package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.CongeStatutMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Demandes de congé EN_ATTENTE → circuit à 3 niveaux, nombreJours en jours ouvrés (ex-runner
 * @Order 902). Ponctuelle : ne réécrase plus, à chaque démarrage, le nombre de jours calculé par
 * les services (qui déduisent les jours fériés).
 */
@ChangeUnit(id = "0003-conges-circuit-trois-niveaux", order = "0003", author = "lot1-1a")
public class C0003CongesCircuitTroisNiveaux {

    private final CongeStatutMigration migration;

    public C0003CongesCircuitTroisNiveaux(CongeStatutMigration migration) {
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
