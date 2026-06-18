package com.example.Pointage_Cleanic.entities.GestionModules.SousModules;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sous-flags du module Stock v2 (7.3), portés dans le claim JWT {@code modules.stock}.
 * Le gating fin est délégué au frontend Angular (cf. CLAUDE.md).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Stock {
    private boolean catalogue;
    private boolean mouvements;
    private boolean etatStock;
    private boolean inventaires;
    private boolean synthese;
    private boolean approvisionnement;
    private boolean tableauBord;
}
