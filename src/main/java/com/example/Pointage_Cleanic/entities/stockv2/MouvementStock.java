package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.NatureDon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_mouvements")
public class MouvementStock {

    @Id
    private String id;

    private String reference;

    private String produitId;
    private String produitCode;
    private String produitLibelle;
    private UniteStock unite;

    private TypeMouvement type;
    private MotifMouvement motif;
    private double quantite;

    private String siteSourceId;
    private String siteSourceNom;

    private String siteDestinationId;
    private String siteDestinationNom;

    private LocalDate date;

    /** Utilisateur créateur, déduit du JWT (jamais envoyé par le client). */
    private String utilisateur;
    private String commentaire;

    private LocalDateTime createdAt;

    // --- 7.4 Contrôle des mouvements (champs optionnels, rétro-compatibles) ---
    /**
     * "DIRECT" = saisie directe 7.3 ; "BON" = généré par la validation d'un bon 7.4 ;
     * "INVENTAIRE" = ajustement généré par la clôture d'un inventaire 7.3.
     */
    private String origine;
    /** Id du bon source (si origine = BON). */
    private String bonId;
    /** Référence du bon source : BE-/BS-AAAAMMJJ-XXX (si origine = BON). */
    private String bonReference;
    /** Catégorie typée du bon d'entrée à l'origine du mouvement. */
    private TypeEntree categorieEntree;
    /** Catégorie typée du bon de sortie à l'origine du mouvement. */
    private TypeSortie categorieSortie;

    /** Id de l'inventaire dont la clôture a généré l'ajustement (si origine = INVENTAIRE). */
    private String inventaireId;
    /** Référence de l'inventaire source : INV-AAAAMMJJ-XXX (si origine = INVENTAIRE). */
    private String inventaireReference;

    // --- 7.5 : traçabilité analytique (recopiée depuis le bon de sortie) ---
    /** Nature du don, recopiée si le bon source est de type DON (sinon null). */
    private NatureDon natureDon;
    /** Bénéficiaire du don, recopié si le bon source est de type DON (sinon null). */
    private String beneficiaireDon;
    /** Chantier rattaché, recopié si le bon source est de type DISTRIBUTION_CHANTIER (sinon null). */
    private String chantierId;
    private String chantierReference;

    // --- 7.6 : traçabilité financière (champs optionnels, rétro-compatibles) ---
    /**
     * Coût unitaire en FCFA gelé au moment du mouvement. Null pour les mouvements antérieurs à 7.6
     * (les vues financières reconstituent alors le coût courant et marquent la ligne estimée).
     */
    private Long coutUnitaireSnapshot;
    /** Valeur du mouvement en FCFA = quantité × coutUnitaireSnapshot. Null pour les mouvements pré-7.6. */
    private Long valeurMouvement;

    // --- Lot 1 / 1b (D4) : annulation d'une pièce par contre-passement, sans suppression ---
    /** Vrai si ce mouvement a été contre-passé (pièce annulée) : exclu des consommations. */
    private Boolean contrePasse;
    /** Sur un mouvement inverse ({@code origine = "ANNULATION"}) : le mouvement qu'il annule. */
    private String mouvementAnnuleId;
    /**
     * Répartition du débit d'une sortie de bon (site source, puis entrepôt en repli), pour la
     * recréditer à l'identique en cas d'annulation. Nulle sur les mouvements antérieurs à 1b :
     * l'annulation recrédite alors l'entrepôt.
     */
    private Double quantiteSurSite;
    private Double quantiteSurConsolide;
}
