package com.example.Pointage_Cleanic.Enum.stockv2;

/**
 * Méthode de valorisation du coût unitaire courant d'un produit (Stock v2 7.6).
 * <ul>
 *   <li>{@code CUMP} : coût unitaire moyen pondéré, recalculé à chaque entrée.</li>
 *   <li>{@code DERNIER_PRIX} : le coût courant prend le dernier prix d'achat.</li>
 *   <li>{@code FIXE} : coût saisi manuellement, jamais recalculé.</li>
 * </ul>
 * Une méthode {@code null} sur un produit signifie « hériter de la méthode globale »
 * ({@code ParametrageValorisation.methodeDefaut}), et à défaut {@code FIXE}.
 */
public enum MethodeValorisation {
    CUMP,
    DERNIER_PRIX,
    FIXE
}
