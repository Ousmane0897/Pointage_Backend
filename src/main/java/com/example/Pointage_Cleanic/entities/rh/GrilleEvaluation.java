package com.example.Pointage_Cleanic.entities.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "grilles_evaluation")
public class GrilleEvaluation {

    @Id
    private String id;

    private String titre;
    private String description;

    @Builder.Default
    private List<String> postesConcernes = new ArrayList<>();

    @Builder.Default
    private List<String> departementsConcernes = new ArrayList<>();

    @Builder.Default
    private List<CritereEvaluation> criteres = new ArrayList<>();

    private boolean actif;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateCreation;
}