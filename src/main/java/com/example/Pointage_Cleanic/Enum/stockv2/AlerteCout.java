package com.example.Pointage_Cleanic.Enum.stockv2;

/**
 * Alertes sur le coût unitaire d'un produit (Stock v2 7.6).
 * <ul>
 *   <li>{@code METHODE_NON_DEFINIE} : ni override produit, ni méthode globale définie.</li>
 *   <li>{@code COUT_ZERO} : coût courant ≤ 0.</li>
 *   <li>{@code ECART_ANORMAL} : écart relatif vs dernier coût > 50 %.</li>
 * </ul>
 */
public enum AlerteCout {
    METHODE_NON_DEFINIE,
    COUT_ZERO,
    ECART_ANORMAL
}
