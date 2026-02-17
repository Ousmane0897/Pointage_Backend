package com.example.Pointage_Cleanic.Dto;


import lombok.*;

import java.math.BigDecimal;

@Setter
@Getter
public class ProduitDto {

    private String codeProduit; // Identifiant business (SKU) ou  Identifiant unique interne
    private String nomProduit;
    private String description; // détails utiles, composition du produit, etc.
    private String categorie;  // ex : Alimentaire, Informatique, Médicaments
    private String[] destination; // usage prévu: vente ou/et agence
    // L'image sera stockée en base sous forme de bytes
    private byte[] image;
    //private String sousCategorie; // ex : Boissons > Jus, ou Médicament > Antibiotique
    private String uniteDeMesure; // ex: pièce, kg, litre
    private String conditionnement; // ex : carton de 12 pièces, pack de 6 bouteilles
    // Informations commerciales (optionnelles)
    private double prixDeVente; // concernant les produits qui sont vendus
    private String emplacement; // Détermine l'emplacement du produit dans l'entrepot
    // Seuils et flags
    private Integer seuilMinimum; // déclenche alerte/réapprovisionnement
    private String statut; // actif - en attente - bloqué pour contrôle qualité
    private Integer quantiteSnapshot;

}
