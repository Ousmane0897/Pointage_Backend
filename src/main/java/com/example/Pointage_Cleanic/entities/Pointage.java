package com.example.Pointage_Cleanic.entities;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.TextIndexed;
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
public class Pointage {

    @Id
    private String id;

    @TextIndexed
    private String codeSecret;

    @TextIndexed
    private String prenom;

    @TextIndexed
    private String nom;

    @TextIndexed
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