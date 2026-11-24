package com.example.Pointage_Cleanic.exception;

/**
 * Une transaction a perdu, plusieurs fois de suite, la course contre d'autres écritures sur les
 * mêmes documents (conflit d'écriture MongoDB). Rien n'a été écrit ; le client peut réessayer.
 * → 409 {@code CONFLIT_CONCURRENT}.
 */
public class ConflitConcurrentException extends RuntimeException {
    public ConflitConcurrentException(String message, Throwable cause) {
        super(message, cause);
    }
}
