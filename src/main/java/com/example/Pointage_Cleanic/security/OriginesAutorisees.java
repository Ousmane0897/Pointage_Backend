package com.example.Pointage_Cleanic.security;

import java.util.List;

/**
 * Origines navigateur autorisées, partagées par le CORS HTTP ({@link SecurityConfig}) et le
 * point d'entrée WebSocket ({@code WebSocketConfig}) : une seule liste à tenir à jour.
 *
 * <p>Ce sont des <b>motifs</b> ({@code setAllowedOriginPatterns}) : {@code *} n'est accepté que là
 * où il est écrit (port local). Avant le lot 0, la liste passait par {@code setAllowedOrigins},
 * qui ignore les jokers : {@code http://127.0.0.1:*} et {@code https://*.ngrok-free.dev}
 * n'autorisaient en réalité rien. Le joker ngrok n'a pas été réactivé (il ouvrirait le CORS à
 * n'importe quel tunnel ngrok) : seul le sous-domaine nommé reste autorisé.
 */
public final class OriginesAutorisees {

    private OriginesAutorisees() {
    }

    public static final List<String> MOTIFS = List.of(
            "https://pointic-cleanic.com",
            "https://www.pointic-cleanic.com",
            "https://app.pointic-cleanic.com",
            "http://localhost",
            "http://localhost:4200",
            "http://127.0.0.1:*",
            "https://sizably-unhonored-bryce.ngrok-free.dev"
    );
}
