package com.example.Pointage_Cleanic.util;

import com.example.Pointage_Cleanic.entities.rh.AffectationSite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Découpage tolérant du champ {@code siteAffecte} (chaîne multi-sites) en sites
 * distincts, et dérivation inverse {@code affectations → siteAffecte}.
 * <p>
 * Séparateurs reconnus : {@code /} ou {@code ,} (avec espaces optionnels
 * autour), ou un tiret <b>entouré d'espaces</b> ({@code " - "}). Le tiret n'est
 * un séparateur que s'il est entouré d'espaces, afin de préserver les noms de
 * sites contenant un tiret collé (ex. « Sacré-Coeur »).
 * <p>
 * Exemples :
 * <ul>
 *   <li>{@code "Sacré-Coeur - Point E"} → [{@code "Sacré-Coeur"}, {@code "Point E"}]</li>
 *   <li>{@code "Keur gorgui / yoff"} → [{@code "Keur gorgui"}, {@code "yoff"}]</li>
 *   <li>{@code "A, B"} → [{@code "A"}, {@code "B"}]</li>
 *   <li>{@code "Sacré-Coeur"} → [{@code "Sacré-Coeur"}] (non découpé)</li>
 * </ul>
 */
public final class SiteAffecteUtils {

    /** Séparateur canonique utilisé pour dériver siteAffecte depuis affectations. */
    public static final String SEPARATEUR = " - ";

    private static final Pattern SEPARATEURS = Pattern.compile("\\s*[/,]\\s*|\\s+-\\s+");

    private SiteAffecteUtils() {
    }

    /**
     * Découpe une chaîne {@code siteAffecte} en sites distincts (trim, vides
     * ignorés). Chaîne null/blank → liste vide.
     */
    public static List<String> decouper(String siteAffecte) {
        if (siteAffecte == null || siteAffecte.isBlank()) {
            return List.of();
        }
        return Arrays.stream(SEPARATEURS.split(siteAffecte))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Dérive une liste d'affectations (sites sans horaires) depuis une chaîne
     * {@code siteAffecte} historique. Utilisé par le back-fill et l'import bulk.
     */
    public static List<AffectationSite> affectationsDepuisSiteAffecte(String siteAffecte) {
        List<AffectationSite> affectations = new ArrayList<>();
        for (String site : decouper(siteAffecte)) {
            affectations.add(AffectationSite.builder().site(site).build());
        }
        return affectations;
    }
}
