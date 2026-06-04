package com.example.Pointage_Cleanic.Dto.rh;

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
public class ValidationEvaluationRequest {

    private String validateurId;
    private String validateurNom;

    @Builder.Default
    private List<BesoinFormationDto> besoinsFormation = new ArrayList<>();
}