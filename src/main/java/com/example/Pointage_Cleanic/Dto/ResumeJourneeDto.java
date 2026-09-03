package com.example.Pointage_Cleanic.Dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

/**
 * Résumé d'une journée de pointage.
 *
 * <p><b>Deux unités cohabitent, ne pas les additionner.</b> {@code totalEmployes} et
 * {@code enConge} comptent des <i>personnes</i> ; tous les autres compteurs comptent des
 * <i>créneaux</i> (employé × site attendu). Un agent en retard sur deux sites pèse deux
 * fois dans {@code retards} mais une seule dans {@code totalEmployes}.
 *
 * <p>L'invariant utile est donc :
 * {@code presents + retards + absents + enAttente + neutres == creneauxPrevus}.
 * L'ancien {@code presents + absents + retards + enConge == totalEmployes} était déjà
 * faux pour les agents multi-pointages : il est abandonné, pas réparé.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeJourneeDto {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** Effectif actif distinct (personnes). */
    private int totalEmployes;

    /** Créneaux attendus ce jour — dénominateur des compteurs de créneaux. */
    private int creneauxPrevus;

    private int presents;
    private int absents;
    private int retards;

    /** Créneau commencé, agent pas encore pointé. */
    private int enAttente;

    /** Créneau pas encore commencé — ni présent, ni absent. */
    private int neutres;

    /**
     * Pointages qu'aucun créneau attendu n'explique. Compteur d'alerte : il signale des
     * affectations non tenues à jour. Les noyer dans {@code absents} — ce que faisait la
     * branche par défaut de l'ancien comptage — est ce qui a masqué le problème.
     */
    private int horsPlan;

    /** Employés en congé approuvé (personnes : une seule ligne congé par employé). */
    private int enConge;
}