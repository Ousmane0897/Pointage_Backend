package com.example.Pointage_Cleanic.entities.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutPeriodeEssai;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionPeriodeEssai {

    private String id;
    private String periodeEssaiId;
    private StatutPeriodeEssai decision;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDecision;

    private String decideurNom;
    private String decideurRole;
    private String commentaire;
}