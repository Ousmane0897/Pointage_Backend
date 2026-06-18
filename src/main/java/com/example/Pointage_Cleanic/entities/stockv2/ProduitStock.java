package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Produit du catalogue Stock v2. Le stock physique n'est PAS porté ici :
 * il vit dans {@link StockParSite} (un solde par couple produit/site).
 * {@code quantiteTotale} est dénormalisée à la lecture (somme des soldes).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_produits")
public class ProduitStock {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String libelle;
    private TypeProduit typeProduit;

    @Indexed
    private String categorieId;
    private String sousCategorie;

    private UniteStock unite;
    private String fournisseurPrincipal;

    /** Seuil d'alerte global du produit (peut être surchargé par site dans StockParSite). */
    private double seuilAlerte;

    /** Prix unitaire en FCFA (entier). */
    private long prixUnitaire;

    @JsonIgnore
    private byte[] photo;
    private String photoNom;
    private String photoMimeType;

    @JsonIgnore
    private byte[] ficheTechnique;
    private String ficheTechniqueNom;
    private String ficheTechniqueMimeType;

    @Indexed
    private boolean actif;
    private String remarque;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
