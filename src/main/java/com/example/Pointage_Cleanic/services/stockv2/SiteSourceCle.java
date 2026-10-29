package com.example.Pointage_Cleanic.services.stockv2;

import java.util.Locale;

/**
 * Clé d'imputation analytique du site source d'une sortie.
 *
 * <p>Un bon de sortie peut viser un site <b>hors référentiel</b> (chantier, prestation
 * ponctuelle) : {@code siteSourceId} est alors nul et seul {@code siteSourceNom} est
 * renseigné. Grouper sur l'id seul fusionnerait tous ces sites sous une même clé nulle ;
 * on groupe donc un site libre par son <b>nom normalisé</b> (casse et espaces ignorés),
 * préfixé pour ne jamais entrer en collision avec un id du référentiel.
 */
public final class SiteSourceCle {

    public static final String PREFIXE_LIBRE = "libre:";
    public static final String AUCUN = "—";

    private SiteSourceCle() {
    }

    public static String cle(String siteId, String siteNom) {
        if (siteId != null && !siteId.isBlank()) {
            return siteId;
        }
        String nom = normaliserNom(siteNom);
        return nom == null ? AUCUN : PREFIXE_LIBRE + nom.toLowerCase(Locale.ROOT);
    }

    /** Trim + espaces internes réduits ; {@code null} si vide. Sert aussi à la saisie. */
    public static String normaliserNom(String nom) {
        if (nom == null) {
            return null;
        }
        String n = nom.trim().replaceAll("\\s+", " ");
        return n.isEmpty() ? null : n;
    }
}
