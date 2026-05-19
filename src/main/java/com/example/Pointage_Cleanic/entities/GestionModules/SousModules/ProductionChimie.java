package com.example.Pointage_Cleanic.entities.GestionModules.SousModules;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductionChimie {
    private boolean formulations;
    private boolean ordresFabrication;
    private boolean lots;
    private boolean controleQualite;
    private boolean matieresPremieres;
    private boolean conditionnement;
    private boolean tableauBord;
}