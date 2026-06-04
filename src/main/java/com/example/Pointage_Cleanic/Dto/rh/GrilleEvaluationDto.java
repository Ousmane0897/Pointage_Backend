package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.entities.rh.CritereEvaluation;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrilleEvaluationDto {

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