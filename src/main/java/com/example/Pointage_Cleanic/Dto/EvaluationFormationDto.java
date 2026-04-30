package com.example.Pointage_Cleanic.Dto;

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
public class EvaluationFormationDto {

    private String id;

    private String participationId;
    private String sessionId;
    private String employeId;

    private Integer note;
    private String commentaire;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEvaluation;
}