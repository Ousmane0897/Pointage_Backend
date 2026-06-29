package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyntheseMensuelleDto {

    private String mois;
    private String siteId;
    private String siteNom;
    private List<LigneSynthese> lignes;
    private double totalEntrees;
    private double totalSorties;
    private long valeurStockFinal;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LigneSynthese {
        private String produitId;
        private String produitCode;
        private String produitLibelle;
        private UniteStock unite;
        private String categorieLibelle;
        private double stockInitial;
        private double entrees;
        private double sorties;
        private double stockFinal;
        private long valeurFinale;
    }
}
