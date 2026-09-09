package com.example.Pointage_Cleanic.exception;

/**
 * Deux jours fériés ne peuvent pas partager la même date.
 *
 * <p>Traduite en <b>409</b> par le {@code GlobalExceptionHandler}. ⚠ Le handler explicite est
 * <b>indispensable</b> : {@code GlobalExceptionHandler} intercepte {@code RuntimeException} et
 * rendrait 500 en silence, une annotation {@code @ResponseStatus} n'étant consultée qu'en
 * l'absence de handler correspondant. Même piège que {@code AffectationInvalideException}.
 */
public class JourFerieConflitException extends RuntimeException {
    public JourFerieConflitException(String message) {
        super(message);
    }
}
