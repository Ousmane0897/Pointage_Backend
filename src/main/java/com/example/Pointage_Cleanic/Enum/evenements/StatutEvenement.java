package com.example.Pointage_Cleanic.Enum.evenements;

/**
 * Cycle d'un événement de domaine dans l'outbox (lot 1 / 1b, décision D5).
 *
 * <ul>
 *   <li>{@code A_TRAITER} : écrit avec la transaction métier, ou remis en file après un échec ;</li>
 *   <li>{@code EN_COURS} : réclamé par un dispatcher, sous bail ; un bail expiré le rend de
 *       nouveau réclamable (instance arrêtée en cours de traitement) ;</li>
 *   <li>{@code TRAITE} : tous les consommateurs l'ont traité ; conservé (journal rejouable) ;</li>
 *   <li>{@code EN_ERREUR} : échecs répétés, alerte d'exploitation, relance manuelle.</li>
 * </ul>
 */
public enum StatutEvenement {
    A_TRAITER,
    EN_COURS,
    TRAITE,
    EN_ERREUR
}
