package com.example.Pointage_Cleanic.entities.productionchimie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionsContenant {
    private Double longueurMm;
    private Double largeurMm;
    private Double hauteurMm;
}