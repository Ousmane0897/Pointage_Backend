package com.example.Pointage_Cleanic.entities.rh;

import com.example.Pointage_Cleanic.Enum.rh.CategorieCritere;
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