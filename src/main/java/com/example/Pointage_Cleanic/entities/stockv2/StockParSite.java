package com.example.Pointage_Cleanic.entities.stockv2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Solde de stock pour un couple (produit, site). Source de vérité des quantités.
 * Le solde consolidé d'un produit = somme des quantités sur tous les sites.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_etats_stock")
public class StockParSite {

    @Id
    private String id;

    private String produitId;
    private String siteId;

    private double quantite;

    /** Surcharge de seuil propre à ce couple produit/site (null = utiliser le seuil global du produit). */
    private Double seuilAlerteOverride;

    private LocalDateTime dateMaj;
}
