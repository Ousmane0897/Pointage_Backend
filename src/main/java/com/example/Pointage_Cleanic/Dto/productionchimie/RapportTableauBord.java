package com.example.Pointage_Cleanic.Dto.productionchimie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RapportTableauBord {
    private KpiProductionPeriode kpis;
    private List<VolumeParProduit> volumesParProduit;
    private List<EvolutionMensuelle> evolutionMensuelle;
    private List<RendementProduit> rendements;
    private RepartitionStatutCq repartitionCq;
    private ComparaisonPeriodes comparaison;
}
