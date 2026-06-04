package com.example.Pointage_Cleanic.Dto.rh;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoldeCongeDto {

    private String employeId;
    private String matricule;
    private String nom;
    private String prenom;
    private String departement;
    private int anneeReference;
    private int acquis;
    private int pris;
    private int enCours;
    private int solde;
}