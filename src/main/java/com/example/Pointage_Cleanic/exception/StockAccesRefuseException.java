package com.example.Pointage_Cleanic.exception;

/**
 * L'appelant n'est pas habilité à l'action demandée sur un document de stock
 * (suppression définitive d'un inventaire clôturé ou d'un bon déjà engagé,
 * réservée au super-administrateur).
 * <p>
 * Traduite en <b>403</b> par le {@code GlobalExceptionHandler}.
 */
public class StockAccesRefuseException extends RuntimeException {

    public StockAccesRefuseException(String message) {
        super(message);
    }
}
