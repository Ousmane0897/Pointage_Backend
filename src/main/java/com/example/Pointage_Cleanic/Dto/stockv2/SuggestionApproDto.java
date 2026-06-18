package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
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
public class SuggestionApproDto {
    private String produitId;
    private String produitCode;
    private String produitLibelle;
    private UniteStock unite;
    private String fournisseurPrincipal;
    private double stockActuel;
    private double seuilAlerte;
    private double consommationMoyenne;
    private double consommationPrevisionnelle;
    private double besoin;
    private double quantiteSuggeree;
    private long prixUnitaire;
    private long montantEstime;
}
