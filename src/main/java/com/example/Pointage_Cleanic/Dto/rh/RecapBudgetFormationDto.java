package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecapBudgetFormationDto {

    private Integer annee;
    private Long budgetPrevu;
    private Long budgetConsomme;
    private Long nombreFormations;
    private Double tauxParticipation;
}