package com.example.Pointage_Cleanic.Enum.rh;

import java.util.List;

/**
 * Statut d'une demande de congé dans le circuit de validation à 3 niveaux :
 * {@code EN_ATTENTE_SUPERIEUR → EN_ATTENTE_RH → EN_ATTENTE_DG → APPROUVE}.
 *
 * <p>Un refus à n'importe quel niveau est terminal ({@code REFUSE}).
 *
 * <p>{@code EN_ATTENTE} est conservé pour les demandes créées avant le circuit :
 * il est traité comme {@code EN_ATTENTE_SUPERIEUR} partout (le
 * {@code CongeStatutMigrationRunner} les convertit au démarrage).
 */
public enum StatutDemande {

    /** @deprecated legacy mono-niveau — traité comme {@link #EN_ATTENTE_SUPERIEUR}. */
    @Deprecated
    EN_ATTENTE,

    EN_ATTENTE_SUPERIEUR,
    EN_ATTENTE_RH,
    EN_ATTENTE_DG,

    APPROUVE,
    REFUSE,
    ANNULE;

    /**
     * Statuts « en cours de circuit » — legacy inclus. Point unique de vérité : sert aux
     * files de validation, aux contrôles de transition et au calcul du solde réservé.
     */
    public static final List<StatutDemande> EN_COURS = List.of(
            EN_ATTENTE, EN_ATTENTE_SUPERIEUR, EN_ATTENTE_RH, EN_ATTENTE_DG);

    public boolean estEnCours() {
        return EN_COURS.contains(this);
    }
}
