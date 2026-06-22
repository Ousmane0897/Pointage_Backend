package com.example.Pointage_Cleanic.exception;

/**
 * Erreur métier d'une opération de stock qui doit être rejetée en 422 UNPROCESSABLE_ENTITY :
 * stock insuffisant (SORTIE), transition d'inventaire invalide, écart non justifié, etc.
 */
public class StockOperationException extends RuntimeException {
    public StockOperationException(String message) {
        super(message);
    }
}
