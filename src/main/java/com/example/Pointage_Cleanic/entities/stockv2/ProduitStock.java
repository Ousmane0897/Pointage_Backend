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

    /**
     * Prix unitaire en FCFA (entier). Sémantique 7.6 : « coût unitaire courant » du produit.
     * Statique si {@code methodeValorisation = FIXE} (saisi manuellement), recalculé automatiquement
     * par le serveur à chaque entrée si CUMP / DERNIER_PRIX.
     */
    private long prixUnitaire;

    // --- 7.6 Valorisation financière (champs optionnels, rétro-compatibles) ---
    /**
     * Override de la méthode de valorisation pour ce produit. {@code null} = hérite de la méthode
     * globale ({@code ParametrageValorisation.methodeDefaut}), à défaut {@code FIXE}.
     * Non éditable via le formulaire produit 7.3 : seul l'endpoint PATCH dédié l'écrit.
     */
    private com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation methodeValorisation;
    /**
     * Prix de vente unitaire en FCFA (entier), pour le calcul des marges. {@code null} si non défini.
     * Non éditable via le formulaire produit 7.3 : seul l'endpoint PATCH dédié l'écrit.
     */
    private Long prixVente;

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
