package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.entities.TrancheIr;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParametresPaieDto {

    private String id;

    private Double tauxIpresGeneralSalarie;
    private Double tauxIpresGeneralEmployeur;
    private Long plafondIpresGeneral;

    private Double tauxIpresComplementaireSalarie;
    private Double tauxIpresComplementaireEmployeur;
    private Long plafondIpresComplementaire;

    private Double tauxCssPrestationsFamilialesEmployeur;
    private Long plafondCss;

    private Double tauxAtMpDefaut;

    private Long montantTrimfMensuel;

    private List<TrancheIr> baremeIr;

    private Integer joursOuvrablesStandardMois;

    private Instant dateModification;
    private String modifieParId;
    private String modifieParNom;
}