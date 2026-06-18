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

    // --- 7.4 Contrôle des mouvements (gating UI frontend) ---
    private boolean categorisation;
    private boolean bonsEntree;
    private boolean bonsSortie;
    private boolean workflowValidation;
    private boolean historiqueDestinataire;
    private boolean plafonds;
    private boolean dotation;
    private boolean rapportsConso;

    // --- 7.5 Analyse des consommations (gating UI frontend) ---
    private boolean analyseMensuelle;
    private boolean chantiers;
    private boolean dons;
    private boolean comparatif;
    private boolean filtresCroises;
}
