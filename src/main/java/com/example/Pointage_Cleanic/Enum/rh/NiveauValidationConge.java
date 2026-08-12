package com.example.Pointage_Cleanic.Enum.rh;

import java.util.Optional;

/**
 * Niveau du circuit de validation des congés.
 *
 * <p>Le niveau 1 est porté par le <b>supérieur hiérarchique du demandeur</b> (un employé, pas un
 * rôle). Le niveau 2 par le rôle {@code RH}, le niveau 3 par {@code SUPERADMIN} — la Direction
 * générale <i>est</i> le super-admin, aucun rôle dédié n'existe.
 */
public enum NiveauValidationConge {

    SUPERIEUR,
    RH,
    DIRECTION_GENERALE;

    /** Statut d'attente correspondant à ce niveau. */
    public StatutDemande statutAttente() {
        return switch (this) {
            case SUPERIEUR -> StatutDemande.EN_ATTENTE_SUPERIEUR;
            case RH -> StatutDemande.EN_ATTENTE_RH;
            case DIRECTION_GENERALE -> StatutDemande.EN_ATTENTE_DG;
        };
    }

    /** Statut obtenu une fois ce niveau franchi. */
    public StatutDemande statutApresValidation() {
        return switch (this) {
            case SUPERIEUR -> StatutDemande.EN_ATTENTE_RH;
            case RH -> StatutDemande.EN_ATTENTE_DG;
            case DIRECTION_GENERALE -> StatutDemande.APPROUVE;
        };
    }

    /**
     * Niveau attendu pour un statut donné, vide si le statut est terminal.
     * Le statut legacy {@code EN_ATTENTE} est assimilé au niveau 1.
     */
    public static Optional<NiveauValidationConge> depuisStatut(StatutDemande statut) {
        if (statut == null) {
            return Optional.empty();
        }
        return switch (statut) {
            case EN_ATTENTE, EN_ATTENTE_SUPERIEUR -> Optional.of(SUPERIEUR);
            case EN_ATTENTE_RH -> Optional.of(RH);
            case EN_ATTENTE_DG -> Optional.of(DIRECTION_GENERALE);
            default -> Optional.empty();
        };
    }
}
