package com.example.Pointage_Cleanic.exception;

/**
 * Pointage refusé par la garde anti-triche du kiosque : téléphone utilisé par un autre agent
 * dans le délai, ou double envoi du même agent.
 *
 * <p>Traduite en <b>429</b> {@code { error, message }} par le {@code GlobalExceptionHandler}, le
 * message étant rédigé pour l'agent. ⚠ Le handler explicite est <b>indispensable</b> : sans lui,
 * le handler {@code RuntimeException} rendrait 500 en silence (même piège que
 * {@code JourFerieConflitException}).
 */
public class PointageTropFrequentException extends RuntimeException {
    public PointageTropFrequentException(String message) {
        super(message);
    }
}
