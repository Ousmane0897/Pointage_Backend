package com.example.Pointage_Cleanic.Enum.stockv2;

import com.example.Pointage_Cleanic.Enum.pieces.StatutPiece;

/**
 * Workflow d'un inventaire : BROUILLON -> COMPTAGE -> VALIDATION -> CLOTURE. Lot 1 / 1b (D4) :
 * un brouillon s'abandonne (ABANDONNE, conservé), un inventaire clôturé s'annule (ANNULE, motif
 * tracé, ajustements contre-passés) ; plus aucune suppression.
 */
public enum StatutInventaire {
    BROUILLON,
    COMPTAGE,
    VALIDATION,
    CLOTURE,
    ANNULE,
    ABANDONNE;

    /**
     * Correspondance vers le cycle commun des pièces (lot 1 / 1b, D4) : le comptage et la
     * validation des écarts sont l'équivalent d'une pièce soumise ; la clôture la valide.
     */
    public StatutPiece statutPiece() {
        return switch (this) {
            case BROUILLON -> StatutPiece.BROUILLON;
            case COMPTAGE, VALIDATION -> StatutPiece.SOUMISE;
            case CLOTURE -> StatutPiece.VALIDEE;
            case ANNULE -> StatutPiece.ANNULEE;
            case ABANDONNE -> StatutPiece.ABANDONNEE;
        };
    }
}
