package com.example.Pointage_Cleanic.Dto.pointage;

import com.example.Pointage_Cleanic.entities.Pointage;

import java.time.LocalDate;

/**
 * Réponse publique de {@code POST /api/pointages} : uniquement ce que l'écran final du kiosque
 * affiche. Ni nom de famille, ni position GPS, ni adresse, ni appareil, ni code de pointage —
 * la réponse est lisible par quiconque saisit un code à 4 chiffres.
 */
public record PointageConfirmationDto(
        String prenom,
        String status,
        LocalDate date,
        String heureArrive,
        String heureDepart
) {

    public static PointageConfirmationDto de(Pointage p) {
        return new PointageConfirmationDto(p.getPrenom(), p.getStatus(), p.getDate(),
                p.getHeureArrive(), p.getHeureDepart());
    }
}
