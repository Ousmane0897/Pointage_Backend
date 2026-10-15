package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * Jours de congé approuvé tombant sur un jour ouvrable <b>de ce mois</b>, hors repos
     * médical (compté à part) et hors absence non justifiée (comptée en absence).
     */
    private int joursConge;

    /**
     * Jours de repos médical approuvé tombant sur un jour ouvrable du mois. Ils ne sont
     * <b>pas</b> des absences — c'est précisément ce que la RH doit pouvoir contrôler.
     */
    private int joursReposMedical;

    /**
     * Minutes réellement travaillées : somme des durées arrivée → départ des créneaux
     * pointés (hors planning compris). Un créneau sans départ n'y entre pas — voir
     * {@link #creneauxSansDepart}.
     */
    private int minutesTravaillees;

    /** Créneaux pointés sans départ : leurs heures sont inconnues, jamais inventées. */
    private int creneauxSansDepart;

    /** Pointages qu'aucun créneau du planning n'explique (même notion que la vue jour). */
    private int horsPlan;

    /** Code de pointage de l'agent (4 chiffres). */
    private String agentId;

    /** Date d'attribution du code de pointage. */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateCreationAgentId;

    /** Vrai quand la date a été reconstituée (date de création du dossier), pas mesurée. */
    private boolean dateCreationAgentIdEstimee;

    /**
     * Détail par site, une entrée par site où l'agent avait au moins un créneau ce mois-ci,
     * plus une entrée « Hors planning » si des pointages n'ont pu être rattachés. Les
     * minutes et retards des sites s'additionnent pour donner ceux de l'agent ; ⚠ pas les
     * jours travaillés : un jour passé sur deux sites ne compte qu'une fois pour l'agent.
     */
    private List<RecapSiteDto> sites;

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

    /** Ligne « agent × site » du récapitulatif. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecapSiteDto {
        private String site;
        /** Vrai pour la ligne des pointages qu'aucun créneau n'explique. */
        private boolean horsPlanning;
        /** Plages horaires rencontrées sur le mois (« 06:00–10:00, 15:00–19:00 »). */
        private String horaires;
        /** Créneaux attendus (fériés compris, congés exclus). */
        private int creneauxPrevus;
        /** Jours distincts où au moins un créneau de ce site a été pointé. */
        private int joursTravailles;
        private int minutesTravaillees;
        private int creneauxSansDepart;
        /** Créneaux passés non pointés (hors fériés et congés). */
        private int creneauxAbsents;
        /** Jours distincts comptant au moins un créneau absent sur ce site. */
        private int joursAbsence;
        /** Créneaux au-delà de la tolérance de retard. */
        private int nombreRetards;
        /** Retard brut cumulé de ces seuls créneaux. */
        private int minutesRetardTotal;
    }

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
