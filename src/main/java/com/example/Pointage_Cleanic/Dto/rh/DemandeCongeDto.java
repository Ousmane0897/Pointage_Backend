package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.TypeConge;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DemandeCongeDto {

    private String id;
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
    private StatutDemande statut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDemande;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDecision;

    private String decideurId;
    private String decideurNom;
    private String commentaireDecision;
}