package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.pieces.StatutPiece;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.entities.pieces.Piece;
import com.example.Pointage_Cleanic.entities.pieces.PieceBase;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
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

/** Bon d'entrée (7.4). Document de workflow ; ne touche au stock qu'à la validation (EFFECTIF). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_bons_entree")
public class BonEntree implements Piece {

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
     * Numéro du bon. Lot 1 / 1b (D4) : nul en brouillon, attribué à la validation
     * ({@code BE-2026-0001}) et recopié depuis {@link #piece} ; les bons antérieurs gardent leur
     * référence d'origine ({@code BE-20260617-001}).
     */
    private String reference;

    /** Bloc commun des pièces (lot 1 / 1b, D4) : numérotation, auteurs, annulation, abandon. */
    private PieceBase piece;

    private TypeEntree type;
    private LocalDate date;

    private String siteDestinationId;
    private String siteDestinationNom;

    private String fournisseur;
    private String referenceCommande;

    @Builder.Default
    private List<LigneBon> lignes = new ArrayList<>();

    @Builder.Default
    private StatutBon statut = StatutBon.BROUILLON;

    private String demandeurId;
    private String demandeurNom;

    /**
     * Auteur du bon, renseigné <b>serveur</b> depuis le JWT à la création et jamais accepté du
     * client. Gouverne les habilitations (modifier / supprimer / soumettre), contrairement au
     * demandeur, choisi dans le formulaire.
     */
    private String creeParId;
    private String creeParEmail;
    private String creeParNom;
    private String validateurId;
    private String validateurNom;

    private String commentaire;
    private String motifRefus;

    @Builder.Default
    private List<EntreeHistorique> historique = new ArrayList<>();

    private long montantTotal;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public StatutPiece statutPiece() {
        return statut == null ? StatutPiece.BROUILLON : statut.statutPiece();
    }

    @Override
    public String siteIdPiece() {
        return siteDestinationId;
    }

    @Override
    public long montantPiece() {
        return montantTotal;
    }

    /** Référence si elle existe (numéro attribué ou référence historique), sinon {@code BROUILLON-…}. */
    @Override
    public String libelleNumero() {
        return reference != null ? reference : Piece.super.libelleNumero();
    }
}
