package com.example.Pointage_Cleanic.Dto.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflitAffectation {
    private String employeId;
    private String employeNom;
    private List<AffectationAgentDto> affectations;
    private LocalDateTime intervalleDebut;
    private LocalDateTime intervalleFin;
}