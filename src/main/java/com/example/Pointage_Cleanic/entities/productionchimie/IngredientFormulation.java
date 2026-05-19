package com.example.Pointage_Cleanic.entities.productionchimie;

import com.example.Pointage_Cleanic.Enum.UniteChimie;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientFormulation {
    private String matierePremiereId;
    private String matierePremiereNom;
    private Double dosage;
    private UniteChimie unite;
    private Integer ordre;
    private String remarque;
}