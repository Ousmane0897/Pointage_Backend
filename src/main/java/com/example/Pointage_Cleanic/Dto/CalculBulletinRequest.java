package com.example.Pointage_Cleanic.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalculBulletinRequest {

    private String employeId;
    private int mois;
    private int annee;
    private String commentaire;
}