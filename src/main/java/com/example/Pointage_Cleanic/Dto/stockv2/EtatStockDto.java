package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.StatutStock;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EtatStockDto {

    private String produitId;
    private String produitCode;
    private String produitLibelle;
    private TypeProduit typeProduit;
    private String categorieLibelle;
    private UniteStock unite;
    /** Absent (null) = ligne consolidée tous sites. */
    private String siteId;
    private String siteNom;
    private double quantite;
    private double seuilAlerte;
    private StatutStock statut;
    private long prixUnitaire;
    private double valeur;
    private LocalDateTime dateMaj;
}
