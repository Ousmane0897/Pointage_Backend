package com.example.Pointage_Cleanic.Dto.stockv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LigneCoutMouvementDto {
    /** Le mouvement complet (avec coutUnitaireSnapshot/valeurMouvement s'ils existent). */
    private MouvementStockDto mouvement;
    private long coutUnitaire;
    private long valeur;
    /** true si reconstitué (mouvement antérieur à 7.6, sans snapshot). */
    private boolean estEstime;
}
