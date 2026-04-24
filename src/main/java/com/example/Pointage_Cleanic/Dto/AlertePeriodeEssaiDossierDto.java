package com.example.Pointage_Cleanic.Dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertePeriodeEssaiDossierDto {

    private String id;
    private String matricule;
    private String nom;
    private String prenom;
    private String poste;
    private String departement;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEntree;

    private Integer dureeEssaiMois;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFinEssaiCalculee;

    private long joursRestants;
    private String statut;
}