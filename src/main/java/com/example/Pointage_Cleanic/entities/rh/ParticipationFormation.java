package com.example.Pointage_Cleanic.entities.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "participations_formation")
public class ParticipationFormation {

    @Id
    private String id;

    private String sessionId;

    private String employeId;

    private String matricule;
    private String nom;
    private String prenom;
    private String departement;

    private boolean present;
    private boolean completee;
    private boolean attestationGeneree;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateAttestation;
}