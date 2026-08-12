package com.example.Pointage_Cleanic.entities.rh;

import com.example.Pointage_Cleanic.Enum.rh.ActionValidationConge;
import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entrée d'historique du circuit de validation d'une demande de congé :
 * qui a fait quoi, à quel niveau, quand. Document embarqué dans {@link DemandeConge}.
 *
 * <p>L'auteur est dénormalisé depuis le JWT côté serveur, jamais accepté du client.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoriqueValidationConge {

    private ActionValidationConge action;

    /** Absent pour {@code CREATION} / {@code ANNULATION}. */
    private NiveauValidationConge niveau;

    private String auteurId;
    private String auteurNom;
    private LocalDateTime date;
    private String commentaire;
}
