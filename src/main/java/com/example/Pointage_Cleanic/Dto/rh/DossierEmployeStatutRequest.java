package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
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