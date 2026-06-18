package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.NatureDon;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Synthèse des dons (7.5 — /analyse/dons). Une ligne par bon de sortie DON EFFECTIF. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyntheseDonsDto {

    private Kpis kpis;
    private List<LigneDon> lignes;
    private List<RepartitionItem> repartitionNature;
    private List<RepartitionItem> topBeneficiaires;
    private List<PointEvolutionDon> evolutionMensuelle;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Kpis {
        private long montantTotal;
        private long nbDons;
        private long nbBeneficiaires;
        private Double evolutionPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LigneDon {
        private String bonId;
        private String reference;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate date;

        private NatureDon natureDon;
        private String beneficiaire;
        private String siteSourceNom;
        private int nbProduits;
        private double quantiteTotale;
        private long montant;
    }

    /** Élément libellé/montant/nombre (repartitionNature, topBeneficiaires). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepartitionItem {
        private String libelle;
        private long montant;
        private Long nombre;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PointEvolutionDon {
        private String mois;
        private long montant;
    }
}
