package com.example.Pointage_Cleanic.entities;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrimeCategorie {

    private String libelle;
    private Long montant;
    private boolean imposable;
    private boolean soumiseIpres;
    private boolean soumiseCss;
}