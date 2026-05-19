package com.example.Pointage_Cleanic.entities.productionchimie;

import com.example.Pointage_Cleanic.Enum.TypeTest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestControle {
    private String libelle;
    private String parametre;
    private String valeurCible;
    private Double toleranceMin;
    private Double toleranceMax;
    private String unite;
    private boolean obligatoire;
    private TypeTest type;
}
