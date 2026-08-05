package com.example.Pointage_Cleanic.exception;

/**
 * Demande de congé invalide sur le fond : motif de refus trop court, dates incohérentes,
 * compte non rattaché à un dossier employé… Traduite en <b>422</b> par le
 * {@code GlobalExceptionHandler} — le front affiche le message tel quel.
 */
public class CongeInvalideException extends RuntimeException {

    public CongeInvalideException(String message) {
        super(message);
    }
}
