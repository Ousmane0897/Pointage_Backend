package com.example.Pointage_Cleanic.Enum.stockv2;

/**
 * Gravité d'une dérive de coût (Stock v2 7.6, tableau de bord financier).
 * {@code CRITIQUE} si |ecartPct| ≥ 40 ; {@code ATTENTION} si |ecartPct| ≥ 20.
 */
public enum GraviteDerive {
    CRITIQUE,
    ATTENTION
}
