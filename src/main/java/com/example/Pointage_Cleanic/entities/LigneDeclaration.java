package com.example.Pointage_Cleanic.entities;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneDeclaration {

    private String employeId;
    private String matricule;
    private String nom;
    private String prenom;
    private String numeroIpres;
    private String numeroCss;

    private Long brutImposable;
    private Long assietteIpres;
    private Long cotisationIpresSalarie;
    private Long cotisationIpresEmployeur;
    private Long assietteCss;
    private Long cotisationCssSalarie;
    private Long cotisationCssEmployeur;
    private Long impotRevenu;
    private Long trimf;

    private Integer joursTravailles;
}