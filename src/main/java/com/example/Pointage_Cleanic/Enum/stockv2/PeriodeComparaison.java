package com.example.Pointage_Cleanic.Enum.stockv2;

/**
 * Période de comparaison de la valeur de stock (Stock v2 7.6, query param {@code comparer}).
 * Référence = aujourd'hui moins : {@code JOUR} = 1 jour, {@code SEMAINE} = 7 jours,
 * {@code MOIS} = 1 mois calendaire.
 */
public enum PeriodeComparaison {
    JOUR,
    SEMAINE,
    MOIS
}
