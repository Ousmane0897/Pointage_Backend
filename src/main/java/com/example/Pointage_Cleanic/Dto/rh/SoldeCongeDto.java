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

    /** Mois de service effectif comptés sur l'exercice — explique d'où sort {@link #acquisBase}. */
    private int moisAcquis;

    /**
     * Droits acquis sur l'exercice, <b>toutes majorations comprises</b> :
     * {@code acquis == acquisBase + supplementEnfants + supplementAnciennete}.
     *
     * <p>Le total et non la seule base : c'est ce champ que les écrans affichent depuis
     * toujours sous le libellé « Acquis », en faire la base seule changerait silencieusement
     * leur sens. Les trois champs suivants ne servent qu'à <b>l'expliquer</b>.
     */
    private int acquis;

    /** {@code moisAcquis × jours par mois} — l'acquis avant toute majoration. */
    private int acquisBase;

    /** Majoration pour enfants de moins de N ans à charge (mères de famille). */
    private int supplementEnfants;

    /** Majoration du palier d'ancienneté atteint (paliers non cumulatifs). */
    private int supplementAnciennete;

    /** Ancienneté retenue au 31/12 de l'exercice — explique {@link #supplementAnciennete}. */
    private int anneesAnciennete;

    /** Nombre d'enfants ayant ouvert le droit — explique {@link #supplementEnfants}. */
    private int enfantsBeneficiaires;
    private int pris;
    private int enCours;
    private int solde;
}