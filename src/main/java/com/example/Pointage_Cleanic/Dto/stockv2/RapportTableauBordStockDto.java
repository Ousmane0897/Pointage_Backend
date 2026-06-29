package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
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
public class RapportTableauBordStockDto {

    private Kpis kpis;
    private List<ValeurCategorie> valeurParCategorie;
    private List<EvolutionValeur> evolutionValeur;
    private List<TopConsommation> topConsommations;
    private List<ProduitDormant> produitsDormants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Kpis {
        private long valeurTotale;
        private long nbProduits;
        private long nbRupture;
        private long nbAlerte;
        private double tauxRotationMoyen;
        private long nbDormants;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValeurCategorie {
        private String categorie;
        private long valeur;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvolutionValeur {
        private String mois;
        private long valeur;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopConsommation {
        private String produitLibelle;
        private double quantite;
        private UniteStock unite;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProduitDormant {
        private String produitId;
        private String produitCode;
        private String produitLibelle;
        private LocalDateTime dernierMouvement;
        private double quantite;
        private long valeur;
    }
}
