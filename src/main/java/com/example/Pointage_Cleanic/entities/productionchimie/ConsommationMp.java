package com.example.Pointage_Cleanic.entities.productionchimie;

import com.example.Pointage_Cleanic.Enum.UniteChimie;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsommationMp {
    private String matierePremiereId;
    private String matierePremiereNom;
    private UniteChimie unite;
    private Double quantiteTheorique;
    private Double quantiteReelle;
    private String lotFournisseurUtilise;
    private String mouvementStockId;
}