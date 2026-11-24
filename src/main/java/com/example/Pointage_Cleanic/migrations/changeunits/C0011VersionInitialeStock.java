package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.VersionInitialeStockMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Verrou optimiste (D1.4) : version 0 sur les bons, inventaires et produits existants, avant que
 * le code muni de {@code @Version} ne les relise et ne les réenregistre.
 */
@ChangeUnit(id = "0011-version-initiale-stock", order = "0011", author = "lot1-1a")
public class C0011VersionInitialeStock {

    private final VersionInitialeStockMigration migration;

    public C0011VersionInitialeStock(VersionInitialeStockMigration migration) {
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
