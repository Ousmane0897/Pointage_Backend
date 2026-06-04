package com.example.Pointage_Cleanic.entities.rh;

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
@Document(collection = "evaluations_formation")
public class EvaluationFormation {

    @Id
    private String id;

    @Indexed
    private String participationId;

    @Indexed
    private String sessionId;

    private String employeId;

    private Integer note;
    private String commentaire;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEvaluation;
}