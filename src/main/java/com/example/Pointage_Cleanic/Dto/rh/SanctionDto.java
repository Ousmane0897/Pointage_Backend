package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutSanction;
import com.example.Pointage_Cleanic.Enum.rh.TypeSanction;
import com.example.Pointage_Cleanic.entities.rh.PieceJointeSanction;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SanctionDto {

    private String id;

    private String employeId;
    private String matricule;
    private String nom;
    private String prenom;
    private String departement;

    private TypeSanction type;
    private String motif;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFaits;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateSanction;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateConvocation;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEntretien;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateNotification;

    private Integer delaiRespectJours;

    private Integer dureeMiseAPied;

    @Builder.Default
    private List<PieceJointeSanction> piecesJointes = new ArrayList<>();

    private StatutSanction statut;
    private String commentaire;

    private String creeParId;
    private String creeParNom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateCreation;
}