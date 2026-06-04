package com.example.Pointage_Cleanic.entities.rh;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetenueCategorie {

    private String libelle;
    private Long montant;
}
