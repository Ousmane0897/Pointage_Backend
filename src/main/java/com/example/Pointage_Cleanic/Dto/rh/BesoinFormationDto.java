package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.PrioriteBesoin;
import com.example.Pointage_Cleanic.Enum.rh.SourceBesoin;
import com.example.Pointage_Cleanic.Enum.rh.StatutBesoin;
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
public class BesoinFormationDto {

    private String id;

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