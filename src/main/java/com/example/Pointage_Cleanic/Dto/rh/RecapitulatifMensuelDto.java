package com.example.Pointage_Cleanic.Dto.rh;

import lombok.*;

/**
 * Agrégat mensuel dérivé (lecture seule) consommé par le sous-module RH 6.2
 * « Temps & Présences » du frontend, exposé via {@code GET /api/temps-presences/recapitulatif}.
 * Combine pointages (présences + retards), absences/congés et heures supplémentaires.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecapitulatifMensuelDto {

    private String employeId;
    private String matricule;
    private String nom;
    private String prenom;
    private String departement;
    private String poste;
    private int mois;
    private int annee;

    private int joursOuvrables;
    private int joursTravailles;
    private int joursAbsence;
    private int joursConge;

    private int nombreRetards;
    private int minutesRetardTotal;

    private double heuresSupTotal;
    private double heuresSupMajoreesEquivalent;
    private HeuresSupParTypeDto heuresSupParType;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HeuresSupParTypeDto {
        private double t15;
        private double t40;
        private double t60;
        private double t100;
    }
}
