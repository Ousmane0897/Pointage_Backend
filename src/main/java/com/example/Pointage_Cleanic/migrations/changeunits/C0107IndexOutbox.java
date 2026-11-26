package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.index.CreateurIndex;
import com.example.Pointage_Cleanic.migrations.index.IndexesAttendus;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Index de l'outbox des événements de domaine ({@code evenements_domaine},
 * {@code evenements_consommes}) (lot 1 / 1b, décision D5), d'après {@link IndexesAttendus}.
 * Non transactionnelle, comme les ChangeUnits d'index de 1a.
 */
@ChangeUnit(id = "0107-index-outbox", order = "0107", author = "lot1-1b", transactional = false)
public class C0107IndexOutbox {

    private static final String ID = "0107-index-outbox";

    private final CreateurIndex createurIndex;

    public C0107IndexOutbox(CreateurIndex createurIndex) {
        this.createurIndex = createurIndex;
    }

    @Execution
    public void executer() {
        createurIndex.creer(IndexesAttendus.OUTBOX, ID);
    }

    @RollbackExecution
    public void annuler() {
        // Rien à défaire : les index créés sont valides, et la création est idempotente.
    }
}
