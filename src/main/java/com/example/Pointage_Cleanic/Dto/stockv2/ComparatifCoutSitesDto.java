package com.example.Pointage_Cleanic.Dto.stockv2;

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
public class ComparatifCoutSitesDto {

    private List<LigneSite> lignes;
    private long coutTotalGlobal;
    private long coutMoyenParSite;
    private int nbSitesSurconsommation;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LigneSite {
        private String siteId;
        private String siteNom;
        private long coutTotal;
        private long nbSorties;
        private double quantiteTotale;
        private double pourcentage;
        private Long coutMoyenReference;
        private Double ecartPct;
        private boolean surconsommation;
    }
}
