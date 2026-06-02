package com.example.Pointage_Cleanic.Dto.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiTerrain {
    private String dateDebut;
    private String dateFin;
    private long nbAffectationsPlanifiees;
    private long nbInterventionsRealisees;
    private double tauxCouverture;
    private long nbAgentsActifs;
    private long nbSitesActifs;
    private double satisfactionMoyenne;
    private long nbControles;
    private long nbControlesConformes;
    private long nbIncidents;
    private long nbAlertesEscaladees;
}