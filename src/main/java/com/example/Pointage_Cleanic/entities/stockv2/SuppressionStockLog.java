package com.example.Pointage_Cleanic.entities.stockv2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Trace d'une suppression définitive (inventaire clôturé, bon déjà engagé) par le super-administrateur.
 * <p>
 * Le document supprimé et ses mouvements disparaissent : sans le détail des lignes contre-passées,
 * l'impact stock de l'opération serait irrécupérable. Collection en <b>écriture seule</b> — aucun
 * endpoint de lecture n'est exposé dans ce lot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_suppressions")
public class SuppressionStockLog {

    /** Nature du document supprimé. */
    public enum TypeDocument {
        INVENTAIRE, BON_ENTREE, BON_SORTIE
    }

    /** Une ligne de stock rétablie par le contre-passement. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LigneContrePassee {
        private String produitId;
        private String produitCode;
        /** Delta appliqué au solde pour annuler l'effet d'origine (signé). */
        private double delta;
        /** Site dont le solde a été corrigé (null = bucket consolidé). */
        private String siteId;
    }

    @Id
    private String id;

    private TypeDocument typeDocument;

    @Indexed
    private String documentId;
    private String reference;
    /** Statut du document juste avant sa suppression (valeur de l'enum, en chaîne). */
    private String statutAvant;

    private String motif;

    /** Auteur déduit du JWT, jamais accepté du client. */
    private String auteurId;
    private String auteurNom;
    private LocalDateTime dateSuppression;

    private int nbMouvementsContrePasses;

    @Builder.Default
    private List<LigneContrePassee> lignes = new ArrayList<>();
}
