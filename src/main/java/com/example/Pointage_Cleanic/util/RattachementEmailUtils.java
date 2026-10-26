package com.example.Pointage_Cleanic.util;

import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Rattachement d'un compte de connexion à son dossier employé.
 *
 * <p>Le JWT ne porte que l'email : c'est <b>la seule clé</b> qui relie un {@code Utilisateur}
 * à un {@code DossierEmploye}. Les deux côtés doivent donc être normalisés de la même façon —
 * l'email du compte l'est depuis toujours par {@code UtilisateurController}
 * ({@code trim().toLowerCase()}), celui du dossier ne l'était pas, et un espace en fin de
 * saisie suffisait à rendre un compte « non rattaché ».
 */
public final class RattachementEmailUtils {

    private RattachementEmailUtils() {
    }

    /** Email normalisé (sans espaces, en minuscules), {@code null} si absent ou vide. */
    public static String normaliser(String email) {
        if (email == null) {
            return null;
        }
        String normalise = email.trim().toLowerCase(Locale.ROOT);
        return normalise.isEmpty() ? null : normalise;
    }

    /**
     * Dossier retenu parmi ceux qui partagent un email. {@code email} n'est pas contraint
     * unique en base : on tranche de façon déterministe (le plus petit id) pour ne pas
     * dépendre de l'ordre de la collection — c'est la règle de tous les appelants.
     */
    public static Optional<DossierEmploye> premierDossier(List<DossierEmploye> dossiers) {
        if (dossiers == null || dossiers.isEmpty()) {
            return Optional.empty();
        }
        return dossiers.stream()
                .filter(Objects::nonNull)
                .min(Comparator.comparing(DossierEmploye::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())));
    }
}
