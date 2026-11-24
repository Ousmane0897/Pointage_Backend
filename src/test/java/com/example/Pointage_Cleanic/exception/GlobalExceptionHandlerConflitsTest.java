package com.example.Pointage_Cleanic.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot 1 / 1a (D1, D3) : conflits de version, conflits concurrents et doublons d'index unique
 * donnent 409 avec un code stable, et non plus 500 (gestionnaire générique RuntimeException).
 */
class GlobalExceptionHandlerConflitsTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void verrou_optimiste_donne_409_conflit_version() {
        ResponseEntity<Map<String, Object>> r =
                handler.handleConflitVersion(new OptimisticLockingFailureException("version 3 attendue"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("error", "CONFLIT_VERSION").containsKey("message");
    }

    @Test
    void conflit_concurrent_donne_409() {
        ResponseEntity<Map<String, Object>> r =
                handler.handleConflitConcurrent(new ConflitConcurrentException("réessayez", null));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("error", "CONFLIT_CONCURRENT").containsEntry("message", "réessayez");
    }

    @Test
    void doublon_donne_409_sans_divulguer_le_message_du_pilote() {
        ResponseEntity<Map<String, Object>> r = handler.handleDoublon(
                new DuplicateKeyException("E11000 duplicate key error index: matricule_unique dup key: { matricule: \"M-42\" }"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("error", "DOUBLON");
        assertThat(r.getBody().get("message").toString()).doesNotContain("M-42", "E11000");
    }
}
