package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.PerimetreInventaire;
import com.example.Pointage_Cleanic.Enum.pieces.StatutPiece;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
import com.example.Pointage_Cleanic.entities.pieces.Piece;
import com.example.Pointage_Cleanic.entities.pieces.PieceBase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_inventaires")
public class Inventaire implements Piece {

    @Id
    private String id;

    /**
     * Verrou optimiste (lot 1 / 1a, décision D1.4) : un enregistrement fondé sur une lecture
     * périmée échoue (409 CONFLIT_VERSION) au lieu d'écraser une modification concurrente.
     * Jamais exposé ni accepté par l'API. Initialisé à 0 sur l'existant par la ChangeUnit 0011.
     */
    @Version
    private Long version;

    /**
     * Numéro de l'inventaire. Lot 1 / 1b (D4) : nul en brouillon, attribué à la clôture
     * ({@code INV-2026-0001}) ; les inventaires antérieurs gardent leur référence d'origine.
     */
    private String reference;

    /** Bloc commun des pièces (lot 1 / 1b, D4) : numérotation, auteurs, annulation, abandon. */
    private PieceBase piece;

    private String libelle;
    private LocalDate datePlanifiee;

    private String siteId;
    private String siteNom;

    private PerimetreInventaire perimetre;
    private String categorieId;

    private double seuilEcartJustification;

    private StatutInventaire statut;

    @Builder.Default
    private List<LigneInventaire> lignes = new ArrayList<>();

    private String responsable;
    private LocalDate dateCloture;
    private String commentaire;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public StatutPiece statutPiece() {
        return statut == null ? StatutPiece.BROUILLON : statut.statutPiece();
    }

    @Override
    public String siteIdPiece() {
        return siteId;
    }

    @Override
    public long montantPiece() {
        return 0L;
    }

    /** Référence si elle existe (numéro attribué ou référence historique), sinon {@code BROUILLON-…}. */
    @Override
    public String libelleNumero() {
        return reference != null ? reference : Piece.super.libelleNumero();
    }
}
