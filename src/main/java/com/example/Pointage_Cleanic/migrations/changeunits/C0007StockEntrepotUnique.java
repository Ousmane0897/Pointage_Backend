package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.StockEntrepotUniqueMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Fusionne les soldes rattachés à un site dans l'entrepôt unique (ex-runner @Order 1010).
 * runAlways : un inventaire par site recrée des soldes de site, que le runner refusionnait à
 * chaque démarrage ; ce comportement est conservé jusqu'au référentiel des magasins (lot 3).
 */
@ChangeUnit(id = "0007-stock-entrepot-unique", order = "0007", author = "lot1-1a", runAlways = true)
public class C0007StockEntrepotUnique {

    private final StockEntrepotUniqueMigration migration;

    public C0007StockEntrepotUnique(StockEntrepotUniqueMigration migration) {
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
