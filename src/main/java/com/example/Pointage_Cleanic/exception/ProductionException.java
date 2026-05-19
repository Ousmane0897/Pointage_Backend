package com.example.Pointage_Cleanic.exception;

public class ProductionException extends RuntimeException {
    public ProductionException(String message) {
        super(message);
    }

    public ProductionException(String message, Throwable cause) {
        super(message, cause);
    }
}