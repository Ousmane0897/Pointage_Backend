package com.example.Pointage_Cleanic.exception;

import java.util.Collections;
import java.util.List;

/**
 * Levée lorsqu'un batch d'insertion échoue à mi-parcours et que l'on ne peut pas
 * garantir l'atomicité (MongoDB standalone, sans transactions multi-documents).
 * <p>
 * Expose la liste des IDs déjà persistés en base avant l'échec pour que l'appelant
 * puisse décider d'un nettoyage manuel si nécessaire.
 */
public class BulkInsertPartialFailureException extends RuntimeException {

    private final List<String> idsDejaInseres;

    public BulkInsertPartialFailureException(String message, List<String> idsDejaInseres, Throwable cause) {
        super(message, cause);
        this.idsDejaInseres = idsDejaInseres == null
                ? Collections.emptyList()
                : List.copyOf(idsDejaInseres);
    }

    public List<String> getIdsDejaInseres() {
        return idsDejaInseres;
    }
}