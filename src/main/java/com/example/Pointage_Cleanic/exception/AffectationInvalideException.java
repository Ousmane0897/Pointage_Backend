package com.example.Pointage_Cleanic.exception;

/**
 * Écriture qui porterait atteinte à l'historique des affectations d'un employé :
 * disparition d'une affectation déjà close. Traduite en <b>422</b> par le
 * {@code GlobalExceptionHandler} — le front affiche le message tel quel.
 * <p>
 * Distincte d'{@code IllegalArgumentException} (→ 400), qui reste réservée aux
 * incohérences de saisie d'une affectation (horaires inversés, sortie avant entrée) :
 * ici le payload est bien formé, c'est la règle métier qui refuse la perte.
 */
public class AffectationInvalideException extends RuntimeException {

    public AffectationInvalideException(String message) {
        super(message);
    }
}
