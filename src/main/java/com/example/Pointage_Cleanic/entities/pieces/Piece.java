package com.example.Pointage_Cleanic.entities.pieces;

import com.example.Pointage_Cleanic.Enum.pieces.StatutPiece;

/**
 * Document porteur d'un bloc {@link PieceBase} (lot 1 / 1b, décision D4). Implémenté par les
 * pièces portées : bons d'entrée et de sortie, inventaires.
 */
public interface Piece {

    String getId();

    PieceBase getPiece();

    /** Correspondance du statut métier de la pièce vers le cycle commun. */
    StatutPiece statutPiece();

    /** Site de rattachement, pour le registre (CDC règle 4 : le site est l'axe commun). */
    String siteIdPiece();

    /** Montant en FCFA, pour le registre (0 si la pièce n'en porte pas). */
    long montantPiece();

    /** Numéro affiché : le numéro attribué, sinon {@code BROUILLON-<6 derniers caractères de l'id>}. */
    default String libelleNumero() {
        PieceBase p = getPiece();
        if (p != null && p.getNumero() != null) {
            return p.getNumero();
        }
        String id = getId();
        if (id == null) {
            return "BROUILLON";
        }
        return "BROUILLON-" + (id.length() <= 6 ? id : id.substring(id.length() - 6));
    }
}
