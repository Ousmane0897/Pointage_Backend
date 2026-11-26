package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.PiecesInventairesExistantsMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Inventaires existants vers le modèle pièce (lot 1 / 1b, D4) : bloc {@code piece} (référence
 * conservée comme numéro), registre des pièces, rapport. Transactionnelle.
 */
@ChangeUnit(id = "0013-pieces-inventaires-existants", order = "0013", author = "lot1-1b")
public class C0013PiecesInventairesExistants {

    private final PiecesInventairesExistantsMigration migration;

    public C0013PiecesInventairesExistants(PiecesInventairesExistantsMigration migration) {
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
