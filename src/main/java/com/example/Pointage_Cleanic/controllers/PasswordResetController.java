package com.example.Pointage_Cleanic.controllers;


import com.example.Pointage_Cleanic.services.ResetPasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    static final String MESSAGE_DEMANDE =
            "Si un compte correspond à cette adresse, un code de réinitialisation vient d'y être envoyé.";

    private final ResetPasswordService resetService;

    /** Même réponse que l'e-mail corresponde à un compte ou non (pas d'énumération des comptes). */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        resetService.sendResetPasswordEmail(request.get("email"));
        return ResponseEntity.ok(Map.of("message", MESSAGE_DEMANDE));
    }

    /** Corps : {@code {email, code, newPassword}}. */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            resetService.resetPassword(request.get("email"), request.get("code"), request.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé avec succès"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage()));
        }
    }
}
