package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ligne d'inventaire, stockée inline dans {@link Inventaire} (pas de @Document).
 * ecart = qtePhysique - qteTheorique (recalculé au comptage).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LigneInventaire {

    private String produitId;
    private String produitCode;
    private String produitLibelle;
    private UniteStock unite;

    private double qteTheorique;
    private Double qtePhysique;
    private Double ecart;
    private String justification;
}
