package com.example.Pointage_Cleanic.services.terrain;

/**
 * Constantes transverses du module Exploitation Terrain (5.2).
 */
public final class TerrainConstantes {

    private TerrainConstantes() {
    }

    /** Département RH dont les agents sont pilotables par le module Terrain. */
    public static final String DEPARTEMENT_EXPLOITATION = "Exploitation";

    /** Rayon de la Terre en mètres (formule de Haversine). */
    public static final double RAYON_TERRE_M = 6_371_000d;

    /** Rayon de tolérance par défaut autour d'un site (mètres) si non renseigné. */
    public static final int RAYON_TOLERANCE_DEFAUT_M = 100;

    /** Précision GPS au-delà de laquelle un pointage est jugé imprécis (mètres). */
    public static final double PRECISION_GPS_MAX_M = 50d;

    /** Seuil de conformité par défaut d'un contrôle qualité (note /5). */
    public static final double NOTE_SEUIL_CONFORMITE_DEFAUT = 3.5d;
}