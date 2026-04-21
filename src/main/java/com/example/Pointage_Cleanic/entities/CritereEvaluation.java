package com.example.Pointage_Cleanic.entities;

import com.example.Pointage_Cleanic.Enum.CategorieCritere;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CritereEvaluation {

    private String code;
    private String libelle;
    private String description;
    private Integer poids;
    private CategorieCritere categorie;
}