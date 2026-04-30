package com.example.Pointage_Cleanic.entities;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndemniteCategorie {

    private String libelle;
    private Long montant;
    private boolean imposable;
}