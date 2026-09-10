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

    /**
     * Jours ouvrables <b>de cet employé</b> : rythme du site, jour de repos hebdomadaire et
     * jours fériés compris. Ce n'est donc pas la même valeur pour tous — un agent de terrain
     * en {@code LUN_SAM} en a davantage qu'un back-office.
     */
    private int joursOuvrables;

    /**
     * Jours du mois effectivement pointés, <b>fériés compris</b> : le travail réellement
     * effectué doit se voir. ⚠ Peut donc dépasser {@link #joursOuvrables} quand un férié a
     * été travaillé — tout calcul de ratio doit plafonner à 1.
     */
    private int joursTravailles;

    /**
     * Jours dus et non honorés : {@code joursOuvrables} moins l'union des jours pointés et
     * des jours de congé. ⚠ Ne se déduit pas de {@code joursOuvrables - joursTravailles -
     * joursConge} — ces trois compteurs ne portent pas sur le même ensemble de jours.
     */
    private int joursAbsence;

    /** Jours de congé approuvé tombant sur un jour ouvrable <b>de ce mois</b>. */
    private int joursConge;

    /**
     * Jours fériés du mois qui seraient tombés un jour travaillé par cet employé — un férié
     * tombant son jour de repos ne lui fait rien gagner et n'est pas compté ici.
     */
    private int joursFeries;

    /** Parmi les précédents, ceux qu'il a effectivement travaillés. */
    private int joursTravaillesFeries;

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
