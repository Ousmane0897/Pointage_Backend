package com.example.Pointage_Cleanic.exception;

/**
 * L'appelant n'est pas habilité à l'action demandée sur une demande de congé
 * (niveau de validation qui n'est pas le sien, dépôt pour un tiers sans être RH…).
 * Traduite en <b>403</b> par le {@code GlobalExceptionHandler}.
 */
public class CongeAccesRefuseException extends RuntimeException {

    public CongeAccesRefuseException(String message) {
        super(message);
    }
}
