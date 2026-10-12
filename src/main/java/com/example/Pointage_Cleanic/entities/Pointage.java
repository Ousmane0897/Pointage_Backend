package com.example.Pointage_Cleanic.entities;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

    @CreatedDate
    private Instant timestamp;

    private String adresse;

    // --- QR code d'agence (null sur les pointages antérieurs et ceux sans QR) ---
    // ⚠ L'agence est stockée par RÉFÉRENCE, jamais par nom : le nom est résolu à la
    // lecture. `site[]` reste la copie des sites de l'agent, pour compatibilité.

    /** Agence du scan d'ARRIVÉE (SiteClient.id). */
    @Indexed
    private String agenceId;
    private Double latitude;
    private Double longitude;
    private Double precisionMetres;
    /** Distance mesurée entre la position du scan d'arrivée et l'agence. */
    private Double distanceMetres;

    /**
     * Agence du scan de DÉPART. Peut légitimement différer de {@link #agenceId} : un agent
     * arrive dans une agence et repart d'une autre. Aucune anomalie n'en découle.
     */
    @Indexed
    private String agenceIdSortie;
    private Double latitudeSortie;
    private Double longitudeSortie;
    private Double precisionMetresSortie;
    private Double distanceMetresSortie;

    /** Codes {@code AnomaliePointage} (informatifs, consultables par la RH). */
    private List<String> anomalies;
}