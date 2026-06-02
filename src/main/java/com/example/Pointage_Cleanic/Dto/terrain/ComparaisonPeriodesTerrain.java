package com.example.Pointage_Cleanic.Dto.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparaisonPeriodesTerrain {
    private KpiTerrain periodeCourante;
    private KpiTerrain periodePrecedente;
    private double deltaCouverturePoints;
    private double deltaInterventionsPourcent;
    private double deltaSatisfactionPoints;
    private double deltaIncidentsPourcent;
}