package com.example.Pointage_Cleanic.entities.stock;



import com.example.Pointage_Cleanic.Enum.MotifMouvementEntreeStock;
import com.example.Pointage_Cleanic.Enum.TypeMouvement;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "mouvements_stock")
public class MouvementEntreeStock {

    /*
    *MouvementStock est le journal (ledger) des modifications de quantité pour un produit.
    * Chaque fois qu’un produit entre, sort, est ajusté, transféré ou perdu, on enregistre un MouvementStock.
    * C’est la source de vérité qui permet : audit, calcul du stock théorique, réconciliation d’inventaire, reporting, et diagnostics.
    * */


    @Id
    private String id;


    //private String produitId; // lien vers Produit.id
    private String codeProduit;
    private String nomProduit;
    private TypeMouvement type; // ENTREE, AJUSTEMENT
    private Integer quantite; // toujours positive


    // Métadonnées métier
    //private String source; // ex: BON_LIVRAISON-123
    //private String reference; // facture, commande, bon de sortie
    private String responsable; // utilisateur qui a réalisé l'opération
    private MotifMouvementEntreeStock motifMouvement; // ex: "Ajustement", "Réception fournisseur"


    // Optionnels utiles
    private String fournisseur; // id ou nom fournisseur (utile pour ENTREE)
    private String numeroFacture; // utile pour ENTREE
    //private String warehouse; // entrepot/localisation logique
    //private String lotNumber; // lot/numéro de série si applicable
    //private String destination; // En cas de livraison de produits dans les agences ou chantiers ou vente
    //private String mois; // Le mois est surtout utile dans le cas ou on doit rassembler les mouvements ou les sorties de produits vers une agence spécifique pour un mois donné.

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant dateMouvement;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant dateDePeremption;

    // Optionnel: coût unitaire à la réception pour valorisation
    //private BigDecimal costUnit;
}
