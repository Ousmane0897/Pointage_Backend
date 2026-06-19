package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.AlerteCout;
import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoutProduitDto {
    private String produitId;
    private String produitCode;
    private String produitLibelle;
    private TypeProduit typeProduit;
    private String categorieLibelle;
    private com.example.Pointage_Cleanic.Enum.stockv2.UniteStock unite;

    private long coutCourant;
    private MethodeValorisation methodeEffective;
    /** Override explicite du produit (null = hérite du global). */
    private MethodeValorisation methodeProduit;
    private Long prixVente;

    private Double quantiteTotale;
    private Long valeurStock;

    private List<AlerteCout> alertes;
    private Long dernierCout;
    private LocalDateTime dateDernierCalcul;
}
