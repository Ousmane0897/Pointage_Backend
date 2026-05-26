package com.example.Pointage_Cleanic.Dto.productionchimie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparaisonPeriodes {
    private KpiProductionPeriode periodeCourante;
    private KpiProductionPeriode periodePrecedente;
    private double deltaVolumePourcent;
    private double deltaTauxReussitePoints;
    private double deltaNbOfTerminesPourcent;
}
