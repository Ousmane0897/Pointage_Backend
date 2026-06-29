package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.GraviteDerive;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDerive;
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
public class RapportTableauBordFinancierDto {

    private Kpis kpis;
    private List<EvolutionValeur> evolutionValeur;
    private List<CoutParSite> coutParSite;
    private List<RepartitionCategorie> repartitionCategorie;
    private List<Derive> derives;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Kpis {
        private long valeurStock;
        private long valeurConsommeeMois;
        private long coutMoyenParSite;
        private long margeGlobale;
        private double tauxMargeMoyen;
        private int nbDerives;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EvolutionValeur {
        private String mois;
        private long valeur;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CoutParSite {
        private String siteNom;
        private long cout;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepartitionCategorie {
        private String categorie;
        private long valeur;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Derive {
        private String cible;
        private TypeDerive type;
        private long valeurActuelle;
        private long valeurReference;
        private double ecartPct;
        private GraviteDerive gravite;
    }
}
