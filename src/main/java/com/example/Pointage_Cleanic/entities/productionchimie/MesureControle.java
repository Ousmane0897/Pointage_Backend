package com.example.Pointage_Cleanic.entities.productionchimie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesureControle {
    private String testLibelle;
    private String parametre;
    private String valeurCible;
    private String valeurMesuree;
    private String unite;
    private boolean conforme;
    private String commentaire;
}
