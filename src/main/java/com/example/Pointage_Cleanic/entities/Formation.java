package com.example.Pointage_Cleanic.entities;

import com.example.Pointage_Cleanic.Enum.TypeFormateur;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "formations")
public class Formation {

    @Id
    private String id;

    private String titre;
    private String description;

    @Builder.Default
    private List<String> competencesVisees = new ArrayList<>();

    private Integer dureeHeures;

    private TypeFormateur typeFormateur;
    private String formateurNom;

    private Long coutFcfa;

    private boolean actif;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateCreation;

    private Instant dateModification;
}