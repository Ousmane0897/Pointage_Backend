package com.example.Pointage_Cleanic.exception;

/**
 * Scan de pointage refusé par une règle métier (QR non reconnu, hors zone de l'agence,
 * hors affectation en mode BLOQUER…). Traduite en <b>422</b> ; le message est destiné à
 * l'agent et affiché tel quel par le front.
 */
public class PointageRefuseException extends RuntimeException {

    public PointageRefuseException(String message) {
        super(message);
    }
}
