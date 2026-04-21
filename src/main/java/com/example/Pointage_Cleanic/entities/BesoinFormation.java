package com.example.Pointage_Cleanic.entities;

import com.example.Pointage_Cleanic.Enum.PrioriteBesoin;
import com.example.Pointage_Cleanic.Enum.SourceBesoin;
import com.example.Pointage_Cleanic.Enum.StatutBesoin;
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
@Document(collection = "besoins_formation")
public class BesoinFormation {

    @Id
    private String id;

    @Indexed
    private String employeId;

    private String matricule;
    private String nom;
    private String prenom;
    private String departement;

    private String competenceLacune;
    private PrioriteBesoin priorite;
    private String formationSuggereId;
    private SourceBesoin source;
    private StatutBesoin statut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateIdentification;
}