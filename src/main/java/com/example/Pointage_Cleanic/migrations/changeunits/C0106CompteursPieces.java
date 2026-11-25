package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.CompteursPiecesMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Collection des compteurs de pièces (lot 1 / 1b, décision D4). Non transactionnelle : une
 * collection ne se crée pas de façon fiable dans une transaction.
 */
@ChangeUnit(id = "0106-compteurs-pieces", order = "0106", author = "lot1-1b", transactional = false)
public class C0106CompteursPieces {

    private final CompteursPiecesMigration migration;

    public C0106CompteursPieces(CompteursPiecesMigration migration) {
        this.migration = migration;
    }

    @Execution
    public void executer() {
        migration.executer();
    }

    @RollbackExecution
    public void annuler() {
        // Rien à défaire : une collection vide, recréée à l'identique au besoin.
    }
}
