package com.example.Pointage_Cleanic.entities.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutDeclaration;
import com.example.Pointage_Cleanic.Enum.rh.TypeDeclaration;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "declarations_sociales")
public class DeclarationSociale {

    @Id
    private String id;

    private TypeDeclaration type;
    private String libelle;

    private Integer mois;
    private int annee;

    @Builder.Default
    private List<LigneDeclaration> lignes = new ArrayList<>();

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
