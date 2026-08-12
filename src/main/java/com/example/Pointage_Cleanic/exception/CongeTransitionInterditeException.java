package com.example.Pointage_Cleanic.exception;

/**
 * Transition impossible sur une demande de congé : statut déjà terminal, ou niveau
 * courant différent de celui que l'appelant tente de trancher (course entre deux
 * validateurs d'un même niveau). Traduite en <b>409</b> par le
 * {@code GlobalExceptionHandler}.
 */
public class CongeTransitionInterditeException extends RuntimeException {

    public CongeTransitionInterditeException(String message) {
        super(message);
    }
}
