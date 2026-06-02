package com.example.Pointage_Cleanic.services.terrain;

import org.springframework.stereotype.Service;

import static com.example.Pointage_Cleanic.services.terrain.TerrainConstantes.RAYON_TERRE_M;

/**
 * Calcul de distance géodésique par la formule de Haversine.
 * Utilisé par la validation anti-spoof du pointage GPS.
 */
@Service
public class HaversineService {

    /**
     * Distance en mètres entre deux points GPS.
     */
    public double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return RAYON_TERRE_M * c;
    }
}