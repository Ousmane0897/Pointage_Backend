package com.example.Pointage_Cleanic.entities;

import com.example.Pointage_Cleanic.Enum.StatutSession;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "sessions_formation")
public class SessionFormation {

    @Id
    private String id;

    @Indexed
    private String formationId;

    private String formationTitre;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private String lieu;
    private Integer capaciteMax;
    private Integer participantsInscrits;

    private StatutSession statut;
}