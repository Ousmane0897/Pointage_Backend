package com.example.Pointage_Cleanic.Dto.pointage;

import com.example.Pointage_Cleanic.entities.Pointage;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pointage tel que lu par les écrans (suivi du jour, historique). Le pointage est déplié
 * à la racine : le JSON reste un sur-ensemble de l'entité renvoyée jusqu'ici.
 * <p>
 * Les noms d'agence sont résolus à la lecture depuis {@code agenceId} /
 * {@code agenceIdSortie} ; {@code null} ⇒ « Agence non renseignée » côté front
 * (pointage antérieur au QR d'agence, sans QR, ou agence supprimée).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointageLectureDto {

    @JsonUnwrapped
    private Pointage pointage;

    private String agenceArriveeNom;
    private String agenceDepartNom;
}
