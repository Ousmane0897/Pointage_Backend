package com.example.Pointage_Cleanic.exception;

/**
 * Stock insuffisant pour une sortie (lot 1 / 1a, décision D1.5) → 422 {@code STOCK_INSUFFISANT}.
 * Sous-classe de {@link StockOperationException} : tout code qui attrapait celle-ci continue de
 * l'attraper ; seul le code d'erreur de la réponse devient plus précis.
 */
public class StockInsuffisantException extends StockOperationException {
    public StockInsuffisantException(String message) {
        super(message);
    }
}
