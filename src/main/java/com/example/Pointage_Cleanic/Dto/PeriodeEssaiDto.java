package com.example.Pointage_Cleanic.Dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodeEssaiDto {

    private String id;
    private String agentId;
    private String nomComplet;
    private String poste;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEmbauche;

    private Integer dureeEssaiMois;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFinEssaiCalculee;

    private long joursRestants;
    private Boolean essaiTermine;
    private String statut;
}