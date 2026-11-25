package com.example.Pointage_Cleanic.entities.pieces;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Annulation tracée d'une pièce validée (lot 1 / 1b, D4 ; CDC règle 5 : qui, quand, pourquoi).
 * La pièce reste en base ; ses effets sont contre-passés par les consommateurs de l'événement
 * {@code PIECE_ANNULEE}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Annulation {
    private AuteurInstantane par;
    private LocalDateTime le;
    private String motif;
    /** Pièce qui corrige celle-ci, quand l'annulation en appelle une (sinon null). */
    private String pieceCorrectiveId;
}
