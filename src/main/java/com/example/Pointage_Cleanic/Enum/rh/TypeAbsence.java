package com.example.Pointage_Cleanic.Enum.rh;

public enum TypeAbsence {
    // Valeurs actives (proposées par l'UI)
    CONGE_PAYE, ANNUEL, SANS_SOLDE,
    // Valeurs legacy conservées pour la relecture des documents existants (plus proposées par l'UI)
    MALADIE, PERMISSION, INJUSTIFIEE,
    AUTRE
}