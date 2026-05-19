package com.example.Pointage_Cleanic.Dto.productionchimie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RendementProduit {
    private String produitNom;
    private double sommeQuantiteTheorique;
    private double sommeQuantiteReelle;
    private double ecartPourcent;
    private long nbOfTermines;
}
