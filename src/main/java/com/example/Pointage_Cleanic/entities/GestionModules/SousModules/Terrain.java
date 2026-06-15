package com.example.Pointage_Cleanic.entities.GestionModules.SousModules;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Terrain {
    private boolean sitesClients;
    private boolean planning;
    private boolean pointage;
    private boolean alertes;
    private boolean interventions;
    private boolean controleQualite;
    private boolean materiel;
    private boolean phytosanitaire;
    private boolean tableauBord;
}
