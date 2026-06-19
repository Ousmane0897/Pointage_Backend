package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.entities.stockv2.Chantier;
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
public class CoutRevientChantierDto {

    /** Entité chantier 7.5 complète (coutTotal/nbMouvements dénormalisés à la lecture). */
    private Chantier chantier;
    private List<LigneChantier> lignes;
    private long coutTotal;
    private int nbProduits;
    private Integer dureeJours;
    private Long coutParJour;
    private Long coutMoyenChantiersSimilaires;
    private Double ecartPct;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LigneChantier {
        private String produitId;
        private String produitCode;
        private String produitLibelle;
        private UniteStock unite;
        private double quantite;
        private long coutUnitaire;
        private long montant;
        private Boolean estEstime;
    }
}
