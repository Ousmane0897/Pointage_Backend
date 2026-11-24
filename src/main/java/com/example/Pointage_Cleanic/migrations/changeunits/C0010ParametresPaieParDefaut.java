package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.ParametresPaieParDefautMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Paramètres de paie par défaut (taux Sénégal) si absents (ex-ParametresPaieDataLoader). Données
 * de référence, pas de démonstration.
 */
@ChangeUnit(id = "0010-parametres-paie-par-defaut", order = "0010", author = "lot1-1a")
public class C0010ParametresPaieParDefaut {

    private final ParametresPaieParDefautMigration migration;

    public C0010ParametresPaieParDefaut(ParametresPaieParDefautMigration migration) {
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
