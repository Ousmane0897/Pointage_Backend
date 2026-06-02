package com.example.Pointage_Cleanic.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestion des erreurs propre au module Terrain.
 * <p>
 * Scopé au package {@code controllers.terrain} avec une priorité maximale afin de
 * produire le format attendu par le frontend Terrain : {@code { message, timestamp, status }}.
 * Les autres modules continuent d'utiliser {@link GlobalExceptionHandler}
 * ({@code { error, message }}) qui reste inchangé.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.example.Pointage_Cleanic.controllers.terrain")
public class TerrainExceptionHandler {

    private Map<String, Object> body(String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        return body;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(ex.getMessage(), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " : " + (fe.getDefaultMessage() == null ? "invalide" : fe.getDefaultMessage()))
                .collect(Collectors.joining(" ; "));
        if (message.isBlank()) {
            message = "Validation des champs en erreur";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(message, HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(ex.getMessage(), HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(TerrainConflitException.class)
    public ResponseEntity<Map<String, Object>> handleConflit(TerrainConflitException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(ex.getMessage(), HttpStatus.CONFLICT));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
    }
}