package com.example.Pointage_Cleanic.services.rh;

import java.util.Set;

/**
 * Périmètre de <b>lecture</b> des congés de l'appelant.
 *
 * <p>Règle métier : un employé ne voit que ses propres congés, plus ceux de ses
 * <b>subordonnés directs</b> s'il en encadre. La RH et le super-admin (= la Direction
 * générale) voient tout — sans quoi ils ne pourraient pas instruire les niveaux 2 et 3
 * du circuit de validation.
 *
 * <p>Un compte non rattaché à un {@code DossierEmploye} (compte technique, super-admin
 * non salarié…) a un périmètre <b>vide</b>, jamais total : c'est le sens de lecture sûr.
 *
 * @param voitTout          l'appelant voit tous les congés (RH / super-admin)
 * @param moi               id du dossier employé de l'appelant, {@code null} s'il n'est pas rattaché
 * @param employesVisibles  ids des employés dont l'appelant peut lire les congés (lui inclus)
 */
public record PerimetreConges(boolean voitTout, String moi, Set<String> employesVisibles) {

    /** Périmètre sans restriction : RH et super-admin (= Direction générale). */
    public static PerimetreConges tout() {
        return new PerimetreConges(true, null, Set.of());
    }

    /** Aucun congé lisible : compte non rattaché à un dossier employé. */
    public static PerimetreConges vide() {
        return new PerimetreConges(false, null, Set.of());
    }

    /** Aucun congé n'est lisible : inutile d'aller interroger la base. */
    public boolean estVide() {
        return !voitTout && employesVisibles.isEmpty();
    }

    public boolean voitEmploye(String employeId) {
        return voitTout || (employeId != null && employesVisibles.contains(employeId));
    }

    /**
     * Lecture d'une demande de congé.
     *
     * <p>On ajoute au périmètre le <b>validateur figé</b> sur la demande : celle-ci reste
     * lisible par le supérieur désigné à sa création, même si l'organigramme a changé
     * depuis. Sans cela, le lien du mail de notification renverrait un 403 à son propre
     * destinataire.
     */
    public boolean voitDemande(String employeId, String superieurHierarchiqueId) {
        return voitEmploye(employeId)
                || (moi != null && moi.equals(superieurHierarchiqueId));
    }
}
