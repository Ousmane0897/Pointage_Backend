package com.example.Pointage_Cleanic.entities.GestionModules;

import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.Absences;
import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.CollecteLivraison;
import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.Pointages;
import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.ProductionChimie;
import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.Stock;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModulesAutorises {

    private boolean Dashboard;
    private boolean Admin;
    private boolean StatistiquesAgences;
    private boolean Planifications;
    private boolean Calendrier;
    private boolean JourFeries;
    private boolean Employes;
    private boolean Agences;
    private boolean RH;

    private CollecteLivraison CollecteLivraison;
    private Absences Absences;
    private Pointages Pointages;
    private Stock Stock;
    private ProductionChimie productionChimie;
}