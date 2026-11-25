package com.example.Pointage_Cleanic.exception;

/**
 * Transition d'état refusée sur un objet du socle (lot 1 / 1b) : relance d'un événement qui
 * n'est pas en erreur, annulation d'une pièce déjà annulée… → 409 {@code TRANSITION_INTERDITE}.
 * Les modules dont le front attend un autre code gardent leur exception (ex.
 * {@code StockConflitException} pour les bons).
 */
public class TransitionInterditeException extends RuntimeException {
    public TransitionInterditeException(String message) {
        super(message);
    }
}
