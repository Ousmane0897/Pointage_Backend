package com.example.Pointage_Cleanic.entities.GestionModules;

import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.ProductionChimie;
import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.Rh;
import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.Stock;
import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.Terrain;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModulesAutorises {

    private boolean dashboard;
    private boolean admin;
    private Rh rh;

    private ProductionChimie productionChimie;
    private Terrain terrain;
    private Stock stock;
}
