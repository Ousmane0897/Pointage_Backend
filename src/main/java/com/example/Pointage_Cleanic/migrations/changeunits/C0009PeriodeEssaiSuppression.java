package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.PeriodeEssaiDropMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Supprime les collections de l'ancien module Période d'essai (ex-runner @Order 1200). Non
 * transactionnelle : la suppression de collection est interdite dans une transaction.
 */
@ChangeUnit(id = "0009-periode-essai-suppression", order = "0009", author = "lot1-1a", transactional = false)
public class C0009PeriodeEssaiSuppression {

    private final PeriodeEssaiDropMigration migration;

    public C0009PeriodeEssaiSuppression(PeriodeEssaiDropMigration migration) {
        this.migration = migration;
    }

    @Execution
    public void executer() {
        migration.executer();
    }

    @RollbackExecution
    public void annuler() {
        // Non transactionnelle et sans retour arrière possible : l'opération est idempotente.
    }
}
