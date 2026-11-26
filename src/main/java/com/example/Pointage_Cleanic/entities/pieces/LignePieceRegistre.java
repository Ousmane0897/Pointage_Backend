package com.example.Pointage_Cleanic.entities.pieces;

import com.example.Pointage_Cleanic.Enum.pieces.StatutPiece;
import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ligne du registre des pièces (lot 1 / 1b, décision D4) : une ligne par pièce, tous modules
 * confondus. Alimenté <b>seulement</b> par le consommateur d'événements
 * {@code RegistrePiecesConsommateur} ; base de la recherche transverse et de F009 (lot 2).
 * Aucune route en 1b.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "registre_pieces")
public class LignePieceRegistre {

    @Id
    private String id;

    private TypePiece type;
    private String pieceId;
    private String numero;
    private Integer exercice;
    private Long compteur;
    private StatutPiece statut;
    private String siteId;
    private long montant;
    private LocalDateTime creeLe;
    private LocalDateTime valideeLe;

    /** Pièces dont celle-ci découle (ex. devis → facture), et pièces qui en découlent. */
    @Builder.Default
    private List<String> amont = new ArrayList<>();
    @Builder.Default
    private List<String> aval = new ArrayList<>();

    /** Date de l'événement qui a produit cet état : un événement plus ancien ne l'écrase pas. */
    private Instant etatAu;
    private String dernierEvenementId;
}
