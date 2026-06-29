package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.AxeComparatif;
import com.example.Pointage_Cleanic.Enum.stockv2.SensEvolution;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Matrice du comparatif mensuel (7.5 — /analyse/comparatif). Valeurs = montant FCFA. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MatriceComparatifDto {

    private AxeComparatif axe;
    private List<String> mois;
    private List<LigneComparatif> lignes;
    private List<Serie> series;
    private List<Long> totauxParMois;
    private long totalGeneral;
    private int nbAlertes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LigneComparatif {
        private String cleId;
        private String libelle;
        private List<Cellule> cellules;
        private long total;
        private Double evolutionGlobalePct;
        private SensEvolution sensGlobal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Cellule {
        private String mois;
        private long valeur;
        private Double quantite;
        private Double evolutionPct;
        private SensEvolution sens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Serie {
        private String label;
        private List<Long> data;
    }
}
