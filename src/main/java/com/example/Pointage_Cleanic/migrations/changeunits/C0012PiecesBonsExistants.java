package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.PiecesBonsExistantsMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Bons de stock existants vers le modèle pièce (lot 1 / 1b, D4) : bloc {@code piece} (référence
 * conservée comme numéro), registre des pièces, rapport. Transactionnelle. Passe avant la
 * création des index du registre (0109), qui vérifie alors l'absence de doublon.
 */
@ChangeUnit(id = "0012-pieces-bons-existants", order = "0012", author = "lot1-1b")
public class C0012PiecesBonsExistants {

    private final PiecesBonsExistantsMigration migration;

    public C0012PiecesBonsExistants(PiecesBonsExistantsMigration migration) {
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
