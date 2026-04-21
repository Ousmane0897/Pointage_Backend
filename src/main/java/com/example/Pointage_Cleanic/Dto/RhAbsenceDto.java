package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.Enum.StatutAbsence;
import com.example.Pointage_Cleanic.Enum.TypeAbsence;
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
public class RhAbsenceDto {

    private String id;
    private String employeId;
    private String matricule;
    private String nom;
    private String prenom;
    private String departement;
    private TypeAbsence type;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private Integer nombreJours;
    private String motif;
    private StatutAbsence statut;
    private PieceJustificativeDto pieceJustificative;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateCreation;

    private String creeParId;
}