package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.RhModuleMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * modulesAutorises.rh booléen → objet de 19 sous-drapeaux (ex-runner @Order 901).
 */
@ChangeUnit(id = "0002-rh-modules-booleen-vers-objet", order = "0002", author = "lot1-1a")
public class C0002RhModulesBooleenVersObjet {

    private final RhModuleMigration migration;

    public C0002RhModulesBooleenVersObjet(RhModuleMigration migration) {
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
