package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Bon d'entrée (7.4). Document de workflow ; ne touche au stock qu'à la validation (EFFECTIF). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_bons_entree")
public class BonEntree {

    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String reference;

    private TypeEntree type;
    private LocalDate date;

    private String siteDestinationId;
    private String siteDestinationNom;

    private String fournisseur;
    private String referenceCommande;

    @Builder.Default
    private List<LigneBon> lignes = new ArrayList<>();

    @Builder.Default
    private StatutBon statut = StatutBon.BROUILLON;

    private String demandeurId;
    private String demandeurNom;

    /**
     * Auteur du bon, renseigné <b>serveur</b> depuis le JWT à la création et jamais accepté du
     * client. Gouverne les habilitations (modifier / supprimer / soumettre), contrairement au
     * demandeur, choisi dans le formulaire.
     */
    private String creeParId;
    private String creeParEmail;
    private String creeParNom;
    private String validateurId;
    private String validateurNom;

    private String commentaire;
    private String motifRefus;

    @Builder.Default
    private List<EntreeHistorique> historique = new ArrayList<>();

    private long montantTotal;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
