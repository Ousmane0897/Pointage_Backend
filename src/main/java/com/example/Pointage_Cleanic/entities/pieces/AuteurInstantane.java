package com.example.Pointage_Cleanic.entities.pieces;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Auteur d'une étape de pièce : identifiant de compte et <b>instantané</b> du nom au moment de
 * l'action (le nom affiché ne change pas si le compte est renommé plus tard). Lot 1 / 1b, D4.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuteurInstantane {
    private String compteId;
    private String nom;
}
