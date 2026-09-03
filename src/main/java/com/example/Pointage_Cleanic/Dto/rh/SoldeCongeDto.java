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

    /**
     * Reliquat cumulé des exercices antérieurs, <b>déjà inclus dans {@link #solde}</b> :
     * l'additionner au solde compterait les jours deux fois.
     */
    private int soldeAnterieur;

    /** Mois de service effectif comptés sur l'exercice — explique d'où sort {@link #acquis}. */
    private int moisAcquis;

    private int acquis;
    private int pris;
    private int enCours;
    private int solde;
}