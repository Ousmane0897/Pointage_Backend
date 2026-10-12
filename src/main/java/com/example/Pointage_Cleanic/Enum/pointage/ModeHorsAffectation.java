package com.example.Pointage_Cleanic.Enum.pointage;

/**
 * Traitement d'un scan QR d'agence quand l'agence scannée ne figure pas parmi les
 * affectations actives de l'agent (paramètre {@code POINTAGE_HORS_AFFECTATION}).
 */
public enum ModeHorsAffectation {
    /** Le pointage est refusé (422). */
    BLOQUER,
    /** Le pointage est accepté et marqué d'une anomalie consultable par la RH. */
    AVERTIR,
    /** Aucun contrôle. */
    AUTORISER
}
