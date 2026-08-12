package com.example.Pointage_Cleanic.entities.rh;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Décision rendue à un niveau du circuit de validation des congés.
 * Document embarqué dans {@link DemandeConge} (un par niveau).
 *
 * <p>Le décideur est toujours résolu depuis le JWT côté serveur, jamais accepté du client.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionNiveau {

    private String decideurId;
    private String decideurNom;
    private LocalDateTime date;
    private String commentaire;
}
