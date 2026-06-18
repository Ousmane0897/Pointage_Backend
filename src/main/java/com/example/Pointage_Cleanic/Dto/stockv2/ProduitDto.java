package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProduitDto {

    private String id;
    private String code;
    private String libelle;
    private TypeProduit typeProduit;
    private String categorieId;
    private String categorieLibelle;
    private String sousCategorie;
    private UniteStock unite;
    private String fournisseurPrincipal;
    private double seuilAlerte;
    private long prixUnitaire;

    private String photoUrl;
    private String photoNom;
    private String ficheTechniqueUrl;
    private String ficheTechniqueNom;
    private String ficheTechniqueMimeType;

    /** Somme du stock sur tous les sites (dénormalisé en lecture). */
    private Double quantiteTotale;

    private boolean actif;
    private String remarque;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
