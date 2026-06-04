package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutPeriodeEssai;
import com.example.Pointage_Cleanic.Enum.rh.TypeContratRh;
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
public class PeriodeEssaiDto {

    private String id;
    private String employeId;
    private String employeNom;
    private String employePrenom;
    private String contratId;
    private TypeContratRh typeContrat;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private Integer dureeJours;
    private StatutPeriodeEssai statut;

    @Builder.Default
    private List<AlertePeriodeEssaiDto> alertes = new ArrayList<>();

    @Builder.Default
    private List<DecisionPeriodeEssaiDto> decisions = new ArrayList<>();
}