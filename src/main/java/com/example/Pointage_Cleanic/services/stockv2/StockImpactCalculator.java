package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;

/**
 * Calcule l'impact signé d'un mouvement sur un périmètre (site donné ou consolidé).
 * Mutualisé par la synthèse mensuelle, l'approvisionnement et le tableau de bord.
 */
public final class StockImpactCalculator {

    private StockImpactCalculator() {
    }

    /**
     * Delta signé (positif = entrée, négatif = sortie) du mouvement vu d'un site.
     * Si {@code siteId} est null, on raisonne en consolidé (« tous sites »).
     */
    public static double signedDelta(MouvementStock m, String siteId) {
        return switch (m.getType()) {
            case ENTREE -> (siteId == null || siteId.equals(m.getSiteDestinationId())) ? m.getQuantite() : 0.0;
            case SORTIE -> (siteId == null || siteId.equals(m.getSiteSourceId())) ? -m.getQuantite() : 0.0;
        };
    }

    /** Quantité sortie (positive) du mouvement vu d'un site ; 0 si ce n'est pas une sortie pour ce site. */
    public static double sortie(MouvementStock m, String siteId) {
        double delta = signedDelta(m, siteId);
        return delta < 0 ? -delta : 0.0;
    }

    /** Quantité entrée (positive) du mouvement vu d'un site. */
    public static double entree(MouvementStock m, String siteId) {
        double delta = signedDelta(m, siteId);
        return delta > 0 ? delta : 0.0;
    }
}
