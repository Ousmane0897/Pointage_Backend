package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.index.CreateurIndex;
import com.example.Pointage_Cleanic.migrations.index.IndexesAttendus;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Index du journal d'audit ({@code journal_audit}) (lot 1 / 1b, décision D6), d'après
 * {@link IndexesAttendus}. Non transactionnelle, comme les ChangeUnits d'index de 1a.
 */
@ChangeUnit(id = "0108-index-audit", order = "0108", author = "lot1-1b", transactional = false)
public class C0108IndexAudit {

    private static final String ID = "0108-index-audit";

    private final CreateurIndex createurIndex;

    public C0108IndexAudit(CreateurIndex createurIndex) {
        this.createurIndex = createurIndex;
    }

    @Execution
    public void executer() {
        createurIndex.creer(IndexesAttendus.AUDIT, ID);
    }

    @RollbackExecution
    public void annuler() {
        // Rien à défaire : les index créés sont valides, et la création est idempotente.
    }
}
