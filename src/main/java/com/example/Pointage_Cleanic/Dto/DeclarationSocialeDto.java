package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.Enum.StatutDeclaration;
import com.example.Pointage_Cleanic.Enum.TypeDeclaration;
import com.example.Pointage_Cleanic.entities.LigneDeclaration;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeclarationSocialeDto {

    private String id;

    private TypeDeclaration type;
    private String libelle;

    private Integer mois;
    private int annee;

    private List<LigneDeclaration> lignes;

    private Long totalBrut;
    private Long totalIpresSalarie;
    private Long totalIpresEmployeur;
    private Long totalCssSalarie;
    private Long totalCssEmployeur;
    private Long totalIr;
    private Long totalTrimf;
    private Long totalPayable;

    private int effectif;

    private StatutDeclaration statut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateGeneration;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateTransmission;

    private String referenceExterne;
    private String commentaire;
}