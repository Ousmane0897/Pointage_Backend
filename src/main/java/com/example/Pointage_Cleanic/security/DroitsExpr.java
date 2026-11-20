package com.example.Pointage_Cleanic.security;

/**
 * Expressions {@code @PreAuthorize} partagées par plusieurs contrôleurs (voir {@link Droits}).
 */
public final class DroitsExpr {

    private DroitsExpr() {
    }

    /**
     * Référentiel en lecture multi-modules : listes d'employés, de sites… consultées par les
     * sélecteurs de plusieurs modules (Terrain, Stock, Chimie, tableau de bord). Tout compte
     * qui a au moins un drapeau d'un module consommateur peut lire.
     */
    public static final String REFERENTIEL =
            "@droits.peutUnParmi('rh.*', 'terrain.*', 'stock.*', 'productionChimie.*', 'dashboard')";

    /** Vues superviseur des pointages kiosque (aujourd'hui, historique, exports). */
    public static final String POINTAGES_SUPERVISION =
            "@droits.peutUnParmi('terrain.pointage', 'rh.pointageCentralise', 'dashboard')";
}
