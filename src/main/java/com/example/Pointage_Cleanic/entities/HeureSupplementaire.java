package com.example.Pointage_Cleanic.entities;

import com.example.Pointage_Cleanic.Enum.StatutValidationHS;
import com.example.Pointage_Cleanic.Enum.TypeMajoration;
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
@Document(collection = "heures_supplementaires")
public class HeureSupplementaire {

    @Id
    private String id;

    @Indexed
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

    @Builder.Default
    private StatutValidationHS statut = StatutValidationHS.EN_ATTENTE;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDeclaration;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDecision;

    private String decideurId;
    private String decideurNom;
    private String commentaireDecision;
}