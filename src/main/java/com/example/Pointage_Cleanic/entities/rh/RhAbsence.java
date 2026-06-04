package com.example.Pointage_Cleanic.entities.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutAbsence;
import com.example.Pointage_Cleanic.Enum.rh.TypeAbsence;
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
@Document(collection = "rh_absences")
public class RhAbsence {

    @Id
    private String id;

    @Indexed
    private String employeId;

    private String matricule;
    private String nom;
    private String prenom;
    private String departement;

    private TypeAbsence type;

    // Précision libre obligatoire quand type = AUTRE, nullable sinon
    private String typeAutrePrecision;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private Integer nombreJours;
    private String motif;

    @Builder.Default
    private StatutAbsence statut = StatutAbsence.DECLAREE;

    private PieceJustificative pieceJustificative;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateCreation;

    private String creeParId;
}