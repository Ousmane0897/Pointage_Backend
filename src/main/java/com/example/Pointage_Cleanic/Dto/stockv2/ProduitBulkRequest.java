package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProduitBulkRequest {

    private List<LigneImport> produits;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LigneImport {
        private Integer numeroLigne;
        private String code;
        private String libelle;
        private TypeProduit typeProduit;
        private String categorieLibelle;
        private String sousCategorie;
        private UniteStock unite;
        private String fournisseurPrincipal;
        private Double seuilAlerte;
        private Long prixUnitaire;
        private Double stockInitial;
        private Boolean actif;
        private String remarque;
    }
}
