package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutSession;
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
public class SessionFormationDto {

    private String id;

    private String formationId;
    private String formationTitre;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private String lieu;
    private Integer capaciteMax;
    private Integer participantsInscrits;

    private StatutSession statut;
}