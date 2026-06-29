package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.SensEcartDotation;
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
public class LigneComparatifDto {
    private String siteId;
    private String siteNom;
    private String produitId;
    private String produitCode;
    private String produitLibelle;
    private UniteStock unite;
    private double prevu;
    private double reel;
    private double ecart;
    private double pourcentageEcart;
    private SensEcartDotation sens;
}
