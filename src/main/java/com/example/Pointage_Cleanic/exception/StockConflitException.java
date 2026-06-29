package com.example.Pointage_Cleanic.exception;

/**
 * Conflit métier du module Stock v2 (ex: code produit déjà utilisé) -> 409 CONFLICT.
 */
public class StockConflitException extends RuntimeException {
    public StockConflitException(String message) {
        super(message);
    }
}
