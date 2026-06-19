package com.example.Pointage_Cleanic.Dto.stockv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ligne envoyée par le client : produitId et quantite sont obligatoires.
 * {@code prixUnitaire} (FCFA) est optionnel — 7.6 : prix d'achat unitaire d'une entrée, pilote le
 * recalcul du coût courant (CUMP / DERNIER_PRIX) et le snapshot du mouvement. Absent (cas 7.4/7.5)
 * ⇒ on retombe sur le coût courant du produit (comportement inchangé).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LignePayload {
    private String produitId;
    private double quantite;
    private Long prixUnitaire;
}
