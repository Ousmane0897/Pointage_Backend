package com.example.Pointage_Cleanic.Dto.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecapitulatifQuotidien {
    private LocalDate date;
    private int nbAgentsExploitation;
    private int nbAffectations;
    private int nbRetards;
    private int nbAbsences;
    private int nbPointagesHorsZone;
    private List<AlerteTerrainDto> alertes;
}