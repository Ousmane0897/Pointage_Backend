package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.Enum.StatutPeriodeEssai;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionPeriodeEssaiDto {

    private String id;
    private String periodeEssaiId;
    private StatutPeriodeEssai decision;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDecision;

    private String decideurNom;
    private String decideurRole;
    private String commentaire;
}