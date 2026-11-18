package com.example.Pointage_Cleanic.entities;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "pointages")
// Index composé pour filtrer par date et trier par timestamp
@CompoundIndex(name = "date_timestamp_idx", def = "{'date': 1, 'timestamp': -1}")


@CompoundIndex(
        name = "search_idx",
        def = "{'nom': 1, 'prenom': 1, 'codeSecret': 1}"
)
// Couvre le flux de pointage : recherche/clôture atomique du pointage ouvert du jour.
@CompoundIndex(
        name = "pointage_flux_idx",
        def = "{'codeSecret': 1, 'date': 1, 'heureDepart': 1}"
)

public class Pointage {

    @Id
    private String id;


    private String codeSecret;
    private String prenom;
    private String nom;
    @Indexed
    private String[] site;

    private LocalDate date;

    private String heureArrive;
    private String heureDepart;
    private String duree;
    private String status;
    @Indexed
    private String deviceId;

    /** Instant de l'ARRIVÉE (posé à la création, jamais modifié par la sortie). */
    @CreatedDate
    private Instant timestamp;

    private String adresse;

    // --- Sortie : téléphone et instant du pointage de DÉPART ---
    // Nuls sur les pointages antérieurs et tant que le pointage est ouvert. Ils permettent
    // à la garde anti-triche de voir AUSSI les sorties : sans eux, un agent pouvait pointer
    // la sortie d'un collègue avec son téléphone sans que rien ne le détecte.

    /** Téléphone (deviceId) ayant servi au pointage de départ. */
    private String deviceIdSortie;
    /** Instant du pointage de départ. */
    private Instant timestampSortie;
}