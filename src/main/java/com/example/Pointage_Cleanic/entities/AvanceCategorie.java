package com.example.Pointage_Cleanic.entities;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvanceCategorie {

    private String libelle;
    private Long montant;
    private Integer dureeMois;
}
