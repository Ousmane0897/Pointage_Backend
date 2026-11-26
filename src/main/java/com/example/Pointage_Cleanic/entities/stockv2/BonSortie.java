package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.NatureDon;
import com.example.Pointage_Cleanic.Enum.pieces.StatutPiece;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.entities.pieces.Piece;
import com.example.Pointage_Cleanic.entities.pieces.PieceBase;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
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

/** Bon de sortie (7.4). Génère des mouvements SORTIE depuis le site source à la validation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_bons_sortie")
public class BonSortie implements Piece {

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

    private TypeSortie type;
    private LocalDate date;

    private String siteSourceId;
    private String siteSourceNom;

    private DestinataireBon destinataire;
    private String motif;

    // --- 7.5 : don (requis si type = DON) ---
    private NatureDon natureDon;
    private String beneficiaireDon;

    // --- 7.5 : rattachement chantier (requis si type = DISTRIBUTION_CHANTIER) ---
    private String chantierId;
    private String chantierReference;

    @Builder.Default
    private List<LigneBon> lignes = new ArrayList<>();

    @Builder.Default
    private StatutBon statut = StatutBon.BROUILLON;

    private String demandeurId;
    private String demandeurNom;
    private String validateurId;
    private String validateurNom;

    /**
     * Auteur du bon, renseigné <b>serveur</b> depuis le JWT à la création et jamais accepté du
     * client (absent de {@code BonSortiePayload}). Distinct du {@code demandeurId}, qui est choisi
     * dans le formulaire : c'est l'auteur qui gouverne les habilitations (reprise après refus,
     * édition, suppression d'un brouillon), pas le demandeur.
     */
    private String creeParId;
    private String creeParEmail;
    private String creeParNom;

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
        return siteSourceId;
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
