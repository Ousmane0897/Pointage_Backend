package com.example.Pointage_Cleanic.Dto;

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
public class KpiRhDto {

    private Long effectifTotal;

    @Builder.Default
    private List<RepartitionItemDto> repartitionDepartement = new ArrayList<>();

    @Builder.Default
    private List<RepartitionItemDto> repartitionSite = new ArrayList<>();

    @Builder.Default
    private List<RepartitionItemDto> repartitionTypeContrat = new ArrayList<>();

    private Double turnover;

    private Double tauxAbsenteisme;
    private Double retardsMoyensMinutes;
    private Double soldeCongesMoyen;

    private Long masseSalarialeMensuelle;
    private Long masseSalarialeAnnuelle;
    private Long coutMoyenParEmploye;

    private Long formationsRealisees;
    private Double tauxParticipationFormation;

    @Builder.Default
    private List<RepartitionItemDto> sanctionsParType = new ArrayList<>();

    @Builder.Default
    private List<RepartitionItemDto> sanctionsParPeriode = new ArrayList<>();
}