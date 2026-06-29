package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Vue mensuelle de consommation (7.5 — /analyse/mensuel). Montants en FCFA entiers. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyntheseConsoMensuelleDto {

    private Kpis kpis;
    private List<LigneMensuelle> lignes;
    private List<PointEvolution> evolution;
    private List<RepartitionItem> topProduits;
    private List<RepartitionItem> repartitionCategorie;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Kpis {
        private long coutTotal;
        private double quantiteTotale;
        private int nbProduits;
        private long nbMouvements;
        private Double evolutionCoutPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LigneMensuelle {
        private String produitId;
        private String produitCode;
        private String produitLibelle;
        private UniteStock unite;
        private double quantite;
        private long cout;
        private Double quantitePrecedente;
        private Long coutPrecedent;
        private Double evolutionPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PointEvolution {
        private String mois;
        private long cout;
        private double quantite;
    }

    /** Élément libellé/montant/quantité (topProduits, repartitionCategorie). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepartitionItem {
        private String libelle;
        private long montant;
        private Double quantite;
    }
}
