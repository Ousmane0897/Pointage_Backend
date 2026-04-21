package com.example.Pointage_Cleanic.entities;

import com.example.Pointage_Cleanic.Enum.NatureLigne;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneBulletin {

    private String code;
    private String libelle;
    private NatureLigne nature;
    private Long base;
    private Double taux;
    private Long montantSalarial;
    private Long montantPatronal;
}