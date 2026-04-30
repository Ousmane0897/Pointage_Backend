package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.entities.NoteEvaluation;
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
public class AutoEvaluationRequest {

    @Builder.Default
    private List<NoteEvaluation> notesAutoEvaluation = new ArrayList<>();

    private String commentaireEmploye;
}