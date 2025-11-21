package com.example.Pointage_Cleanic.entities.besoins;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BesoinProduit {
    private String codeProduit;
    private String nomProduit;
    private int quantite;
}
