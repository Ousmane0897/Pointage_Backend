package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.index.CreateurIndex;
import com.example.Pointage_Cleanic.migrations.index.IndexesAttendus;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Index du registre des pièces ({@code registre_pieces}) (lot 1 / 1b, décision D4), d'après
 * {@link IndexesAttendus}. Non transactionnelle, comme les ChangeUnits d'index de 1a.
 */
@ChangeUnit(id = "0109-index-registre", order = "0109", author = "lot1-1b", transactional = false)
public class C0109IndexRegistre {

    private static final String ID = "0109-index-registre";

    private final CreateurIndex createurIndex;

    public C0109IndexRegistre(CreateurIndex createurIndex) {
        this.createurIndex = createurIndex;
    }

    @Execution
    public void executer() {
        createurIndex.creer(IndexesAttendus.REGISTRE, ID);
    }

    @RollbackExecution
    public void annuler() {
        // Rien à défaire : les index créés sont valides, et la création est idempotente.
    }
}
