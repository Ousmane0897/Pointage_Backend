package com.example.Pointage_Cleanic.exception;

/**
 * L'appelant n'est pas habilité à la gestion des comptes utilisateurs.
 * Traduite en <b>403</b> par le {@code GlobalExceptionHandler}.
 */
public class CompteAccesRefuseException extends RuntimeException {

    public CompteAccesRefuseException(String message) {
        super(message);
    }
}
