package com.example.Pointage_Cleanic.exception;

import java.io.Serial;

/**
 * Conflit métier du module Terrain (code dupliqué, conflit de planning,
 * transition d'état illégale). Mappée en HTTP 409 par {@code TerrainExceptionHandler}.
 */
public class TerrainConflitException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TerrainConflitException(String message) {
        super(message);
    }
}