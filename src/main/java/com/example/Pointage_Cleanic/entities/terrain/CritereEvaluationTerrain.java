package com.example.Pointage_Cleanic.entities.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Critère d'une grille d'évaluation terrain. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CritereEvaluationTerrain {
    private String libelle;
    private String parametre;
    private Double poids;
    private Double noteMin;
    private Double noteMax;
    private boolean obligatoire;
    private String description;
}