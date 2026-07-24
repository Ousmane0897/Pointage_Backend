package com.example.Pointage_Cleanic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Règle métier de formulation violée (ex. plusieurs lignes de complément « qsp »).
 * Requête bien formée mais sémantiquement invalide → 422.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class FormulationInvalideException extends RuntimeException {
    public FormulationInvalideException(String message) {
        super(message);
    }
}
