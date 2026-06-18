package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;

/** Libellés humains des catégories de bons (utilisés en dénormalisation : libelleType, libelle). */
public final class StockLibelles {

    private StockLibelles() {
    }

    public static String libelle(TypeEntree type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case ACHAT_FOURNISSEUR -> "Achat fournisseur";
            case RETOUR_PRODUCTION -> "Retour production";
            case TRANSFERT_INTER_SITES -> "Transfert inter-sites";
            case REINTEGRATION -> "Réintégration";
        };
    }

    public static String libelle(TypeSortie type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case DISTRIBUTION_AGENCE_SITE_CLIENT -> "Distribution agence / site client";
            case DISTRIBUTION_CHANTIER -> "Distribution chantier";
            case VENTE_PRODUIT -> "Vente produit";
            case CONSOMMATION_INTERNE -> "Consommation interne";
        };
    }

    /** Motif 7.3 équivalent d'un type d'entrée (pour le MouvementStock généré). */
    public static MotifMouvement motif(TypeEntree type) {
        return switch (type) {
            case ACHAT_FOURNISSEUR -> MotifMouvement.ACHAT;
            case RETOUR_PRODUCTION -> MotifMouvement.PRODUCTION;
            case TRANSFERT_INTER_SITES -> MotifMouvement.TRANSFERT;
            case REINTEGRATION -> MotifMouvement.RETOUR;
        };
    }

    /** Motif 7.3 équivalent d'un type de sortie (pour le MouvementStock généré). */
    public static MotifMouvement motif(TypeSortie type) {
        return switch (type) {
            case VENTE_PRODUIT -> MotifMouvement.VENTE;
            case DISTRIBUTION_AGENCE_SITE_CLIENT, DISTRIBUTION_CHANTIER, CONSOMMATION_INTERNE ->
                    MotifMouvement.CONSOMMATION;
        };
    }
}
