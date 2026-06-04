package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.entities.rh.NoteEvaluation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluationManagerRequest {

    @Builder.Default
    private List<NoteEvaluation> notesManager = new ArrayList<>();

    private String commentaireManager;
    private String objectifsPeriodeSuivante;

    private String evaluateurId;
    private String evaluateurNom;
}