package com.example.Pointage_Cleanic.entities.besoins;


import com.example.Pointage_Cleanic.Enum.StatutCommande;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "collectes_besoins")
public class CollecteBesoins {

    @Id
    private String id;

    private String destination; // agence, chantier, etc.
    private String responsable; // nom du gérant ou du demandeur

    //@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private String dateDemande;

    private StatutCommande statut;

    @Builder.Default
    private int nombreModifications = 0;
    private String dateLivraison;
    private String heureLivraison;
    private List<BesoinProduit> produitsDemandes; // Liste des produits et quantités demandées
    private List<BesoinProduit> anciensProduitsDemandes; // Stocke les produits et quatités d'origine pour que le superadmin sache les données collectées par le superviseur.

    private List<String> historiqueModifications;

    private String moisActuel;
}
