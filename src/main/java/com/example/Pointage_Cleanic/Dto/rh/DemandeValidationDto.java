package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutValidation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DemandeValidationDto {

    private String id;
    private String periodeEssaiId;
    private String employeId;
    private String employeNom;
    private String employePrenom;
    private StatutValidation statut;
    private LocalDateTime dateCreation;
    private String commentaireManager;
    private String commentaireRh;
    private LocalDateTime dateFinalisation;
}