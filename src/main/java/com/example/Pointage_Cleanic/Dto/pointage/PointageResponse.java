package com.example.Pointage_Cleanic.Dto.pointage;

import com.example.Pointage_Cleanic.entities.Pointage;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Réponse de {@code POST /api/pointages}.
 * <p>
 * Le pointage est <b>déplié</b> à la racine ({@link JsonUnwrapped}) : le JSON reste un
 * sur-ensemble strict de l'ancienne réponse (l'entité brute), si bien qu'un front non
 * mis à jour continue de lire {@code heureArrive} / {@code heureDepart}.
 * <p>
 * {@code agenceId}, {@code agenceNom} et {@code distanceMetres} désignent l'agence <b>du
 * scan courant</b> : pour une sortie, l'agence de sortie (qui peut différer de celle
 * d'entrée). Ils sont nuls pour un pointage sans QR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointageResponse {

    @JsonUnwrapped
    private Pointage pointage;

    /** {@code ENTREE} ou {@code SORTIE}. */
    private String type;
    private LocalDateTime horodatage;

    private String agenceId;
    private String agenceNom;
    private Double distanceMetres;

    /** Message à montrer à l'agent (pointage accepté mais signalé), ou {@code null}. */
    private String avertissement;
}
