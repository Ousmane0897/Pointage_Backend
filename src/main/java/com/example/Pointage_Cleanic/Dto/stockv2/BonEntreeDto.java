package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BonEntreeDto {
    private String id;
    private String reference;
    private TypeEntree type;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String siteDestinationId;
    private String siteDestinationNom;
    private String fournisseur;
    private String referenceCommande;
    private List<LigneBonDto> lignes;
    private StatutBon statut;
    private String demandeurId;
    private String demandeurNom;

    /** Auteur du bon, déduit du JWT à la création (comparé à l'adresse de connexion par le front). */
    private String creeParId;
    private String creeParEmail;
    private String creeParNom;
    private String validateurId;
    private String validateurNom;
    private String commentaire;
    private String motifRefus;
    private List<HistoriqueDto> historique;
    private long montantTotal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
