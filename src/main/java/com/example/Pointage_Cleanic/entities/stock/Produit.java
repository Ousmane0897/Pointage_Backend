package com.example.Pointage_Cleanic.entities.stock;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.util.Date;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "produits")
public class Produit {


    @Id
    private String id;


    // Référence et description
    private String codeProduit; // Identifiant business (SKU) ou  Identifiant unique interne
    private String nomProduit;
    private String description; // détails utiles, composition du produit, etc.
    private String categorie;  // ex : Alimentaire, Informatique, Médicaments
    private String destination; // usage prévu: vente, agence
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



    // Ce champ représente le stock théorique, il n'est pas directement stocké dans Produit, mais
    // calculer dynamiquement à chaque mouvement de stock du produit (Entrée,sortie, aj)
    private Integer quantiteSnapshot;














    //Description produit
    /*@Id
    private String id;
    private String codeProduit; // Identifiant unique interne
    private String nomProduit;
    private String imageProduit;
    private String statut; // actif - en attente - bloqué pour contrôle qualité
    private String categorie; // ex : Alimentaire, Informatique, Médicaments
    private String sousCategorie; // ex : Boissons > Jus, ou Médicament > Antibiotique
    private String description; // détails utiles, composition, etc.

    //Quantité & unités
    private Integer quantiteEntree;
    private String uniteDeMesure; // pièce, kg, litre, boîte, carton…
    private String conditionnement; // ex : carton de 12 pièces, pack de 6 bouteilles
    private Integer seuilMinimum; // déclenche alerte/réapprovisionnement

    //Valeur & coûts
    private BigDecimal prixUnitaire; //	Prix unitaire d’achat (HT/TTC)
    private BigDecimal prixDeVente; // concernant les produits qui sont vendus
    private String fournisseur; // id fournisseur, raison sociale, contact
    private String numeroFacture; // preuve d’entrée

    //Traçabilité & suivi
    @Field("dateEntreeEnStock")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private Date dateEntree; // Correspond à la date d'entrée du produit en stock

    @Field("datePeremption")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private Date datePeremption; // Date d'expiration du produit

    private String emplacement; // Détermine l'emplacement du produit dans l'entrepot
    private String responsableEntree; // Désigne la personne qui a validé l'opération*/



}
