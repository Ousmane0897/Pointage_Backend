package com.example.Pointage_Cleanic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleDisabledException(DisabledException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getMessage());
        return errorResponse;
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleUserNotFoundException(UsernameNotFoundException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getMessage());
        return errorResponse;
    }


    @ExceptionHandler(EmployeAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmployeExists(EmployeAlreadyExistsException ex) {

        Map<String, Object> body = new HashMap<>();
        body.put("error", "EMPLOYE_EXISTS");
        body.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailExists(EmailAlreadyExistsException ex) {

        Map<String, Object> body = new HashMap<>();
        body.put("error", "EMAIL_EXISTS");
        body.put("message", ex.getMessage()); // 👈 C’est ce message qui sera lu par Angular

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "VALIDATION_ERROR");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        Map<String, String> body = new HashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }

    @ExceptionHandler(jakarta.mail.MessagingException.class)
    public ResponseEntity<Map<String, String>> handleMessaging(jakarta.mail.MessagingException ex) {
        Map<String, String> body = new HashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {

        Map<String, Object> body = new HashMap<>();
        body.put("error", "NOT_FOUND");
        body.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(BulkInsertPartialFailureException.class)
    public ResponseEntity<Map<String, Object>> handleBulkInsertPartialFailure(
            BulkInsertPartialFailureException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "BULK_INSERT_PARTIAL_FAILURE");
        body.put("message", ex.getMessage());
        body.put("idsDejaInseres", ex.getIdsDejaInseres());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(StockChimieInsuffisantException.class)
    public ResponseEntity<Map<String, Object>> handleStockChimieInsuffisant(StockChimieInsuffisantException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "STOCK_CHIMIE_INSUFFISANT");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(TransitionOfInterditeException.class)
    public ResponseEntity<Map<String, Object>> handleTransitionOfInterdite(TransitionOfInterditeException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "TRANSITION_OF_INTERDITE");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ControleQualiteInvalideException.class)
    public ResponseEntity<Map<String, Object>> handleControleQualiteInvalide(ControleQualiteInvalideException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "CONTROLE_QUALITE_INVALIDE");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(EntiteReferenceeException.class)
    public ResponseEntity<Map<String, Object>> handleEntiteReferencee(EntiteReferenceeException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "ENTITE_REFERENCEE");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ProductionException.class)
    public ResponseEntity<Map<String, Object>> handleProductionGeneric(ProductionException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "PRODUCTION_ERROR");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(StockConflitException.class)
    public ResponseEntity<Map<String, Object>> handleStockConflit(StockConflitException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "STOCK_CONFLICT");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Le handler générique {@code RuntimeException → 500} capture tout ce qui n'est pas déclaré
     * ici : sans cette entrée, le refus de suppression définitive sortirait en 500.
     */
    @ExceptionHandler(StockAccesRefuseException.class)
    public ResponseEntity<Map<String, Object>> handleStockAccesRefuse(StockAccesRefuseException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "STOCK_ACCES_REFUSE");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(StockOperationException.class)
    public ResponseEntity<Map<String, Object>> handleStockOperation(StockOperationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "STOCK_OPERATION_ERROR");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "" : fe.getDefaultMessage()
                ))
                .collect(Collectors.toList());

        Map<String, Object> body = new HashMap<>();
        body.put("error", "VALIDATION_ERROR");
        body.put("message", "Validation des champs en erreur");
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }


    // Optionnel : gestion d'autres exceptions
}

