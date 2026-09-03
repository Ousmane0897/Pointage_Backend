package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

/**
 * Une ligne de la vue « pointage centralisé ». Depuis le rattachement des horaires au
 * site, une ligne est un <b>créneau</b> (employé × jour × site attendu), et non plus un
 * enregistrement de pointage : un agent affecté à deux sites produit deux lignes par
 * jour, chacune évaluée sur l'horaire de son propre site.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointageCentraliseDto {

    /**
     * Identifiant <b>du créneau</b>, synthétique et stable : il ne change pas quand
     * l'agent finit par pointer. C'est ce qui permet au {@code trackBy} Angular de ne
     * pas détruire puis recréer la ligne à chaque rafraîchissement. L'identifiant réel
     * du pointage est porté à part par {@link #pointageId}.
     */
    private String id;

    private String employeId;
    private String matricule;
    private String nom;
    private String prenom;
    private String departement;
    /** Site du créneau ; sur une ligne hors planning, les sites du pointage. */
    private String site;
    private String poste;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String heureArrivee;
    private String heureDepart;
    private Integer dureeMinutes;
    /** Retard <b>brut</b> en minutes ; la tolérance n'en est pas déduite. */
    private Integer retardMinutes;
    private String statut;
    private String motif;

    /** Horaire attendu sur ce site, "HH:mm" — permet d'afficher « prévu 08:00 ». */
    private String siteHoraireDebut;
    private String siteHoraireFin;

    /** {@code _id} du pointage rattaché, {@code null} tant que l'agent n'a pas pointé. */
    private String pointageId;

    /**
     * {@code false} sur une ligne hors planning (un pointage qu'aucun créneau attendu
     * n'explique). Distinguer ces lignes évite de les confondre avec une présence
     * normale, et c'est le symptôme direct d'affectations non tenues à jour.
     */
    private boolean planifie;
}