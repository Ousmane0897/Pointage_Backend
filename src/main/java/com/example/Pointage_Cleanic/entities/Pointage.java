package com.example.Pointage_Cleanic.entities;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "pointages")
// Index composé pour filtrer par date et trier par timestamp


// Couvre le flux de pointage : recherche/clôture atomique du pointage ouvert du jour.

public class Pointage {

    @Id
    private String id;


    private String codeSecret;
    private String prenom;
    private String nom;
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