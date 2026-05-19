package com.example.Pointage_Cleanic.entities.productionchimie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtapeFormulation {
    private Integer ordre;
    private String libelle;
    private Integer dureeMinutes;
    private Double temperature;
    private Double pression;
    private Double vitesseAgitation;
    private Integer dureeReposMinutes;
    private String instructions;
}