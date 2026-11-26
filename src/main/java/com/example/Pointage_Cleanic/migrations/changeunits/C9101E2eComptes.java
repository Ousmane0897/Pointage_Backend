package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.E2eComptesSeed;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.context.annotation.Profile;

/**
 * Essais de bout en bout : Comptes d'essai (super-admin, compte limité au Stock). Profil e2e seulement, base dont le nom contient « e2e »
 * (jamais en production). Base remise à zéro à chaque lancement de {@code npm run e2e}.
 */
@ChangeUnit(id = "9101-e2e-comptes", order = "9101", author = "lot1-outillage")
@Profile("e2e")
public class C9101E2eComptes {

    private final E2eComptesSeed migration;

    public C9101E2eComptes(E2eComptesSeed migration) {
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
