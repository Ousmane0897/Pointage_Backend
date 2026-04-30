package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.Enum.StatutDossierEmploye;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DossierEmployeStatutRequest {

    private StatutDossierEmploye statut;
    private Integer dureeEssaiMois;
}