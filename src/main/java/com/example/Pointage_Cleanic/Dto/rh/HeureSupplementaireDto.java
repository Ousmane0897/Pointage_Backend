package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutValidationHS;
import com.example.Pointage_Cleanic.Enum.rh.TypeMajoration;
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
public class HeureSupplementaireDto {

    private String id;
    private String employeId;
    private String matricule;
    private String nom;
    private String prenom;
    private String departement;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String heureDebut;
    private String heureFin;
    private Double nombreHeures;
    private TypeMajoration typeMajoration;
    private Integer tauxMajoration;
    private String motif;
    private Double heuresMajoreesEquivalent;
    private StatutValidationHS statut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDeclaration;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDecision;

    private String decideurId;
    private String decideurNom;
    private String commentaireDecision;
}