package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.AffectationSiteBackfillMigration;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Dérive et complète les affectations des dossiers (ex-runner @Order 1200). runAlways : les
 * dossiers créés depuis reçoivent leur rythme et leur date d'entrée à chaque démarrage, comme
 * avant ; à remplacer par un calcul à l'écriture en 1d.
 */
@ChangeUnit(id = "0008-affectations-sites-complement", order = "0008", author = "lot1-1a", runAlways = true)
public class C0008AffectationsSitesComplement {

    private final AffectationSiteBackfillMigration migration;

    public C0008AffectationsSitesComplement(AffectationSiteBackfillMigration migration) {
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
