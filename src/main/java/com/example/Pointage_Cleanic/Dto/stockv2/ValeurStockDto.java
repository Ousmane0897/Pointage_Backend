package com.example.Pointage_Cleanic.Dto.stockv2;

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
public class ValeurStockDto {

    private Kpis kpis;
    private List<RepartitionCategorie> repartitionCategorie;
    private List<LigneValeur> lignes;
    /** Horodatage ISO complet du snapshot (affiché côté front, polling 30 s). */
    private String dateCalcul;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Kpis {
        private long valeurTotale;
        private long nbProduits;
        private Long valeurPrecedente;
        private Long ecartValeur;
        private Double ecartPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepartitionCategorie {
        private String categorie;
        private long valeur;
        private Double quantite;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LigneValeur {
        private String produitId;
        private String produitCode;
        private String produitLibelle;
        private String categorieLibelle;
        private double quantite;
        private long coutUnitaire;
        private long valeur;
    }
}
