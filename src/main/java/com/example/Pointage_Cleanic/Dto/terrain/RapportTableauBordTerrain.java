package com.example.Pointage_Cleanic.Dto.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RapportTableauBordTerrain {
    private KpiTerrain kpis;
    private List<InterventionsParSite> interventionsParSite;
    private List<PointEvolution> evolutionCouverture;
    private List<IncidentsParSite> incidentsParSite;
    private List<PointEvolution> evolutionSatisfaction;
    private ComparaisonPeriodesTerrain comparaison;
}