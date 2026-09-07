package com.example.Pointage_Cleanic.util;

import com.example.Pointage_Cleanic.entities.rh.EnfantEmploye;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Outils purs sur la liste d'enfants d'un dossier employé.
 *
 * <p>La liste étant remplacée en bloc à chaque écriture du dossier, une ligne déjà
 * persistée n'est reconnaissable que par son {@code id} — d'où {@link #assurerIds}.
 */
public final class EnfantEmployeUtils {

    private EnfantEmployeUtils() {
    }

    /**
     * Pose un identifiant sur toute ligne qui n'en a pas encore, <b>sans jamais écraser</b>
     * un id existant : un aller-retour client/serveur doit conserver l'identité des lignes.
     * Idempotent.
     */
    public static void assurerIds(List<EnfantEmploye> enfants) {
        if (enfants == null) {
            return;
        }
        for (EnfantEmploye enfant : enfants) {
            if (enfant != null && (enfant.getId() == null || enfant.getId().isBlank())) {
                enfant.setId(UUID.randomUUID().toString());
            }
        }
    }

    /**
     * Tri par date de naissance croissante (aîné d'abord), les dates absentes en dernier.
     * Purement cosmétique — aucun calcul de droit ne dépend de l'ordre.
     */
    public static void trierParNaissance(List<EnfantEmploye> enfants) {
        if (enfants == null) {
            return;
        }
        enfants.sort(Comparator.comparing(EnfantEmploye::getDateNaissance,
                Comparator.nullsLast(Comparator.naturalOrder())));
    }
}
