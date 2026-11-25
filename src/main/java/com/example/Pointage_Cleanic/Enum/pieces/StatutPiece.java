package com.example.Pointage_Cleanic.Enum.pieces;

/**
 * Cycle de vie commun d'une pièce (lot 1 / 1b, décision D4) :
 * {@code BROUILLON → SOUMISE → VALIDEE}, branches {@code REFUSEE}, {@code ANNULEE} (pièce
 * validée, annulation tracée) et {@code ABANDONNEE} (brouillon conservé, jamais supprimé).
 *
 * <p>Chaque pièce garde son propre statut métier (contrat d'API du front) ; ce statut commun en
 * est la correspondance, utilisée par le registre des pièces.
 */
public enum StatutPiece {
    BROUILLON,
    SOUMISE,
    VALIDEE,
    REFUSEE,
    ANNULEE,
    ABANDONNEE
}
