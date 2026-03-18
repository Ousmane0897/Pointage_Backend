package com.example.Pointage_Cleanic.entities.GestionModules.SousModules;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Stock {
    private boolean produits;
    private boolean entrees;
    private boolean sorties;
    private boolean suivis;
    private boolean historiquesEntrees;
    private boolean historiquesSorties;
}