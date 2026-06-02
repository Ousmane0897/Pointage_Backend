package com.example.Pointage_Cleanic.entities.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Produit consommé durant une intervention. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProduitUtilise {
    private String nom;
    private Double quantite;
    private String unite;
    private String reference;
}