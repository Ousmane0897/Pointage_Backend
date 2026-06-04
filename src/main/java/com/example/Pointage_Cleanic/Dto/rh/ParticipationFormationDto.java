package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParticipationFormationDto {

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