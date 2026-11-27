package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.E2eStockSeed;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.context.annotation.Profile;

/**
 * Essais de bout en bout : Jeu de départ Stock (site, catégorie, articles, stock initial). Profil e2e seulement, base dont le nom contient « e2e »
 * (jamais en production). Base remise à zéro à chaque lancement de {@code npm run e2e}.
 */
@ChangeUnit(id = "9102-e2e-stock", order = "9102", author = "lot1-outillage")
@Profile("e2e")
public class C9102E2eStock {

    private final E2eStockSeed migration;

    public C9102E2eStock(E2eStockSeed migration) {
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
