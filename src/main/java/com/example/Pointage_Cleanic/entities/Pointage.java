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
    private String deviceId;

    @CreatedDate
    private Instant timestamp;

    private String adresse;
}