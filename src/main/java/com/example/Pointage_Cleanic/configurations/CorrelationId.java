package com.example.Pointage_Cleanic.configurations;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Identifiant de corrélation du traitement en cours (lot 1 / 1b, D5 et D6) : il relie une
 * requête HTTP, les lignes d'audit qu'elle écrit, les événements qu'elle publie et les
 * traitements de ces événements. Porté par le MDC des journaux ({@value #CLE}).
 */
public final class CorrelationId {

    public static final String CLE = "correlationId";
    public static final String EN_TETE = "X-Correlation-Id";

    private CorrelationId() {
    }

    /** Identifiant courant ; en dehors de toute requête (tâche planifiée), un nouvel identifiant. */
    public static String courant() {
        String id = MDC.get(CLE);
        return id != null ? id : UUID.randomUUID().toString();
    }

    /** Valeur reçue si c'est un UUID (on ne recopie pas une chaîne arbitraire), sinon un nouvel UUID. */
    public static String depuisEnTete(String valeur) {
        if (valeur != null) {
            try {
                return UUID.fromString(valeur.trim()).toString();
            } catch (IllegalArgumentException ignore) {
                // valeur non conforme : on en génère une
            }
        }
        return UUID.randomUUID().toString();
    }

    /** Exécute {@code traitement} sous l'identifiant donné, puis rétablit le précédent. */
    public static <T> T sous(String id, Supplier<T> traitement) {
        String precedent = MDC.get(CLE);
        MDC.put(CLE, id != null ? id : UUID.randomUUID().toString());
        try {
            return traitement.get();
        } finally {
            if (precedent == null) {
                MDC.remove(CLE);
            } else {
                MDC.put(CLE, precedent);
            }
        }
    }
}
