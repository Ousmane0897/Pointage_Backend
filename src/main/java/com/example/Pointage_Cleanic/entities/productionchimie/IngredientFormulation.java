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
    /** Quantité pour le lot de référence. Optionnel si {@code qs} ou {@code ingredientComplement}. */
    private Double dosage;
    private UniteChimie unite;
    private Integer ordre;
    private String remarque;

    /** Ligne de complément « qsp » (ex. eau) : sa quantité est calculée automatiquement (Fonction B). */
    private boolean ingredientComplement;
    /** Ligne « quantité suffisante » (ex. soude) : sans valeur fixe, ignorée par tous les calculs. */
    private boolean qs;
}