package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyntheseMargesDto {

    private List<LigneMarge> lignes;
    private long margeGlobaleTotale;
    private long chiffreAffaires;
    private long coutTotal;
    private double tauxMargeMoyen;
    private int nbProduitsNonRentables;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LigneMarge {
        private String produitId;
        private String produitCode;
        private String produitLibelle;
        private TypeProduit typeProduit;
        private String categorieLibelle;
        private long prixVente;
        private long coutRevient;
        private long margeUnitaire;
        private double tauxMarge;
        private double quantiteVendue;
        private long margeGlobale;
        private boolean rentable;
    }
}
