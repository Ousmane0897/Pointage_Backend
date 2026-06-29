package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ligne d'un bon (entrée ou sortie). Le client n'envoie que {@code produitId} et {@code quantite} ;
 * les autres champs sont dénormalisés/figés à la création ou à la modification du bon.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LigneBon {
    private String produitId;
    private double quantite;

    // Dénormalisations figées (prix courant du produit au moment de la saisie)
    private String produitCode;
    private String produitLibelle;
    private UniteStock unite;
    private long prixUnitaire;
    private long montant;
}
