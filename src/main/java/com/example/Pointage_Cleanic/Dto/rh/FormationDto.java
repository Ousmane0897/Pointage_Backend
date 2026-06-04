package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.TypeFormateur;
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
public class FormationDto {

    private String id;

    private String titre;
    private String description;

    @Builder.Default
    private List<String> competencesVisees = new ArrayList<>();

    private Integer dureeHeures;

    private TypeFormateur typeFormateur;
    private String formateurNom;

    private Long coutFcfa;

    private boolean actif;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateCreation;
}