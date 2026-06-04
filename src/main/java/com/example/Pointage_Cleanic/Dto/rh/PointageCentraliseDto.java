package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointageCentraliseDto {

    private String id;
    private String employeId;
    private String matricule;
    private String nom;
    private String prenom;
    private String departement;
    private String site;
    private String poste;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String heureArrivee;
    private String heureDepart;
    private Integer dureeMinutes;
    private Integer retardMinutes;
    private String statut;
    private String motif;
}