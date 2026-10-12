package com.example.Pointage_Cleanic.Enum.pointage;

/**
 * Anomalies <b>informatives</b> posées sur un pointage accepté. Aucune ne bloque le
 * pointage : ce qui bloque est un refus (422), pas une anomalie.
 * <p>
 * Chaque anomalie est suffixée par le moment du scan : un agent peut légitimement
 * arriver dans une agence et repartir d'une autre, chaque scan est donc contrôlé contre
 * l'agence qu'il désigne, indépendamment de l'autre.
 * <p>
 * ⚠ Il n'existe volontairement <b>pas</b> d'anomalie « agence de sortie différente ».
 */
public enum AnomaliePointage {
    HORS_AFFECTATION_ENTREE,
    HORS_AFFECTATION_SORTIE,
    /** Précision GPS annoncée au-delà de {@code TerrainConstantes.PRECISION_GPS_ANOMALIE_M}. */
    PRECISION_FAIBLE_ENTREE,
    PRECISION_FAIBLE_SORTIE;

    public static AnomaliePointage horsAffectation(boolean sortie) {
        return sortie ? HORS_AFFECTATION_SORTIE : HORS_AFFECTATION_ENTREE;
    }

    public static AnomaliePointage precisionFaible(boolean sortie) {
        return sortie ? PRECISION_FAIBLE_SORTIE : PRECISION_FAIBLE_ENTREE;
    }
}
