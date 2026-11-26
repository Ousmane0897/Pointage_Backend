package com.example.Pointage_Cleanic.Enum.stockv2;

import com.example.Pointage_Cleanic.Enum.pieces.StatutPiece;

/**
 * Statut du workflow d'un bon : BROUILLON -> SOUMIS -> VALIDE -> EFFECTIF, ou SOUMIS -> REFUSE.
 * Lot 1 / 1b (D4) : un brouillon n'est plus supprimé mais ABANDONNE (conservé) ; un bon EFFECTIF
 * n'est plus supprimé mais ANNULE (motif tracé, mouvements inverses).
 */
public enum StatutBon {
    BROUILLON,
    SOUMIS,
    VALIDE,
    EFFECTIF,
    REFUSE,
    ANNULE,
    ABANDONNE;

    /** Correspondance vers le cycle commun des pièces (lot 1 / 1b, D4). */
    public StatutPiece statutPiece() {
        return switch (this) {
            case BROUILLON -> StatutPiece.BROUILLON;
            case SOUMIS -> StatutPiece.SOUMISE;
            case VALIDE, EFFECTIF -> StatutPiece.VALIDEE;
            case REFUSE -> StatutPiece.REFUSEE;
            case ANNULE -> StatutPiece.ANNULEE;
            case ABANDONNE -> StatutPiece.ABANDONNEE;
        };
    }
}
