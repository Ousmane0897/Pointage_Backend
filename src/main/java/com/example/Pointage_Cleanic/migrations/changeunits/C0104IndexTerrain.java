package com.example.Pointage_Cleanic.migrations.changeunits;

import com.example.Pointage_Cleanic.migrations.index.CreateurIndex;
import com.example.Pointage_Cleanic.migrations.index.IndexesAttendus;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Index du module Terrain (dont les 3 index de l'ancienne TerrainIndexesConfig) (lot 1 / 1a, décision D3), d'après {@link IndexesAttendus}.
 * Non transactionnelle : MongoDB n'autorise pas la création d'index dans une transaction sur une
 * collection existante. Doublons ou conflit de définition : rapport dans migration_rapports et
 * échec (démarrage refusé), sans rien dédoublonner.
 */
@ChangeUnit(id = "0104-index-terrain", order = "0104", author = "lot1-1a", transactional = false)
public class C0104IndexTerrain {

    private static final String ID = "0104-index-terrain";

    private final CreateurIndex createurIndex;

    public C0104IndexTerrain(CreateurIndex createurIndex) {
        this.createurIndex = createurIndex;
    }

    @Execution
    public void executer() {
        createurIndex.creer(IndexesAttendus.TERRAIN, ID);
    }

    @RollbackExecution
    public void annuler() {
        // Rien à défaire : les index créés sont valides, et la création est idempotente.
    }
}
