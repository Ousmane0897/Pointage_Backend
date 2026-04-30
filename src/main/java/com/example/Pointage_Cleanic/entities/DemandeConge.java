package com.example.Pointage_Cleanic.entities;

import com.example.Pointage_Cleanic.Enum.StatutDemande;
import com.example.Pointage_Cleanic.Enum.TypeConge;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "conges")
public class DemandeConge {

    @Id
    private String id;

    @Indexed
    private String employeId;

    private String matricule;
    private String nom;
    private String prenom;
    private String departement;

    private TypeConge type;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private Integer nombreJours;
    private String motif;

    @Builder.Default
    private StatutDemande statut = StatutDemande.EN_ATTENTE;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDemande;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDecision;

    private String decideurId;
    private String decideurNom;
    private String commentaireDecision;
}