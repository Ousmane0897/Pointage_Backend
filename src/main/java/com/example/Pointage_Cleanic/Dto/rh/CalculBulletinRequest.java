package com.example.Pointage_Cleanic.Dto.rh;

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
    // Code de la grille salariale sélectionnée manuellement à la génération
    // (le rattachement employé → grille n'est plus automatique).
    private String categorieCode;
    private int mois;
    private int annee;
    private String commentaire;
}