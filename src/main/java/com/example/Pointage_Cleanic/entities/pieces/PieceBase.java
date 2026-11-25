package com.example.Pointage_Cleanic.entities.pieces;

import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Bloc commun de toute pièce (lot 1 / 1b, décision D4), <b>incorporé</b> dans le document de la
 * pièce sous le champ {@code piece}.
 *
 * <p>Écart de forme avec la conception, consigné dans {@code docs/refonte/1b-pieces.md} : le
 * statut et la version restent à la <b>racine</b> du document. {@code @Version} n'est reconnu par
 * Spring Data qu'à la racine, et chaque pièce garde son statut métier (contrat d'API du front,
 * index existants). Le statut commun est donné par {@link Piece#statutPiece()}.
 *
 * <p>Numérotation : {@link #numero} reste nul tant que la pièce n'est pas validée ; il est
 * attribué dans la transaction de validation par
 * {@code services.pieces.NumerotationService}. Une pièce antérieure à 1b garde sa référence
 * d'origine en {@code numero}, avec {@code exercice} et {@code compteur} nuls (série historique).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PieceBase {

    private TypePiece type;

    /** Format {@code <TYPE>-<EXERCICE>-<NNNN>}, nul en brouillon. */
    private String numero;
    /** Année civile (Africa/Dakar) de la validation ; nul pour une référence historique. */
    private Integer exercice;
    /** Rang dans la série (type, exercice) : c'est lui qui trie, jamais la chaîne du numéro. */
    private Long compteur;

    private AuteurInstantane creePar;
    private LocalDateTime creeLe;
    private AuteurInstantane soumisePar;
    private LocalDateTime soumiseLe;
    private AuteurInstantane valideePar;
    private LocalDateTime valideeLe;

    private Annulation annulation;
    private Abandon abandon;
}
